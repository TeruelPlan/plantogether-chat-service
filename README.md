# Chat Service

> Service de chat groupe en temps réel par voyage

## Rôle dans l'architecture

Le Chat Service fournit un canal de communication en temps réel pour chaque voyage. Utilisant WebSocket STOMP, il permet
aux participants d'échanger des messages instantanément, partager des images et des liens, réagir aux messages avec des
emojis, et épingler les messages importants. Redis gère les sessions WebSocket pour une scalabilité distribuée.

## Fonctionnalités

- Chat groupe en temps réel par voyage via WebSocket STOMP
- Support des messages texte, images et liens
- Réactions emoji sur les messages (like, laugh, love, etc.)
- Épinglage de messages importants
- Historique des messages persisté
- Notifications de présence (utilisateur en ligne/hors ligne)
- Édition et suppression de messages (2h après création)
- Recherche dans l'historique
- Modération : suppression de messages par organisateur

## Endpoints WebSocket

**Connexion :**

```
ws://[host]:8086/ws/trips/{tripId}/chat
Header: Authorization: Bearer [token]
```

**Canaux STOMP (subscription) :**

- `/topic/trips/{tripId}/messages` : recevoir tous les messages du voyage
- `/user/queue/trips/{tripId}/notifications` : notifications privées
- `/topic/trips/{tripId}/presence` : événements de présence

**Canaux STOMP (envoi - POST) :**

- `/app/trips/{tripId}/message/send` : envoyer un message
- `/app/trips/{tripId}/message/react` : ajouter une réaction
- `/app/trips/{tripId}/message/pin` : épingler un message
- `/app/trips/{tripId}/presence` : mettre à jour la présence

## Endpoints REST

| Méthode | Endpoint                                       | Description                         |
|---------|------------------------------------------------|-------------------------------------|
| GET     | `/api/trips/{tripId}/messages`                 | Récupérer l'historique (pagination) |
| POST    | `/api/trips/{tripId}/messages/search`          | Chercher dans l'historique          |
| POST    | `/api/trips/{tripId}/messages/{messageId}/pin` | Épingler un message                 |
| DELETE  | `/api/trips/{tripId}/messages/{messageId}/pin` | Dépingler                           |
| DELETE  | `/api/trips/{tripId}/messages/{messageId}`     | Supprimer un message                |
| PUT     | `/api/trips/{tripId}/messages/{messageId}`     | Éditer un message                   |
| GET     | `/api/trips/{tripId}/messages/pinned`          | Récupérer les messages épinglés     |

## Modèle de données

**Message**

- `id` (UUID) : identifiant unique
- `trip_id` (UUID, FK) : voyage associé
- `keycloak_id` (UUID) : auteur du message
- `content` (String) : contenu du message (max 5000 caractères)
- `type` (ENUM: TEXT, IMAGE, LINK, SYSTEM) : type de message
- `image_key` (String, nullable) : clé de l'image sur MinIO (si type=IMAGE)
- `link_url` (String, nullable) : URL du lien (si type=LINK)
- `pinned_at` (Timestamp, nullable) : date d'épinglage
- `pinned_by` (UUID, nullable) : qui a épinglé
- `edited_at` (Timestamp, nullable)
- `created_at` (Timestamp)

**MessageReaction**

- `id` (UUID)
- `message_id` (UUID, FK)
- `keycloak_id` (UUID) : qui réagit
- `emoji` (String) : code emoji (ex: "like", "love", "laugh")
- `created_at` (Timestamp)

**ChatPresence**

- `trip_id` (UUID)
- `keycloak_id` (UUID)
- `status` (ENUM: ONLINE, OFFLINE, IDLE)
- `last_seen_at` (Timestamp)

## Événements (RabbitMQ)

**Publie :**

- `MessageSent` — Émis lors d'un nouveau message
- `MessageDeleted` — Émis lors de la suppression d'un message
- `UserPresenceChanged` — Émis lors d'un changement de statut

**Consomme :** (aucun)

## Configuration

```yaml
server:
  port: 8086
  servlet:
    context-path: /

spring:
  application:
    name: plantogether-chat-service
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  datasource:
    url: jdbc:postgresql://postgres:5432/plantogether_chat
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  rabbitmq:
    host: ${RABBITMQ_HOST:rabbitmq}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER}
    password: ${RABBITMQ_PASSWORD}
  redis:
    host: ${REDIS_HOST:redis}
    port: ${REDIS_PORT:6379}

keycloak:
  serverUrl: ${KEYCLOAK_SERVER_URL:http://keycloak:8080}
  realm: ${KEYCLOAK_REALM:plantogether}
  clientId: ${KEYCLOAK_CLIENT_ID}

minio:
  endpoint: ${MINIO_ENDPOINT:http://minio:9000}
  accessKey: ${MINIO_ACCESS_KEY}
  secretKey: ${MINIO_SECRET_KEY}
  bucket: ${MINIO_BUCKET:plantogether}

websocket:
  stomp:
    enabled: true
    heartbeat: 30000  # ms
```

## Lancer en local

```bash
# Prérequis : Docker Compose (infra), Java 21+, Maven 3.9+

# Option 1 : Maven
mvn spring-boot:run

# Option 2 : Docker
docker build -t plantogether-chat-service .
docker run -p 8086:8081 \
  -e KEYCLOAK_SERVER_URL=http://host.docker.internal:8080 \
  -e DB_USER=postgres \
  -e DB_PASSWORD=postgres \
  -e REDIS_HOST=host.docker.internal \
  -e MINIO_ENDPOINT=http://host.docker.internal:9000 \
  plantogether-chat-service
```

## Dépendances

- **Keycloak 24+** : authentification des WebSocket
- **PostgreSQL 16** : persistance des messages
- **Redis** : gestion des sessions WebSocket, pub/sub
- **RabbitMQ** : publication d'événements
- **MinIO** : stockage des images uploadées
- **Spring Boot 3.3.6** : framework web
- **Spring WebSocket** : STOMP over WebSocket
- **Spring Session Redis** : session distribuée

## Architecture WebSocket

```
Client (Flutter App)
    |
    v
[WebSocket STOMP] --> Chat Service --> [Redis PubSub] --> autres clients
                           |
                           v
                    [PostgreSQL] (persistance)
```

Redis assure que même si plusieurs instances du Chat Service tournent, tous les clients reçoivent les messages.

## Payload STOMP

**Envoyer un message :**

```json
{
  "content": "Bonjour!",
  "type": "TEXT"
}
```

**Réaction emoji :**

```json
{
  "messageId": "uuid-xxx",
  "emoji": "like"
}
```

**Épinglage :**

```json
{
  "messageId": "uuid-xxx"
}
```

## Notes de sécurité

- Tous les WebSocket requièrent un token Bearer Keycloak valide
- Les utilisateurs ne peuvent éditer/supprimer que leurs propres messages
- Seuls les organisateurs peuvent épingler/supprimer les messages d'autres
- Les images sont validées (taille max 5 MB, formats autorisés)
- Zéro PII stockée (seuls les UUIDs Keycloak)
- Les messages sont chiffrés en transit (WSS en production)

## Modération

Les messages de type SYSTEM ne peuvent être créés que par le service lui-même (ex: "Alice a rejoint", "Bob a quitté").

## Limitations

- Taille maximale d'un message : 5000 caractères
- Taille maximale d'une image : 5 MB
- Historique conservé : configurable (par défaut 90 jours)
- Nombre de réactions par message : max 100
