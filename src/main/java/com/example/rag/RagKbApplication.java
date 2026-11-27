package com.example.rag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 本地 RAG 知识库启动类。
 * 技术栈：SpringBoot Web + LangChain4j + 嵌入式 H2（H2Vector）。
 * 启动前请先在本机安装并启动 Ollama，并拉取对话模型与 Embedding 模型。
 */
@SpringBootApplication
public class RagKbApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagKbApplication.class, args);
    }
}
