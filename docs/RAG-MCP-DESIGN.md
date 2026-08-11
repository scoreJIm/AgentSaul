# RAG + MCP Demo — 设计与 Prompt Engineering 分析

## 概述

本文档记录了在 AgentSaul 项目中添加的 RAG（Retrieval-Augmented Generation）和 MCP（Model Context Protocol）两个 Demo 的设计思路、架构决策和 Prompt Engineering 要点。

## 一、RAG Demo

### 1.1 架构设计

```
用户问题 → EmbeddingModel(DashScope API) → 向量化
                                               ↓
                                         SimpleVectorStore
                                         (内存级向量搜索)
                                               ↓
                                          top-K 文档块
                                               ↓
                          ┌────────────────────┼────────────────────┐
                          ↓                    ↓                    ↓
                     [检索到的Chunks]    [完整Augmented Prompt]   [LLM 流式回答]
                     前端展示            前端展示               前端实时展示
```

### 1.2 为什么选择 SimpleVectorStore

| 方案 | 优点 | 缺点 |
|------|------|------|
| **SimpleVectorStore (选用)** | 内存级，零配置，无需外部服务 | 重启丢失，不适合生产 |
| PgVector | 持久化，可生产使用 | 需要 PostgreSQL+pgvector 扩展 |
| Redis Vector | 复用现有 Redis | 需要 Redis Stack |
| ChromaDB/Qdrant | 专业向量数据库 | 需要额外部署 |

选择 SimpleVectorStore 因为它最适合 Demo 场景：启动即用，无需任何外部服务，重启后自动重新索引。

### 1.3 分块策略设计（Prompt Engineering 核心）

分块策略直接影响 RAG 的检索质量和最终 LLM 回答的准确性。本 Demo 实现了三种策略对比：

#### 策略 1: Token 窗口切分（TokenTextSplitter）
```java
new TokenTextSplitter(
    400,   // chunkSize: 约200个中文字
    80,    // minChunkSizeChars
    50,    // minChunkLengthToEmbed: 忽略过短的片段
    50,    // maxNumChunks
    true   // keepSeparator: 保留标题等分隔符
)
```

**设计思路：**
- 400 tokens ≈ 200 中文字，这是一个经过验证的"甜点"值
  - 太小（<100字）：语义不完整，检索碎片化
  - 太大（>500字）：噪音多，检索精度下降
- 50 token overlap 确保跨 chunk 边界的上下文不丢失
- `keepSeparator=true` 保留 Markdown 标题，让 LLM 知道信息层级

#### 策略 2: 句子边界切分（Sentence-based）
```
按 。！？；\n 切分 → 拼接句子直到 ~300 字 → 生成 chunk
```

**设计思路：**
- 以自然句子为边界，语义完整性最好
- 自适应长度：短句拼接，长句独立
- 适合中文法律文本（句号是自然语义单元）
- 缺点是 chunk 大小不均匀

#### 策略 3: 段落边界切分（Paragraph-based）
```
按 \n\n 切分 → 每个段落一个 chunk → 超长段落再按句子细分
```

**设计思路：**
- 尊重文档作者的结构意图
- 每个法律条款通常是独立段落，天然适合检索
- 对超长段落（>500字）做二次切分，避免单个 chunk 过大

#### 策略对比

| 维度 | Token 窗口 | 句子边界 | 段落边界 |
|------|-----------|---------|---------|
| 语义完整性 | ★★★ | ★★★★★ | ★★★★ |
| 大小均匀性 | ★★★★★ | ★★★ | ★★ |
| 法律文本适配 | ★★★ | ★★★★ | ★★★★★ |
| 检索精度 | ★★★★ | ★★★★ | ★★★ |

### 1.4 Prompt 模板设计（核心 Prompt Engineering）

```
你是一个法律知识助手。请严格根据以下【检索到的法律知识】来回答用户问题。

## 重要规则
- 仅使用下面提供的法律知识来回答，不要使用你自身的知识
- 如果检索到的知识不足以回答，请直接说"根据现有法律知识库，我无法确切回答这个问题"
- 回答时引用知识来源（如"根据合同法..."、"根据刑法..."等）
- 用中文回答，保持专业但易懂
- 适当引用原文条款

## 检索到的法律知识
{context}

## 用户问题
{question}
```

**设计要点分析：**

