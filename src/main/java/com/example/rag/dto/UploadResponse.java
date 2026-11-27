package com.example.rag.dto;

/**
 * 文档上传成功响应体。
 */
public class UploadResponse {

    /** 文档 ID */
    private String docId;

    /** 文件名 */
    private String filename;

    /** 分块数量 */
    private int chunkCount;

    public UploadResponse(String docId, String filename, int chunkCount) {
        this.docId = docId;
        this.filename = filename;
        this.chunkCount = chunkCount;
    }

    public String getDocId() {
        return docId;
    }

    public String getFilename() {
        return filename;
    }

    public int getChunkCount() {
        return chunkCount;
    }
}
