package com.example.rag.dto;

import java.util.List;

/**
 * 问答响应体：包含大模型回答与命中的知识库片段（便于溯源）。
 */
public class ChatResponse {

    /** 大模型生成的回答 */
    private String answer;

    /** 召回的参考片段（来源溯源） */
    private List<Source> sources;

    public ChatResponse(String answer, List<Source> sources) {
        this.answer = answer;
        this.sources = sources;
    }

    public String getAnswer() {
        return answer;
    }

    public List<Source> getSources() {
        return sources;
    }

    /**
     * 单个参考片段。
     */
    public static class Source {
        /** 来源文档名 */
        private String filename;
        /** 片段正文 */
        private String content;
        /** 余弦相似度分数（0~1） */
        private double score;

        public Source(String filename, String content, double score) {
            this.filename = filename;
            this.content = content;
            this.score = score;
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
