# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
mvn clean package

# Run locally
mvn spring-boot:run

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Build Docker image
docker build -t plantogether-chat-service .

# Run with Docker
docker run -p 8086:8081 \
  -e DB_USER=postgres -e DB_PASSWORD=postgres \
  -e REDIS_HOST=host.docker.internal \
  -e KEYCLOAK_URL=http://host.docker.internal:8180 \
  plantogether-chat-service
```

**Prerequisites:** Java 21, Maven 3.9+, running PostgreSQL + Redis + RabbitMQ + Keycloak instances.

## Architecture

Real-time group chat microservice for trips. Part of the PlanTogether platform (microservices architecture with Eureka service discovery).

**Port:** 8086
**Base package:** `com.plantogether.chat`

### Key Layers

- **WebSocket (STOMP)** — Primary interface. Endpoint `/ws` with SockJS fallback. Broker prefixes: `/topic`, `/queue`; app prefix: `/app`; user prefix: `/user`.
- **REST API** — Secondary interface for message history, search, pin/unpin, edit, delete under `/api/trips/{tripId}/messages`.
- **Service / Repository / Model / DTO** — Packages exist but are empty; business logic is yet to be implemented.
- **Security** — OAuth2 JWT resource server backed by Keycloak. All WebSocket connections require a Bearer token.

### WebSocket Channels

| Direction | Destination | Purpose |
|-----------|-------------|---------|
| Subscribe | `/topic/trips/{tripId}/messages` | Broadcast messages |
| Subscribe | `/topic/trips/{tripId}/presence` | Presence events |
| Subscribe | `/user/queue/trips/{tripId}/notifications` | Private notifications |
| Send | `/app/trips/{tripId}/message/send` | Send a message |
| Send | `/app/trips/{tripId}/message/react` | Add emoji reaction |
| Send | `/app/trips/{tripId}/message/pin` | Pin a message |
| Send | `/app/trips/{tripId}/presence` | Update presence status |

### Infrastructure Dependencies

| Service | Purpose | Default (local) |
|---------|---------|-----------------|
| PostgreSQL 16 | Message persistence | `localhost:5432/plantogether_chat` |
| Redis | WebSocket session distribution (pub/sub) | `localhost:6379` |
| RabbitMQ | Outbound domain events | `localhost:5672` |
| Keycloak 24+ | JWT auth (JWK set URI) | `http://localhost:8180` |
| MinIO | Image storage | `http://localhost:9000` |
| Eureka | Service discovery | `http://localhost:8761/eureka/` |

### Database Schema

Managed by **Flyway** (`classpath:db/migration`). Hibernate is set to `validate` — never auto-creates schema.

Current schema (`V1__init_schema.sql`) has a minimal `messages` table. The full data model (see README) includes `message_reactions` and `chat_presence` tables that still need to be migrated.

**Full intended model:**
- `messages`: id (UUID), trip_id, keycloak_id (sender), content (max 5000), type (TEXT/IMAGE/LINK/SYSTEM), image_key, link_url, pinned_at, pinned_by, edited_at, created_at
- `message_reactions`: id, message_id (FK), keycloak_id, emoji, created_at
- `chat_presence`: trip_id, keycloak_id, status (ONLINE/OFFLINE/IDLE), last_seen_at

### RabbitMQ Events Published

- `MessageSent` — new message created
- `MessageDeleted` — message deleted
- `UserPresenceChanged` — presence status changed

### Business Rules

- Users can only edit/delete their own messages (within 2h of creation).
- Only trip organizers can pin messages or delete others' messages.
- SYSTEM message type is reserved for service-generated messages.
- No PII stored — only Keycloak UUIDs.
- Redis pub/sub enables horizontal scaling: multiple service instances all relay messages to their connected clients.
