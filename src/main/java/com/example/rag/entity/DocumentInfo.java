package com.example.rag.entity;

import java.time.LocalDateTime;

/**
 * 知识库文档元数据（存 H2 表 kb_document）。
 * 向量与分块正文存 embedding_chunk 表，删除文档时级联清理。
 */
public class DocumentInfo {

    /** 文档主键（UUID） */
    private String id;

    /** 原始文件名 */
    private String filename;

    /** 切分后的分块数量 */
    private int chunkCount;

    /** 上传时间 */
    private LocalDateTime createdAt;

    public DocumentInfo() {
    }

    public DocumentInfo(String id, String filename, int chunkCount, LocalDateTime createdAt) {
        this.id = id;
        this.filename = filename;
        this.chunkCount = chunkCount;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
