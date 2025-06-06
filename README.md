# Scalable WebSocket Messaging Application

A scalable real-time messaging application built with Spring Boot, WebSockets, Kafka, and Redis. This system supports multiple server instances with automatic message routing between them.

## Architecture Overview

This application uses a microservice architecture with the following components:

- **Spring WebSocket Servers**: Handle WebSocket connections from clients
- **Kafka**: Message broker for reliable communication between server instances
- **Redis**: Tracks user connections and routes messages to appropriate servers
- **Nginx**: Load balancer (configured in docker-compose.yml)

## Key Features

- Real-time messaging using WebSockets
- Horizontally scalable architecture
- Public and private messaging
- Message persistence via Kafka
- User session tracking with Redis

## Technology Stack

- **Backend**: Spring Boot, Spring WebSocket, Spring Kafka
- **Message Broker**: Apache Kafka (KRaft mode without Zookeeper)
- **Cache/Store**: Redis
- **Frontend**: HTML, CSS, JavaScript
- **Containerization**: Docker and Docker Compose

## Project Structure

```
.
├── docker-compose.yml    # Docker services configuration
├── spring-ws-server/     # Spring Boot WebSocket server
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/messagingapp/
│   │   │   │   ├── config/       # Configuration classes
│   │   │   │   ├── handler/      # WebSocket and Kafka handlers
│   │   │   │   ├── model/        # Data models
│   │   │   │   └── service/      # Business logic services
│   │   │   └── resources/
│   │   │       ├── static/       # Frontend assets
│   │   │       └── application.yml  # Application configuration
│   └── pom.xml           # Maven dependencies
└── ...
```

## Getting Started

### Prerequisites

- Docker and Docker Compose
- Java 11 or higher (for development)
- Maven (for development)

### Running with Docker Compose

1. Clone the repository
2. Navigate to the project root directory
3. Run the application stack:

```bash
docker-compose up -d
```

The application will be available at http://localhost:8080

### Development Setup

1. Start the infrastructure components:

```bash
docker-compose up -d redis kafka kafka-ui
```

2. Run the Spring application:

```bash
cd spring-ws-server
./mvnw spring-boot:run
```

### Configuration

Each WebSocket server instance has a unique ID configured via the `SERVER_ID` environment variable. This ID is used to:

- Name Kafka topics (`messages-{SERVER_ID}`)
- Name consumer groups (`ws-server-group-{SERVER_ID}`)
- Track server-to-user mappings in Redis

## How It Works

1. Users connect to a WebSocket server via Nginx load balancer
2. User's connection is registered in Redis with their server ID
3. When a user sends a message, the application:
   - Looks up the recipient's server ID from Redis
   - Sends the message to the Kafka topic corresponding to that server
4. Each server consumes messages only from its own topic
5. Messages are delivered to recipients via their WebSocket connection

## Monitoring

- **Kafka UI**: Available at http://localhost:8090
- Server logs available through Docker

## License

[MIT](LICENSE)
