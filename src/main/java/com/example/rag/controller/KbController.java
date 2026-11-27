package com.example.rag.controller;

import com.example.rag.common.ApiException;
import com.example.rag.dto.ChatRequest;
import com.example.rag.dto.ChatResponse;
import com.example.rag.dto.UploadResponse;
import com.example.rag.entity.DocumentInfo;
import com.example.rag.service.ChatRateLimiter;
import com.example.rag.service.DocumentService;
import com.example.rag.service.RagService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 知识库与问答接口。
 */
@RestController
@RequestMapping("/api")
public class KbController {

    private final DocumentService documentService;
    private final RagService ragService;
    private final ChatRateLimiter chatRateLimiter;

    public KbController(DocumentService documentService, RagService ragService, ChatRateLimiter chatRateLimiter) {
        this.documentService = documentService;
        this.ragService = ragService;
        this.chatRateLimiter = chatRateLimiter;
    }

    /**
     * 上传 TXT / Markdown 文档，自动解析、分块、向量化入库。
     */
    @PostMapping("/documents/upload")
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        DocumentInfo doc = documentService.upload(file);
        return new UploadResponse(doc.getId(), doc.getFilename(), doc.getChunkCount());
    }

    /**
     * 查看已上传文档列表。
     */
    @GetMapping("/documents")
    public List<DocumentInfo> listDocuments() {
        return documentService.list();
    }

    /**
     * 删除文档，并同步清理其全部向量。
     */
    @DeleteMapping("/documents/{id}")
    public String delete(@PathVariable("id") String id) {
        documentService.delete(id);
        return "删除成功";
    }

    /**
     * 基于知识库问答。
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        if (!chatRateLimiter.tryAcquire(httpRequest.getRemoteAddr())) {
            throw ApiException.tooManyRequests("提问次数已达上限，请一小时后再试");
        }
        return ragService.ask(request.getQuestion());
    }
}
