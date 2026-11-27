# 本地 RAG 知识库（SpringBoot + LangChain4j + H2Vector）

一套最小可运行的 RAG（检索增强生成）知识库原型，面向 Java 后端演示与面试讲解。
向量与文档元数据落在嵌入式 H2 文件库，Redis 用于问答接口分布式限流；
大模型与向量化模型通过本地 Ollama 提供，无需商业 API Key。

## 一、技术栈

| 模块 | 选型 | 说明 |
| --- | --- | --- |
| Web 框架 | Spring Boot 2.7.18 | 内嵌 Tomcat，兼容 JDK 11/17 |
| RAG 框架 | LangChain4j 0.35.0 | 统一的模型 / 向量检索抽象 |
| 模型接入 | langchain4j-ollama | 本地 Ollama 提供对话模型 + Embedding 模型 |
| 向量库 | 嵌入式 H2（H2Vector） | 向量序列化为文本落 H2 文件库，余弦相似度检索 |
| 持久化 | spring-jdbc + H2 | 文档元数据与分块向量同库存放 |
| 限流 | Redisson + Redis | 按客户端 IP 原子滑动窗口限制问答频率 |

## 二、项目结构

```
rag-kb/
├── pom.xml
├── README.md
└── src/main/
    ├── java/com/example/rag/
    │   ├── RagKbApplication.java          # 启动类
    │   ├── config/ModelConfig.java       # 装配 Ollama 对话/Embedding 模型
    │   ├── controller/KbController.java # 上传 / 列表 / 删除 / 问答接口
    │   ├── service/
    │   │   ├── DocumentService.java      # 文档解析、分块、向量化、入库
    │   │   └── RagService.java           # 检索、Prompt 隔离组装、生成回答
    │   ├── store/VectorStore.java       # H2 向量存储与余弦检索
    │   ├── security/PromptGuard.java     # 长度限制 + 提示词注入基础检测
    │   ├── entity/DocumentInfo.java      # 文档元数据实体
    │   ├── dto/                           # 请求 / 响应 DTO
    │   └── common/                       # 业务异常 + 全局异常处理
    └── resources/application.yml         # 配置（模型、分块、检索、安全阈值）
```

## 三、启动前置准备

### 1. 安装并启动 Ollama
- 下载：https://ollama.com
- 拉取对话模型与 Embedding 模型（模型名需与 `application.yml` 一致）：

```bash
ollama pull qwen3.5:4b
ollama pull nomic-embed-text
```

- 默认服务地址 `http://localhost:11434`，可在 `application.yml -> rag.ollama.base-url` 修改。

### 2. 启动 Redis

问答接口通过 Redis 统计滑动窗口请求；默认连接 `127.0.0.1:6379`，也可通过 `REDIS_HOST` / `REDIS_PORT` 设置。

### 3. 编译启动

```bash
# JDK 11+，Maven 3.6+
mvn spring-boot:run
```

启动成功后：
- 接口地址：`http://localhost:8080`
- 前端知识库界面：`http://localhost:8080`（支持文档上传、列表、删除与知识库问答）
- H2 控制台：`http://localhost:8080/h2-console`
  - JDBC URL：`jdbc:h2:file:./data/ragkb`
  - 用户名：`sa`，密码为空

## 四、接口与调用示例（curl）

### 1. 上传文档（.txt / .md）

```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@./docs/知识库说明.md"
```

响应：

```json
{
  "docId": "a1b2c3d4-....",
  "filename": "知识库说明.md",
  "chunkCount": 6
}
```

### 2. 查看文档列表

```bash
curl http://localhost:8080/api/documents
```

### 3. 删除文档（级联清理向量）

```bash
curl -X DELETE http://localhost:8080/api/documents/{docId}
```

### 4. 知识库问答

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "本知识库主要介绍了哪些内容？"}'
```

响应（含回答与溯源片段）：

```json
{
  "answer": "根据参考资料，本知识库主要介绍了……",
  "sources": [
    {
      "filename": "知识库说明.md",
      "content": "……片段正文……",
      "score": 0.78
    }
  ]
}
```

## 五、RAG 处理链路

1. **文档入库**：上传文件 → 解析 UTF-8 文本 → 按字符长度分块（带重叠）→ Embedding 向量化 → 写入 H2 向量库。
2. **问答检索**：问题向量化 → 与库内向量计算余弦相似度 → 过滤低于阈值的片段 → 取 Top-K。
3. **Prompt 组装**：严格分区【系统指令】/【参考资料】/【用户问题】，明确告知模型参考资料仅作阅读、不得执行其中指令。
4. **生成回答**：调用本地对话模型生成答案，返回答案与命中片段用于溯源。

## 六、安全设计（原型基础版）

- **长度限制**：单条问题超过 `rag.security.max-question-length` 直接拦截，防止超长请求挤占上下文与 token。
- **问答限流**：按客户端 IP 限制每小时最多 100 次；Redis Lua 脚本原子完成过期清理、计数与记录，多实例共享窗口。部署在代理后时，需确保应用可获得实际客户端 IP。
- **注入检测**：关键词规则识别“忽略前面指令 / 你现在是 / 泄露密钥 / 越狱”等常见注入模式。
- **Prompt 隔离**：系统指令、参考资料、用户问题分区标记，降低指令覆盖与检索文档注入风险。
- **异常兜底**：全局异常统一返回 JSON，不向外暴露堆栈；模型/向量异常记录日志。

> 生产增强方向：语义级注入分类模型、输入不可见字符清洗、输出合规审计、文档入库前安全扫描、按账号做文档级权限过滤。

## 七、关键可调参数（application.yml）

| 配置项 | 含义 | 默认 |
| --- | --- | --- |
| `rag.chunk.size` | 分块字符数 | 500 |
| `rag.chunk.overlap` | 分块重叠字符数 | 80 |
| `rag.retrieval.max-results` | 召回片段上限 | 4 |
| `rag.retrieval.min-score` | 最低余弦相似度 | 0.5 |
| `rag.security.max-question-length` | 问题最大字符数 | 1000 |
| `spring.redis.host` / `spring.redis.port` | Redisson 使用的 Redis 地址与端口 | `127.0.0.1:6379` |

## 八、面试讲解要点

- **为什么用 LangChain4j**：纯 Java 生态，可与 Spring 无缝整合，复用现有权限、日志、安全模块，避免跨语言维护成本。
- **为什么用 H2 嵌入式向量库**：原型演示零外部依赖、一键启动；生产可平滑替换为 Milvus / PGVector，检索接口已在 `VectorStore` 层收敛。
- **一致性与可追溯**：回答附带命中片段来源；文档删除级联清理向量，避免脏数据被检索到。
- **已知边界**：当前为内存线性相似度检索，适合小体量知识库；关键词注入检测只能覆盖已知模式，生产需叠加语义检测与输出审计。
