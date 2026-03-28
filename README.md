# Cloud Storage System

A distributed cloud storage system with block-level deduplication, delta sync, compression, and conflict resolution. Built as a multi-module Java project with a microservices architecture, the system splits files into content-addressed blocks, stores them in object storage, and provides efficient upload, download, and versioning capabilities.

## Architecture Overview

The project is organized into five Gradle modules:

| Module | Description |
|--------|-------------|
| **api-server** | REST API gateway. Handles file upload/download, revision listing, and conflict resolution. Coordinates with the block server for block-level operations. |
| **block-server** | Manages block storage lifecycle. Splits files into 4 MB blocks, computes SHA-256 hashes, compresses with GZIP, stores in MinIO, and runs cold-storage migration. |
| **notification-server** | Consumes file-change events from RabbitMQ and delivers real-time notifications via Redis Pub/Sub, with an offline backup queue. |
| **load-client** | Automated load-testing client that exercises upload, delta sync, download, revision listing, and conflict scenarios. |
| **common** | Shared DTOs, constants, and utilities used across modules. |

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language & Framework | Java 21, Spring Boot 3.4.3 |
| Metadata Storage | PostgreSQL 16 |
| Caching & Pub/Sub | Redis 7 |
| Event Messaging | RabbitMQ 3 |
| Object Storage | MinIO |
| Load Balancer | Nginx |
| Monitoring | Prometheus + Grafana |
| Orchestration | Docker Compose |

## Prerequisites

- Docker and Docker Compose v2

## Quick Start

```bash
# Start all services
docker compose up -d

# Wait for services to be healthy (about 2-3 minutes for first build)
docker compose ps

# Run load tests
docker compose --profile test up load-client
```

## Service URLs

| Service | URL | Credentials |
|---------|-----|-------------|
| Cloud Storage API | http://localhost | - |
| Grafana | http://localhost:3000 | admin/admin |
| Prometheus | http://localhost:9090 | - |
| MinIO Console | http://localhost:9001 | minioadmin/minioadmin |
| RabbitMQ Management | http://localhost:15672 | guest/guest |

## API Endpoints

**Upload a file:**

```bash
curl -X POST http://localhost/files/upload \
  -F "file=@/path/to/file.mp4" \
  -F "filename=file.mp4"
```

**Download a file:**

```bash
curl -o downloaded.mp4 "http://localhost/files/download?path=/file.mp4"
```

**List file revisions:**

```bash
curl "http://localhost/files/list_revisions?path=/file.mp4&limit=10"
```

## Key Features

- **Block-level deduplication** -- Files are split into 4 MB blocks, each identified by its SHA-256 hash. Identical blocks are stored only once.
- **Delta sync** -- Re-uploading a modified file transfers and stores only the new or changed blocks.
- **Compression** -- Blocks are compressed with GZIP before being written to object storage.
- **Conflict resolution** -- Optimistic locking with version numbers. When a conflict is detected, both versions are preserved.
- **Cold storage** -- Blocks not accessed for 30 days are automatically moved to a cold-storage bucket.
- **Notifications** -- File change events are published via RabbitMQ. An offline backup queue retains events for disconnected clients.
- **High availability** -- 2 API server instances and 2 block server instances run behind an Nginx load balancer.

## Project Structure

```
cloud-storage/
├── api-server/                 # REST API gateway
│   └── src/main/java/          # Controllers, services, config
├── block-server/               # Block storage & processing
│   └── src/main/
│       ├── java/               # Block splitting, hashing, compression, cold storage
│       └── resources/          # schema.sql, application config
├── notification-server/        # Event notifications
│   └── src/main/java/          # RabbitMQ consumers, Redis publishers
├── load-client/                # Load testing client
│   └── src/main/java/          # Test scenarios (upload, delta, conflict)
├── common/                     # Shared code
│   └── src/main/java/          # DTOs, constants
├── grafana/                    # Grafana dashboards & provisioning
├── prometheus/                 # Prometheus scrape configuration
├── nginx/                      # Nginx load-balancer config
├── video/                      # Sample files for load testing
├── docker-compose.yml          # Full stack orchestration
├── Dockerfile                  # Multi-stage build (shared across modules)
├── build.gradle                # Root build script
├── settings.gradle             # Module declarations
└── gradle.properties           # Dependency versions
```

## Load Testing

The `load-client` module runs an automated test suite that exercises the full system:

1. **Upload** -- Uploads sample files from the `video/` directory.
2. **Re-upload (delta sync)** -- Re-uploads the same files to verify that only new/modified blocks are stored.
3. **Download** -- Downloads files and verifies integrity.
4. **Revisions** -- Lists file revision history.
5. **Conflict test** -- Simulates concurrent uploads to trigger conflict resolution.

Run with:

```bash
docker compose --profile test up load-client
```

## Monitoring

Grafana is available at [http://localhost:3000](http://localhost:3000) with a pre-provisioned dashboard that includes:

- Request rates and latency percentiles
- Upload and download metrics
- Block processing throughput
- Deduplication rates
- Compression ratio
- Cold storage migration activity

## Development

```bash
# Build all modules
./gradlew build

# Run tests
./gradlew test

# Build a specific module
./gradlew :block-server:build
```