1. **角色定义**：「法律知识助手」— 明确但不浮夸的角色
2. **核心约束**：「仅使用下面提供的法律知识」— 这是 RAG 的关键：强制 LLM 基于检索结果而非训练记忆回答
3. **边界情况处理**：「如果检索到的知识不足以回答」— 防止 LLM 在没有相关上下文时编造答案（幻觉）
4. **引用要求**：「引用知识来源」— 鼓励 LLM 明确标注信息来源，增强可信度
5. **模板结构**：System 层（规则）+ Context 层（检索结果）+ Query 层（用户问题）三层分离

### 1.5 检索到回答的完整链路

```
1. 用户: "试用期最长多久？"
2. Embedding: [0.023, -0.451, ...]  (1536维向量)
3. 向量搜索: cosine_similarity → Top-3 chunks
   3.1 "劳动合同期限三个月以上不满一年的，试用期不得超过一个月..."
      (来自 labor-law.md, similarity=0.87)
   3.2 "劳动合同分为固定期限合同、无固定期限合同..."
      (来自 labor-law.md, similarity=0.72)
   3.3 "已满十四周岁不满十六周岁的人，犯故意杀人..."
      (来自 criminal-law.md, similarity=0.51)
4. Prompt 拼接: 系统提示 + chunk1~3 + 用户问题
5. LLM 生成: "根据劳动合同法第19条，试用期最长不得超过六个月..."
6. 前端展示: 检索块 + 完整 Prompt + 流式回答
```

### 1.6 SSE 事件流设计

RAG 的 SSE 响应使用 `event:` 类型区分不同阶段的数据：

```
event: chunks     → 检索到的文档块 (JSON)
event: prompt     → 拼接后的完整 Prompt (文本)
event: answer     → LLM 回答开始信号
data: ...         → LLM token 流
event: done       → 完成信号
```

这样前端可以在同一个 SSE 流中解析出不同阶段的数据，分别渲染到对应面板。

---

## 二、MCP Demo

### 2.1 架构设计

```
┌─────────────────────────────────────────────────┐
│                  AgentSaul App                    │
│                                                   │
│  ┌──────────────┐         ┌──────────────────┐   │
│  │ MCP Client   │──SSE──→│  MCP Server       │   │
│  │ (ChatClient  │←JSON─RPC│  (WebMVC)         │   │
│  │  + MCP tools)│         │                   │   │
│  └──────────────┘         │  ┌─────────────┐  │   │
│                           │  │McpTools      │  │   │
│  ┌──────────────┐         │  │- statute     │  │   │
│  │ Direct Tools │         │  │- time        │  │   │
│  │ (现有 @Tool) │         │  │- case        │  │   │
│  └──────────────┘         │  └─────────────┘  │   │
│                           └──────────────────┘   │
└─────────────────────────────────────────────────┘
```

### 2.2 核心概念

**MCP (Model Context Protocol)** 是 Anthropic 提出的开放协议，定义 AI 应用如何与外部工具和数据源通信。

核心流程：
1. **Tool Discovery**: Client 连接 Server，自动发现可用工具列表
2. **Tool Invocation**: LLM 决策调用工具 → Client 通过 MCP 发送 `tools/call` → Server 执行 → 返回结果
3. **标准化 Schema**: 每个工具的输入参数都有 JSON Schema 定义

### 2.3 MCP vs 直接 @Tool

| 维度 | 直接 @Tool | MCP @Tool |
|------|-----------|-----------|
| 耦合度 | 工具代码与 App 同进程 | 工具可在不同进程/语言/服务器 |
| 发现机制 | 编译时注入 | 运行时动态发现 |
| 协议 | Spring AI 内部调用 | JSON-RPC 2.0 标准协议 |
| 适用场景 | 单体应用内的简单工具 | 微服务/多语言/第三方工具集成 |
| 网络开销 | 无 | SSE/HTTP 通信开销 |

### 2.4 MCP 工具设计

Demo 暴露三个 MCP 工具：

1. **legalStatuteLookup** — 法律条文查询
   - 输入：法律主题关键词
   - 返回：相关法律条文摘要
   
2. **mcpServerTime** — 服务器时间
   - 无输入
   - 返回：当前时间字符串

3. **mcpCaseAnalyzer** — 案件分析
   - 输入：案件类型 + 案情描述
   - 返回：结构化分析报告

### 2.5 关于 MCP 依赖的说明

