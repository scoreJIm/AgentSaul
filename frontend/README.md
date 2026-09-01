# AgentSaul frontend

The frontend is a standalone tool-execution workspace served by Spring Boot.
It visualizes conversations, streaming responses, and the tools selected during
each run. Public visitors receive a short-lived USER demo token; administrator
credentials are never embedded in the browser.

`index.html` contains the dependency-free interface. Maven copies `frontend/`
into the application JAR under `static/` during packaging.

```bash
mvn spring-boot:run
```

Open `http://localhost:8080`.
