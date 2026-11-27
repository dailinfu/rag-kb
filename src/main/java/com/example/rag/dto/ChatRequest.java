package com.example.rag.dto;

/**
 * 问答请求体。
 */
public class ChatRequest {

    /** 用户问题（会经过长度限制与提示词注入检测） */
    private String question;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
