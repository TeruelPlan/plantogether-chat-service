# Chat Service

> Service de messagerie temps réel par voyage (WebSocket STOMP)

## Rôle dans l'architecture

Le Chat Service fournit la messagerie temps réel au sein de chaque voyage via WebSocket STOMP. Les messages sont
persistés en base et le display_name de l'expéditeur est résolu côté Flutter (qui dispose déjà de la liste des
membres en mémoire) — le serveur ne stocke que le `keycloak_id`. Il n'expose pas de serveur gRPC.

## Fonctionnalités

- Messagerie temps réel via WebSocket STOMP
- Persistance des messages (TEXT / IMAGE / SYSTEM)
- Messages épinglés (`pinned_at`)
- Canal de mises à jour temps réel (dépenses, votes, tâches)
- Notifications personnelles par utilisateur
- Vérification d'appartenance au trip via gRPC avant connexion

## API WebSocket STOMP

La connexion WebSocket s'établit sur `/ws` avec le token Bearer dans les headers STOMP.

| Type | Destination | Description |
|------|-------------|-------------|
| CONNECT | `/ws` | Connexion WebSocket (upgrade HTTP, header `Authorization: Bearer {token}`) |
| SUBSCRIBE | `/topic/trips/{id}/chat` | Recevoir les messages du trip |
| SUBSCRIBE | `/topic/trips/{id}/updates` | Mises à jour temps réel (dépenses, votes, tâches) |
| SEND | `/app/trips/{id}/chat` | Envoyer un message `{ content, type }` |
| SUBSCRIBE | `/user/queue/notifications` | Notifications personnelles |

## gRPC Client

- `TripService.CheckMembership(tripId, userId)` — vérification d'appartenance à la connexion STOMP

## Modèle de données (`db_chat`)

**message**

| Colonne | Type | Description |
|---------|------|-------------|
| `id` | UUID PK | Identifiant unique (UUID v7) |
| `trip_id` | UUID NOT NULL | Référence au trip |
| `sender_id` | UUID NOT NULL | keycloak_id de l'expéditeur |
| `content` | TEXT NOT NULL | Contenu du message |
| `type` | ENUM NOT NULL | TEXT / IMAGE / SYSTEM |
| `pinned_at` | TIMESTAMP NULLABLE | Date d'épinglage (null = non épinglé) |
| `created_at` | TIMESTAMP NOT NULL | |

> La résolution du `display_name` et de l'`avatar_url` est effectuée côté Flutter, qui maintient la liste des membres du trip en mémoire. Le serveur ne stocke et ne transmet que le `keycloak_id`.

## Événements RabbitMQ (Exchange : `plantogether.events`)

**Publie :**

| Routing Key | Déclencheur |
|-------------|-------------|
| `chat.message.sent` | Nouveau message envoyé |

**Consomme :** aucun (les mises à jour temps réel sur `/topic/trips/{id}/updates` sont déclenchées directement par les autres services via STOMP ou par consommation d'événements RabbitMQ selon l'implémentation)

## Rate Limiting

| Règle | Limite |
|-------|--------|
| Connexions WebSocket | 5 connexions simultanées par utilisateur |

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

## Lancer en local

```bash
# Prérequis : docker compose --profile essential up -d
# + plantogether-proto et plantogether-common installés

mvn spring-boot:run
```

## Dépendances

- **Keycloak 24+** : validation JWT (token STOMP header)
- **PostgreSQL 16** (`db_chat`) : persistance des messages
- **RabbitMQ** : publication d'événements (`chat.message.sent`)
- **Trip Service** (gRPC 9081) : vérification d'appartenance
- **plantogether-proto** : contrats gRPC (client)
- **plantogether-common** : DTOs events, CorsConfig

## Sécurité

- La connexion WebSocket requiert un token Bearer valide (transmis dans les headers STOMP)
- L'appartenance au trip est vérifiée via gRPC à l'abonnement
- Zero PII stockée (uniquement des `keycloak_id`)
- 5 connexions WebSocket simultanées max par utilisateur (rate limiting)
