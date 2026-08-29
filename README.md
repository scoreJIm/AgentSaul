# AgentSaul

A conversational chat app built on Spring AI, with a light legal-assistant theme.
It demonstrates function calling, SSE streaming, conversation memory, and structured output.

## What it does

You chat, and it remembers the conversation. When it needs real information it
calls a tool instead of guessing — translation, weather (Open-Meteo),
geolocation (ip-api.com), legal references, and basic math. Tool selection is
handled by Spring AI's function calling; the app just registers the tools.

## Tech stack

| Layer | What |
|-------|------|
| Framework | Spring Boot 3.4.4, Spring AI 1.0.0 |
| LLM backend | DashScope `qwen3-vl-32b-thinking`, OpenAI-compatible endpoint |
| Database | PostgreSQL + MyBatis |
| Cache | Redis (session state, message cache) |
| Frontend | Vanilla HTML/CSS/JS, SSE streaming |
| Build | Maven, Java 21 |
| CI/CD | GitHub Actions |
| Container | Docker multi-stage |

## Quick start

```bash
git clone git@github.com:scoreJIm/AgentSaul.git
cd AgentSaul
```

Set your DashScope API key and point the app at PostgreSQL. Environment
variables (with defaults) are:

- `AI_API_KEY` — DashScope key (required)
- `AI_MODEL` — defaults to `qwen3-vl-32b-thinking`
- `SPRING_DATASOURCE_URL` — defaults to `jdbc:postgresql://localhost:5432/agent_saul`
- `DB_USERNAME` / `DB_PASSWORD` — default `postgres` / `postgres`
- `REDIS_HOST` / `REDIS_PORT`

Then:

```bash
mvn spring-boot:run
```

Open `http://localhost:8080`. App tables auto-initialize from `schema.sql`.

Or with Docker Compose (starts PostgreSQL + Redis + the app):

```bash
AI_API_KEY=your-key docker compose up --build
```

Or a standalone image:

```bash
docker build -t agentsaul .
docker run -p 8080:8080 -e AI_API_KEY=your-key agentsaul
```

## API

| Method | Path | What |
|--------|------|------|
| POST | `/api/chat` | Send message, SSE stream back |
| POST | `/api/analysis/legal` | Analyze a legal query into a structured case analysis (structured output) |
| GET | `/api/conversations` | List conversations |
| GET | `/api/conversations/{id}/messages` | Message history |
| GET | `/api/conversations/{id}/tools` | Tool call history |
| DELETE | `/api/conversations/{id}` | Delete conversation |

## Architecture notes

- **Tool dispatch (`@Tool`)**: Spring AI function calling — the model chooses
  the tool; the app just registers it. `IntentParser` is a lightweight regex
  classifier used for language detection and logging, not for routing.
- **Explicit `ToolCallback`**: `ToolCallbackConfig` wraps a plain `Function` into
  a `FunctionToolCallback` bean and wires it via `defaultToolCallbacks(...)` —
  the underlying mechanism `@Tool` builds on.
- **Structured output**: `LegalAnalysisService` asks the model for JSON and
  `ChatClient.call().entity(CaseAnalysis.class)` maps it straight into a Java
  record — no hand-parsed JSON.
- **Session memory**: `PostgresChatMemory` (last 20 messages per conversation
  via MyBatis), with an in-memory `MessageWindowChatMemory` fallback. Different
  tabs = different sessions.
- **Streaming + persistence**: SSE chunks hit the client immediately. On stream
  completion, the full response is persisted to PostgreSQL.
- **Prompts as data**: the system prompt lives in Markdown files under
  `src/main/resources/prompts/`, editable without touching Java.

## Project layout

```
src/main/java/com/agentsaul/
├── AgentSaulApplication.java
├── config/                     # AiConfig, ToolCallbackConfig, SecurityConfig, ResilienceConfig, ...
├── controller/                 # Chat, LegalAnalysis, McpDemo, Auth, Admin
├── entity/                     # Conversation, Message
├── exception/                  # GlobalExceptionHandler, RateLimitExceededException
├── mcp/                        # McpTools, McpClientConfig (demo)
├── memory/                     # PostgresChatMemory (last 20 messages)
├── repository/                 # MyBatis mappers (ConversationMapper, MessageMapper)
├── security/                   # JwtAuthenticationFilter, JwtTokenProvider
├── service/
│   ├── ChatService.java        # Chat logic, streaming, memory, tool wiring
│   ├── LegalAnalysisService.java # Structured output (LLM JSON → record)
│   ├── IntentParser.java       # Language detection + intent logging
│   └── SessionManager.java     # Redis-backed session state
└── tool/
    ├── LegalTools.java         # Legal deadline, settlement, reference
    ├── TranslateTools.java     # LLM-backed translation
    ├── UtilityTools.java       # Weather (Open-Meteo), geolocation (ip-api.com), time
    └── WebTools.java           # Calculator, world clock
```

## Status

A working prototype that demonstrates Spring AI: function calling (`@Tool` +
`ToolCallback`), structured output, conversation memory, and SSE streaming.

Deeper detail: [ARCHITECTURE.md](ARCHITECTURE.md).
