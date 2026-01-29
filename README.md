# Cloud LLM API

An OpenAI-compatible local LLM stub server built with Kotlin and Ktor. Created as a demo project for JavaLand 2026.

The server exposes the same REST API shape as OpenAI (`/v1/chat/completions`, `/v1/models`), making it easy to develop and test LLM-powered applications locally without calling external services.

## Modules

| Module   | Description                                              |
|----------|----------------------------------------------------------|
| `model`  | Shared data classes (request/response) with kotlinx.serialization |
| `server` | Ktor CIO server implementing the OpenAI-compatible API   |
| `client` | HTTP client library wrapping the API                     |
| `agent`  | AI agent integration using the Koog framework            |

## Prerequisites

- JDK 17+

## Build

```bash
./gradlew build
```

## Run

Start the stub server on port 8080:

```bash
./gradlew :server:run
```

Run the client demo (requires a running server):

```bash
./gradlew :client:run
```

Run the agent demo (starts its own server internally):

```bash
./gradlew :agent:run
```

## API Endpoints

### POST /v1/chat/completions

Send a chat completion request:

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "local-stub",
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```

### GET /v1/models

List available models:

```bash
curl http://localhost:8080/v1/models
```

## Tech Stack

- Kotlin 2.1.0
- Ktor 3.2.2
- kotlinx.serialization 1.8.1
- Koog Agents 0.6.1