Spring AI 1.0.0 GA 版本中 MCP 支持可能不完整。`spring-ai-starter-mcp-server-webmvc` 和 `spring-ai-starter-mcp-client` 已被添加到 pom.xml。

**如果 MCP 依赖不可用：**
- `McpClientConfig` 通过 `@ConditionalOnProperty` 提供了降级方案
- MCP Demo 页面仍然可用，ChatClient 会使用直接工具
- 可以通过调整 `spring.ai.mcp.client.enabled` 和依赖版本控制行为

---

## 三、文件清单

### 新增文件

| 文件 | 用途 |
|------|------|
| `src/main/resources/rag-docs/contract-law.md` | 合同法知识文档 |
| `src/main/resources/rag-docs/criminal-law.md` | 刑法知识文档 |
| `src/main/resources/rag-docs/civil-procedure.md` | 民事诉讼法知识文档 |
| `src/main/resources/rag-docs/marriage-law.md` | 婚姻法知识文档 |
| `src/main/resources/rag-docs/labor-law.md` | 劳动法知识文档 |
| `src/main/java/com/agentsaul/rag/RagConfig.java` | EmbeddingModel + SimpleVectorStore 配置 |
| `src/main/java/com/agentsaul/rag/ChunkingStrategies.java` | 三种分块策略实现 |
| `src/main/java/com/agentsaul/rag/RagDocumentLoader.java` | 文档加载/分块/索引 |
| `src/main/java/com/agentsaul/rag/RagService.java` | 检索 + Prompt 构建 + LLM 调用 |
| `src/main/java/com/agentsaul/controller/RagController.java` | RAG REST API |
| `src/main/java/com/agentsaul/mcp/McpTools.java` | MCP 工具定义 |
| `src/main/java/com/agentsaul/mcp/McpClientConfig.java` | MCP Client 配置（含降级） |
| `src/main/java/com/agentsaul/controller/McpDemoController.java` | MCP Demo REST API |
| `src/main/resources/static/rag-demo.html` | RAG 前端页面 |
| `src/main/resources/static/mcp-demo.html` | MCP 前端页面 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `pom.xml` | 添加 spring-ai-starter-vector-store-simple, spring-ai-starter-mcp-server-webmvc, spring-ai-starter-mcp-client |
| `src/main/resources/application.yml` | 添加 spring.ai.mcp 配置段 |

---

## 四、运行与验证

### 启动

```bash
mvn spring-boot:run
```

### 验证清单

1. **RAG 索引状态**: `GET http://localhost:8080/api/rag/stats`
   - 检查 documents=5, strategies 各有 chunks
   - 检查 indexed=true（如果 embedding API 可用）

2. **分块预览**: `GET http://localhost:8080/api/rag/chunks?strategy=token`
   - 验证三种策略产生不同数量和结构的 chunks

3. **RAG 问答页面**: 打开 `http://localhost:8080/rag-demo.html`
   - 输入问题如"试用期最长多久"
   - 验证三面板展示：检索块 → Prompt → 流式回答

4. **MCP 工具列表**: `GET http://localhost:8080/api/mcp/tools`

5. **MCP 对话页面**: 打开 `http://localhost:8080/mcp-demo.html`

### 如果 Embedding API 不可用

DashScope 的兼容模式（`/compatible-mode`）可能不支持 `/v1/embeddings` 端点。如果索引失败：
1. 在日志中查看 `RAG indexing failed` 错误
2. 分块功能仍然可用（通过 `/api/rag/chunks` 查看）
3. 可以在 `RagConfig.java` 中切换到其他 embedding 源

---

## 五、Prompt Engineering 总结

### RAG 的核心 Prompt Engineering 要素

1. **Chunk 设计** > 模型选择：好的分块策略比换更大模型更有效
2. **Prompt 模板** 是 RAG 的灵魂：必须明确约束 LLM 只使用检索到的上下文
3. **边界处理** 不能省略："不知道"比"编造"好
4. **引用要求** 提升可信度：让 LLM 标注信息来源
5. **Top-K 调优**：太少（1-2）可能遗漏关键信息，太多（5+）可能引入噪音

### MCP 的协议设计价值

1. **标准化工具接口** — 让工具可被任何 MCP Client 发现和调用
2. **解耦工具提供者和消费者** — 工具可以用不同语言实现、部署在不同服务器
3. **JSON Schema 定义** — LLM 通过标准的参数 Schema 理解如何调用工具
