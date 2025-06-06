# Spring Boot WebSocket Server

This is a Spring Boot version of the WebSocket server, providing the same functionality as the Node.js implementation with improved scalability and integration with Spring ecosystem.

## Features

- WebSocket-based real-time messaging
- User authentication during WebSocket handshake
- Session persistence with Redis
- Message distribution across multiple server instances via Kafka
- Direct messaging between users
- Broadcasting messages to all connected users
- Dynamic server instance identification
- Spring Cloud Stream for Kafka integration
- Responsive web UI for chat

## Requirements

- Java 17 or higher
- Maven 3.6 or higher
- Redis server
- Kafka server

## Configuration

The application can be configured using environment variables or by modifying the `application.yml` file:

| Environment Variable | Description | Default Value |
|----------------------|-------------|---------------|
| `SERVER_ID` | Unique identifier for this server instance | Generated UUID |
| `REDIS_HOST` | Redis server hostname | localhost |
| `REDIS_PORT` | Redis server port | 6379 |
| `KAFKA_BROKERS` | Comma-separated list of Kafka broker addresses | localhost:9092 |
| `PORT` | Server HTTP port | 8080 |

## Building and Running

### Build the application

```bash
mvn clean package
```

### Run locally

```bash
java -jar target/messaging-app-0.0.1-SNAPSHOT.jar
```

### Run with Docker

```bash
docker build -t spring-ws-server .
docker run -p 8080:8080 -e REDIS_HOST=redis -e KAFKA_BROKERS=kafka:9092 spring-ws-server
```

## API Endpoints

- `/ws` - WebSocket endpoint
  - Query parameters:
    - `X-Auth-User-Id` - User ID for authentication
  - Headers (alternative):
    - `X-Auth-User-Id` - User ID for authentication

- `/health` - Health check endpoint
  - Returns server status, server ID, and timestamp

## WebSocket Communication

### Message Types

1. **Chat Messages**:
   ```json
   {
     "type": "chat",
     "message": "Hello everyone!",
     "recipientId": "" // Optional, if empty broadcasts to all users
   }
   ```

2. **Server Info Messages**:
   ```json
   {
     "type": "info",
     "message": "Welcome to the chat!",
     "serverId": "server123",
     "clientId": "client456",
     "clients": 42,
     "connectedUsers": ["user1", "user2", "user3"]
   }
   ```

3. **User Status Messages**:
   ```json
   {
     "type": "user-joined",
     "userId": "user1"
   }
   ```
   ```json
   {
     "type": "user-left",
     "userId": "user1"
   }
   ```

## Architecture

The Spring Boot server maintains the same distributed architecture as the Node.js implementation:
- Redis for tracking user-server mappings and managing sessions
- Kafka for distributing messages between server instances
- WebSockets for real-time communication with clients

## Integration with Docker Compose

This Spring Boot server can be added to the existing docker-compose.yml to run alongside or replace the Node.js implementation.

## Implementation Details

- Built with Spring Boot 3.1.x
- Uses Spring WebSocket for WebSocket handling
- Uses Spring Cloud Stream for Kafka integration
- Uses Spring Data Redis for Redis integration
- Implements constructor injection pattern without @Autowired annotations
- Includes health check endpoint
- Supports scaling with multiple instances
