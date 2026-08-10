# ⚖ AgentSaul — Better Call Your AI Attorney

Spring AI 驱动的智能对话 Agent，Better Call Saul 主题风格。

## 技术栈

| 层次 | 技术 |
|------|------|
| 框架 | Spring Boot 3.4 + Spring AI 1.0 |
| LLM | DashScope（百炼）Qwen3 / OpenAI 兼容 |
| 数据库 | MySQL + MyBatis |
| 缓存 | Redis（Session / 消息缓存） |
| 前端 | 原生 HTML/CSS/JS，SSE 流式对话 |
| 构建 | Maven + Java 21 |

## 快速启动

### 1. 环境要求
- JDK 21+
- MySQL 5.7+ / 8.0
- Redis（可选，默认连 localhost:6379）
- Maven 3.9+

### 2. 配置
编辑 `src/main/resources/application.yml`，填入 API Key 和数据库信息：
```yaml
spring:
  ai:
    openai:
      api-key: sk-your-api-key
      base-url: https://dashscope.aliyuncs.com/compatible-mode
  datasource:
    url: jdbc:mysql://localhost:3306/agent_saul?...
```

### 3. 启动
```bash
mvn spring-boot:run
```
打开浏览器访问 `http://localhost:8080`

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/chat` | 发送消息（SSE 流式返回） |
| GET | `/api/conversations` | 获取会话列表 |
| GET | `/api/conversations/{id}/messages` | 获取历史消息 |
| DELETE | `/api/conversations/{id}` | 删除会话 |

### 请求示例
```json
POST /api/chat
{
  "message": "I need legal advice",
  "conversationId": 1
}
```

## 项目结构
```
src/main/java/com/agentsaul/
├── AgentSaulApplication.java
├── config/
│   ├── AiConfig.java          # ChatClient Bean
│   └── CacheConfig.java       # Redis 缓存配置
├── controller/
│   └── ChatController.java    # REST API
├── entity/
│   ├── Conversation.java      # 会话实体
│   └── Message.java           # 消息实体
├── repository/
│   ├── ConversationMapper.java
│   └── MessageMapper.java
└── service/
    └── ChatService.java       # 核心对话逻辑
```

## 特性
- SSE 流式输出
- 多轮对话记忆（MessageChatMemoryAdvisor）
- 会话管理（新建/切换/删除）
- Redis 缓存
- Better Call Saul 主题 UI
