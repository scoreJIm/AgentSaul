# AgentSaul Deployment

## Architecture

```
Browser ---> Nginx (80/443) ---> Spring Boot (8080) ---> MySQL (3306)
                                    |                  Redis (6379)
                                    +---> DashScope API
```

## Option 1: Plain JAR (simplest)

```bash
cd /opt/agentsaul
mvn clean package -DskipTests
export AI_API_KEY="sk-your-key"
export DB_PASSWORD="your-db-password"
nohup java -jar target/agentsaul-0.0.1-SNAPSHOT.jar > app.log 2>&1 &
curl http://localhost:8080/actuator/health
```

## Option 2: Docker Compose

```yaml
version: '3.8'
services:
  agentsaul:
    build: .
    ports:
      - "8080:8080"
    environment:
      AI_API_KEY: ${AI_API_KEY}
      DB_USERNAME: root
      DB_PASSWORD: ${DB_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
    depends_on:
      - mysql
      - redis

  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
      MYSQL_DATABASE: agent_saul
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

volumes:
  mysql_data:
```

```bash
docker-compose up -d
docker-compose logs -f agentsaul
docker-compose down
```

## Option 3: systemd (production)

```ini
[Unit]
Description=AgentSaul AI Chat Service
After=network.target mysql.service redis.service

[Service]
Type=simple
User=agentsaul
WorkingDirectory=/opt/agentsaul
EnvironmentFile=/opt/agentsaul/.env
ExecStart=/usr/bin/java -jar /opt/agentsaul/agentsaul.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable agentsaul
sudo systemctl start agentsaul
sudo systemctl status agentsaul
```

## Environment Variables

| Variable | Required | Default | Notes |
|----------|----------|---------|-------|
| AI_API_KEY | Yes | - | DashScope API Key |
| AI_BASE_URL | No | dashscope.aliyuncs.com/compatible-mode | LLM endpoint |
| AI_MODEL | No | qwen3-vl-32b-thinking | Model name |
| DB_USERNAME | No | root | MySQL user |
| DB_PASSWORD | Yes | root | MySQL password |
| REDIS_HOST | No | localhost | Redis host |
| REDIS_PORT | No | 6379 | Redis port |
| SERVER_PORT | No | 8080 | Server port |

## Health Check

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP","components":{"db":{"status":"UP"},"redis":{"status":"UP"}}}
```

## Pre-Deploy Checklist

- [ ] MySQL running, database agent_saul reachable
- [ ] Redis running (optional, cache degrades gracefully without it)
- [ ] Java 21+ installed
- [ ] Environment variables set (AI_API_KEY + DB_PASSWORD)
- [ ] Port 8080 open in firewall
- [ ] application-local.yml created from gitignore
