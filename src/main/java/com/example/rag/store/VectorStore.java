package com.example.rag.store;

import com.example.rag.entity.DocumentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 基于嵌入式 H2 的向量存储（H2Vector）。
 * 原型规模下向量随文档一起落 H2 文件库，检索时加载内存计算余弦相似度；
 * 无需 Milvus/Redis 等外部中间件，重启数据不丢失。
 */
@Repository
public class VectorStore {

    private static final Logger log = LoggerFactory.getLogger(VectorStore.class);

    private final JdbcTemplate jdbcTemplate;

    public VectorStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initTables();
    }

    /** 启动时建表（IF NOT EXISTS，重复执行安全） */
    private void initTables() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS kb_document (" +
                        "id VARCHAR(64) PRIMARY KEY, " +
                        "filename VARCHAR(255), " +
                        "chunk_count INT, " +
                        "created_at TIMESTAMP" +
                        ")");
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS embedding_chunk (" +
                        "id VARCHAR(64) PRIMARY KEY, " +
                        "doc_id VARCHAR(64), " +
                        "content CLOB, " +
                        "vector TEXT" +
                        ")");
        log.info("H2 向量表初始化完成");
    }

    /**
     * 保存文档及其全部分块向量。
     *
     * @param docId       文档 ID
     * @param filename    文件名
     * @param contents    分块正文列表
     * @param embeddings  与正文一一对应的向量
     */
    public void saveDocument(String docId, String filename, List<String> contents, List<float[]> embeddings) {
        jdbcTemplate.update(
                "INSERT INTO kb_document(id, filename, chunk_count, created_at) VALUES (?,?,?,?)",
                docId, filename, contents.size(), LocalDateTime.now());

        for (int i = 0; i < contents.size(); i++) {
            String chunkId = UUID.randomUUID().toString();
            jdbcTemplate.update(
                    "INSERT INTO embedding_chunk(id, doc_id, content, vector) VALUES (?,?,?,?)",
                    chunkId, docId, contents.get(i), serializeVector(embeddings.get(i)));
        }
        log.info("文档 [{}] 入库完成，共 {} 个分块", filename, contents.size());
    }

    /**
     * 向量检索：返回相似度高于阈值的 Top-K 片段。
     */
    public List<ChunkResult> search(float[] queryVector, int topK, double minScore) {
        List<ChunkRow> rows = jdbcTemplate.query(
                "SELECT c.id, c.doc_id, d.filename, c.content, c.vector " +
                        "FROM embedding_chunk c LEFT JOIN kb_document d ON c.doc_id = d.id",
                new ChunkRowMapper());

        List<ChunkResult> results = new ArrayList<>();
        for (ChunkRow row : rows) {
            float[] vector = deserializeVector(row.vector);
            double score = cosineSimilarity(queryVector, vector);
            if (score >= minScore) {
                results.add(new ChunkResult(row.docId, row.filename, row.content, score));
            }
        }
        // 按相似度降序，取前 topK
        results.sort(Comparator.comparingDouble(ChunkResult::getScore).reversed());
        if (results.size() > topK) {
            results = results.subList(0, topK);
        }
        return results;
    }

    /**
     * 列出全部已上传文档（按上传时间倒序）。
     */
    public List<DocumentInfo> listDocuments() {
        return jdbcTemplate.query(
                "SELECT id, filename, chunk_count, created_at FROM kb_document ORDER BY created_at DESC",
                (rs, rowNum) -> new DocumentInfo(
                        rs.getString("id"),
                        rs.getString("filename"),
                        rs.getInt("chunk_count"),
                        rs.getTimestamp("created_at").toLocalDateTime()));
    }

    /**
     * 删除文档，并级联清理其全部向量分块。
     *
     * @return 删除的文档数量（0 表示不存在）
     */
    public int deleteDocument(String docId) {
        jdbcTemplate.update("DELETE FROM embedding_chunk WHERE doc_id = ?", docId);
        return jdbcTemplate.update("DELETE FROM kb_document WHERE id = ?", docId);
    }

    // ---------------- 向量序列化与相似度计算 ----------------

    private String serializeVector(float[] vector) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    private float[] deserializeVector(String text) {
        if (text == null || text.isEmpty()) {
            return new float[0];
        }
        String[] parts = text.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Float.parseFloat(parts[i].trim());
        }
        return vector;
    }

    /** 余弦相似度：范围 [-1,1]，方向越接近越接近 1 */
    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length || a.length == 0) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ---------------- 内部数据结构 ----------------

    /** 查询行映射的中间结构 */
    private static class ChunkRow {
        String id;
        String docId;
        String filename;
        String content;
        String vector;
    }

    private static class ChunkRowMapper implements RowMapper<ChunkRow> {
        @Override
        public ChunkRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            ChunkRow row = new ChunkRow();
            row.id = rs.getString("id");
            row.docId = rs.getString("doc_id");
            row.filename = rs.getString("filename");
            row.content = rs.getString("content");
            row.vector = rs.getString("vector");
            return row;
        }
    }

    /** 对外返回的命中片段 */
    public static class ChunkResult {
        private final String docId;
        private final String filename;
        private final String content;
        private final double score;

        public ChunkResult(String docId, String filename, String content, double score) {
            this.docId = docId;
            this.filename = filename;
            this.content = content;
            this.score = score;
        }

        public String getDocId() {
            return docId;
        }

        public String getFilename() {
            return filename;
        }

        public String getContent() {
            return content;
        }

        public double getScore() {
            return score;
        }
    }
}
