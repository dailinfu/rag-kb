package com.example.rag.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 大模型与向量化模型装配。
 * 全部通过本地 Ollama 提供，无需商业 API Key；
 * 后续如需切换为 Claude / 通义千问等，只需替换对应 Bean 的实现。
 */
@Configuration
public class ModelConfig {

    @Value("${rag.ollama.base-url}")
    private String baseUrl;

    @Value("${rag.ollama.chat-model}")
    private String chatModel;

    @Value("${rag.ollama.embedding-model}")
    private String embeddingModel;

    @Value("${rag.ollama.temperature:0.2}")
    private double temperature;

    /**
     * 对话大模型：负责基于检索到的上下文片段生成最终回答。
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(chatModel)
                .temperature(temperature)
                .build();
    }

    /**
     * Embedding 模型：把文本（文档分块 / 用户问题）转为向量，用于相似度检索。
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName(embeddingModel)
                .build();
    }
}
