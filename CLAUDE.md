# CLAUDE.md

This file provides guidance to Claude when working with code in this repository.

## Commands

```bash
# Build
mvn clean package

# Build without tests
mvn clean package -DskipTests

# Run locally
mvn spring-boot:run

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Docker build
docker build -t plantogether-chat-service .
docker run -p 8086:8086 \
  -e DB_USER=postgres -e DB_PASSWORD=postgres \
  -e REDIS_HOST=host.docker.internal \
  -e KEYCLOAK_URL=http://host.docker.internal:8180 \
  plantogether-chat-service
```

**Prerequisites:** Java 21, Maven 3.9+, running PostgreSQL + Redis + Keycloak.

## Architecture

Spring Boot 3.3.6 microservice (Java 21). Provides real-time group chat for trips via WebSocket (STOMP).

**Port:** REST + WebSocket `8086` (no gRPC server)

**Package:** `com.plantogether.chat`

### Package structure

```
com.plantogether.chat/
├── config/          # SecurityConfig, WebSocketConfig, RabbitConfig
├── controller/      # STOMP message handlers + REST history controller
├── domain/          # JPA entity (Message)
├── repository/      # Spring Data JPA
├── service/         # Business logic
├── dto/             # Request/Response DTOs (Lombok @Data @Builder)
├── grpc/
│   └── client/      # TripGrpcClient (CheckMembership → trip-service:9081)
└── event/
    └── publisher/   # RabbitMQ publishers (ChatMessageSent)
```

### Infrastructure dependencies

| Dependency | Default (local) | Purpose |
|---|---|---|
| PostgreSQL 16 | `localhost:5432/plantogether_chat` | Message persistence (db_chat) |
| Redis | `localhost:6379` | WebSocket session distribution (pub/sub for horizontal scaling) |
| RabbitMQ | `localhost:5672` | Outbound domain events |
| Keycloak 24+ | `localhost:8180` | JWT auth (JWK set URI) |
| trip-service gRPC | `localhost:9081` | CheckMembership before accepting messages |


### Domain model (db_chat)

**`message`** — id (UUID), trip_id (UUID), sender_id (Keycloak UUID), content (TEXT), type (`TEXT`/`IMAGE`/`SYSTEM`),
pinned_at (TIMESTAMP nullable), created_at.

No separate reactions or presence tables (client-side resolution). `SYSTEM` type is reserved for service-generated
messages (e.g. expense created, member joined notifications relayed from other services).

### WebSocket (STOMP)

Endpoint: `/ws` (HTTP upgrade, SockJS fallback). All connections require a Bearer token in the handshake.

| Direction | Destination | Purpose |
|---|---|---|
| SUBSCRIBE | `/topic/trips/{tripId}/chat` | Receive all messages in a trip chat |
| SUBSCRIBE | `/topic/trips/{tripId}/updates` | Receive real-time updates (expenses, votes, etc.) from other services |
| SUBSCRIBE | `/user/queue/notifications` | Private notifications for the connected user |
| SEND | `/app/trips/{tripId}/chat` | Send a message `{ content, type }` |

**Important:** display names and avatars are **not resolved server-side**. Messages are stored with `sender_id`
(Keycloak UUID). The Flutter client resolves the display name from its in-memory member list. This avoids gRPC
calls on the hot path.

### REST API

| Method | Endpoint | Notes |
|---|---|---|
| GET | `/api/v1/trips/{tripId}/messages` | Message history (paginated) |

### RabbitMQ events

**Publishes** (exchange `plantogether.events`):
- `chat.message.sent` — routing key `chat.message.sent` — when a new message is saved (consumed by notification-service)

This service does **not** consume trip or member events directly. The `/topic/trips/{tripId}/updates` STOMP channel
can relay events published by other services if needed (via a shared consumer).

### Security

- Stateless JWT via `KeycloakJwtConverter` — `realm_access.roles` → `ROLE_<ROLE>` Spring authorities
- WebSocket connections authenticated via Bearer token in the HTTP handshake header
- ORGANIZER can pin messages or delete others' messages; users can only edit/delete their own (within 2h)
- Zero PII stored — only Keycloak UUIDs

### Horizontal scaling

Redis pub/sub enables multiple chat-service instances: each instance subscribes to Redis channels for the trips
its connected clients are watching, and relays messages to them. Stateless regarding WebSocket routing.

### Environment variables

| Variable | Default |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_USER` | `plantogether` |
| `DB_PASSWORD` | `plantogether` |
| `RABBITMQ_HOST` | `localhost` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |
| `KEYCLOAK_URL` | `http://localhost:8180` |
| `TRIP_SERVICE_GRPC_HOST` | `localhost` |
| `TRIP_SERVICE_GRPC_PORT` | `9081` |

