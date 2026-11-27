package com.example.rag.service;

import com.example.rag.common.ApiException;
import com.example.rag.entity.DocumentInfo;
import com.example.rag.store.VectorStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 知识库文档服务：负责文档解析、分块、向量化、入库、列表与删除。
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;

    @Value("${rag.chunk.size:500}")
    private int chunkSize;

    @Value("${rag.chunk.overlap:80}")
    private int chunkOverlap;

    public DocumentService(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 上传并入库一份 TXT / Markdown 文档。
     *
     * @param file 上传的文件
     * @return 文档元信息
     */
    public DocumentInfo upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("上传文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !(filename.endsWith(".txt") || filename.endsWith(".md"))) {
            throw ApiException.badRequest("仅支持 .txt 或 .md 文档");
        }

        // 1. 解析纯文本（UTF-8）
        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("文档解析失败：{}", filename, e);
            throw ApiException.badRequest("文档解析失败，请确认文件编码为 UTF-8");
        }
        if (text.trim().isEmpty()) {
            throw ApiException.badRequest("文档内容为空");
        }

        // 2. 文本分块（带重叠）
        List<String> chunks = splitBySize(text, chunkSize, chunkOverlap);
        if (chunks.isEmpty()) {
            throw ApiException.badRequest("文档内容为空");
        }

        // 3. 逐块向量化
        List<float[]> embeddings = new ArrayList<>();
        for (String chunk : chunks) {
            Response<Embedding> response = embeddingModel.embed(chunk);
            embeddings.add(response.content().vector());
        }

        // 4. 写入 H2 向量库
        String docId = UUID.randomUUID().toString();
        vectorStore.saveDocument(docId, filename, chunks, embeddings);
        return new DocumentInfo(docId, filename, chunks.size(), java.time.LocalDateTime.now());
    }

    /**
     * 文档列表。
     */
    public List<DocumentInfo> list() {
        return vectorStore.listDocuments();
    }

    /**
     * 删除文档及其全部向量。
     */
    public void delete(String docId) {
        int rows = vectorStore.deleteDocument(docId);
        if (rows == 0) {
            throw ApiException.notFound("文档不存在或已被删除");
        }
    }

    /**
     * 按字符长度切分文本，相邻分块保留 overlap 个字符重叠，缓解上下文割裂。
     */
    private List<String> splitBySize(String text, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        int len = text.length();
        int start = 0;
        while (start < len) {
            int end = Math.min(start + size, len);
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end == len) {
                break;
            }
            // 下一块回退 overlap 个字符，保证重叠且位置递增
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }
}
