# Chat Service

> Real-time messaging service per trip (WebSocket STOMP)

## Role in the Architecture

The Chat Service provides real-time messaging within each trip via WebSocket STOMP. Messages are persisted
in the database, and the display name of the sender is resolved client-side by Flutter (which already holds
the member list in memory) — the server only stores the `device_id`. It does not expose a gRPC server.

## Features

- Real-time messaging via WebSocket STOMP
- Message persistence (TEXT / IMAGE / SYSTEM)
- Pinned messages (`pinned_at`)
- Real-time update channel (expenses, votes, tasks)
- Personal notifications per user
- Trip membership verification via gRPC before connection

## WebSocket STOMP API

The WebSocket connection is established on `/ws` with the `X-Device-Id` header in the STOMP connect headers.

| Type | Destination | Description |
|------|-------------|-------------|
| CONNECT | `/ws` | WebSocket connection (HTTP upgrade, `X-Device-Id` header) |
| SUBSCRIBE | `/topic/trips/{id}/chat` | Receive trip messages |
| SUBSCRIBE | `/topic/trips/{id}/updates` | Real-time updates (expenses, votes, tasks) |
| SEND | `/app/trips/{id}/chat` | Send a message `{ content, type }` |
| SUBSCRIBE | `/user/queue/notifications` | Personal notifications |

## gRPC Client

- `TripService.IsMember(tripId, deviceId)` — membership verification at STOMP subscription

## Data Model (`db_chat`)

**message**

| Column | Type | Description |
|--------|------|-------------|
| `id` | UUID PK | Unique identifier (UUID v7) |
| `trip_id` | UUID NOT NULL | Trip reference |
| `sender_id` | UUID NOT NULL | device_id of the sender |
| `content` | TEXT NOT NULL | Message content |
| `type` | ENUM NOT NULL | TEXT / IMAGE / SYSTEM |
| `pinned_at` | TIMESTAMP NULLABLE | Pin date (null = not pinned) |
| `created_at` | TIMESTAMP NOT NULL | |

> Display name and avatar resolution is performed client-side by Flutter, which maintains the trip member list in memory. The server only stores and transmits the `device_id`.

## RabbitMQ Events (Exchange: `plantogether.events`)

**Publishes:**

| Routing Key | Trigger |
|-------------|---------|
| `chat.message.sent` | New message sent |

**Consumes:** none (real-time updates on `/topic/trips/{id}/updates` are triggered directly by other services via STOMP or by consuming RabbitMQ events depending on implementation)

## Rate Limiting

| Rule | Limit |
|------|-------|
| WebSocket connections | 5 simultaneous connections per device |

## Configuration

```yaml
server:
  port: 8086

spring:
  application:
    name: plantogether-chat-service
  datasource:
    url: jdbc:postgresql://postgres:5432/db_chat
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
  rabbitmq:
    host: ${RABBITMQ_HOST:rabbitmq}

grpc:
  client:
    trip-service:
      address: static://trip-service:9081
```

## Running Locally

```bash
# Prerequisites: docker compose up -d
# + plantogether-proto and plantogether-common installed

mvn spring-boot:run
```

## Dependencies

- **PostgreSQL 16** (`db_chat`): message persistence
- **RabbitMQ**: event publishing (`chat.message.sent`)
- **Redis**: WebSocket session distribution (pub/sub for horizontal scaling)
- **Trip Service** (gRPC 9081): membership verification
- **plantogether-proto**: gRPC contracts (client)
- **plantogether-common**: event DTOs, DeviceIdFilter, SecurityAutoConfiguration, CorsConfig

## Security

- Anonymous device-based identity: `X-Device-Id` header on every request and in STOMP connect headers
- `DeviceIdFilter` (from plantogether-common, auto-configured via `SecurityAutoConfiguration`) extracts the device UUID and sets the SecurityContext principal
- No JWT, no Keycloak, no login, no sessions
- Trip membership is verified via gRPC at subscription
- Zero PII stored (only `device_id` references)
- 5 simultaneous WebSocket connections max per device (rate limiting)
