# AgentSaul — Status

> Last updated: 2026-05-10

## Done

### Chat
- POST /api/chat — SSE streaming chat
- System prompt: Saul Goodman persona
- MessageChatMemoryAdvisor for multi-turn memory

### Tool Calls (10 tools)
| Tool | Class | Notes |
|------|-------|-------|
| currentDateTime | UtilityTools | Current time |
| getWeather | UtilityTools | Open-Meteo, free, no API key |
| geoLocation | UtilityTools | ip-api.com, free |
| calculateDeadline | LegalTools | Legal deadline calc, skips weekends |
| estimateSettlement | LegalTools | Personal injury settlement estimate |
| legalInfo | LegalTools | Common legal knowledge lookup |
| translate | TranslateTools | Multi-language translation |
| webSearch | WebTools | Search URL generation |
| calculate | WebTools | Math expression evaluator |
| worldTime | WebTools | Multi-timezone clock |

### Conversation History
- MySQL: conversations + messages (includes tool_name)
- API: GET /api/conversations, GET /api/conversations/{id}/messages
- API: GET /api/conversations/{id}/tools, DELETE /api/conversations/{id}
- Frontend sidebar: history list, switch/delete

### Logging (logback-spring.xml)
- business.log -> com.agentsaul.service + tool
- api.log -> com.agentsaul.controller
- system.log -> config + root + third-party

### Frontend
- Better Call Saul theme
- Sidebar conversation list
- SSE streaming message bubbles
- Tool call indicator

### Infrastructure
- MySQL 5.7 + MyBatis 3.0.4
- Redis configured
- Maven 3.9 + JDK 21

## How to run

```bash
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.10
mvn spring-boot:run
```

Open http://localhost:8080
