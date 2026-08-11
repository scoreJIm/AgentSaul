# AgentSaul Frontend

The frontend is a standalone static web app served by Spring Boot.

## Structure

| File | Description |
|------|-------------|
| `index.html` | Main chat UI — Better Call Saul themed AI attorney |
| `mcp-demo.html` | MCP (Model Context Protocol) tools demo |
| `rag-demo.html` | RAG (Retrieval-Augmented Generation) demo |

## Development

Edit files directly in `frontend/`. Spring Boot serves them from `file:frontend/` in dev mode.

```bash
# Start backend with frontend
mvn spring-boot:run

# Open in browser
open http://localhost:8080
```

## Build

Maven copies `frontend/` into the JAR at `static/` during `mvn package`.

```xml
<resource>
    <directory>frontend</directory>
    <targetPath>static</targetPath>
</resource>
```
