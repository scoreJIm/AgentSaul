# AgentSaul — Your AI Attorney

A conversational AI agent built on Spring AI. "Better Call Saul" themed because
naming things is hard and the show is great.

## What it does

You chat. It remembers. When it needs real information, it fires off tool calls
instead of hallucinating. Web search, translation, legal references, weather,
math — it reaches for the right tool rather than guessing.

The point isn't the legal theme. It's the architecture underneath: intent
parsing, tool dispatch, SSE streaming, and session-scoped memory that doesn't
bleed across users.

## Tech stack

| Layer | What |
|-------|------|
| Framework | Spring Boot 3.4, Spring AI 1.0 |
| LLM backend | DashScope Qwen3, OpenAI-compatible endpoint |
| Database | MySQL + MyBatis |
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

Edit `src/main/resources/application.yml` — point it at your MySQL and
DashScope API key. Then:

```bash
mvn spring-boot:run
```

Open `http://localhost:8080`. Schema auto-initializes.

Or with Docker:

```bash
docker build -t agentsaul .
docker run -p 8080:8080 -e AI_API_KEY=your-key agentsaul
```

## API

| Method | Path | What |
|--------|------|------|
| POST | `/api/chat` | Send message, SSE stream back |
| GET | `/api/conversations` | List conversations |
| GET | `/api/conversations/{id}/messages` | Message history |
| GET | `/api/conversations/{id}/tools` | Tool call history |
| DELETE | `/api/conversations/{id}` | Delete conversation |

## Architecture notes

- **Tool dispatch**: `IntentParser` does a first-pass classification, then
  Spring AI handles tool selection. Not hardcoded if-else.
- **Session memory**: `MessageWindowChatMemory` keyed by session ID, 20
  messages per window. Different tabs = different sessions.
- **Streaming + persistence**: SSE chunks hit the client immediately. On
  stream completion, the full response lands in MySQL and Redis.
- **Prompts as data**: System prompt lives in Markdown files under
  `resources/prompts/`. Tweak without touching Java.

## Project layout

```
src/main/java/com/agentsaul/
├── AgentSaulApplication.java
├── config/
│   ├── AiConfig.java            # ChatClient bean
│   └── CacheConfig.java         # Redis setup
├── controller/
│   └── ChatController.java      # REST endpoints
├── entity/
│   ├── Conversation.java
│   └── Message.java
├── repository/
│   ├── ConversationMapper.java
│   └── MessageMapper.java
├── service/
│   ├── ChatService.java         # Chat logic, streaming, memory
│   └── IntentParser.java        # Intent + language detection
└── tool/
    ├── LegalTools.java          # Legal deadline, settlement, reference
    ├── TranslateTools.java      # Multi-language translation
    ├── UtilityTools.java        # Weather, geolocation, time
    └── WebTools.java            # Search, calculator, world clock
```

## Status

Working prototype. I experiment with agent patterns here — tool composition,
memory strategies, intent routing. Architecture is real, rough edges are mine.
