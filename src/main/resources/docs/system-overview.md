# AI Assistant System Overview

## Purpose

This application is a deployment investigation assistant. It combines a local language model, persistent memory, retrieval-augmented generation (RAG), and deployment tools to produce concise, structured answers.

## Request flow

`POST /chat` accepts a message and an optional conversation ID. If no ID is supplied, `ChatController` generates a UUID. The request is then processed by `AssistantService` in the following order:

```text
User request
    |
    +--> Extract durable memory with the LLM
    |       +--> Save accepted memory in PostgreSQL
    |
    +--> Assemble the LLM context
    |       +--> Assistant system prompt
    |       +--> Persistent memories
    |       +--> RAG-retrieved runbook content
    |       +--> Recent conversation messages
    |       +--> Current user message
    |
    +--> Run the bounded tool-calling loop
    |       +--> Ask the LLM whether a tool is needed
    |       +--> Execute requested tools
    |       +--> Append tool results to the context
    |       +--> Repeat up to agent.max-iterations
    |
    +--> Generate a structured final response
    |
    +--> Store the user message and final answer in conversation history
    |
    +--> Return the answer, confidence, and conversation ID
```

## Context assembly

The assistant uses a mutable list of `LLMMessage` objects as the context shared by the tool loop and final-answer generation. Messages are added in a deliberate order:

1. The main system prompt defines the assistant's role and response style.
2. Persistent memory supplies durable application facts.
3. Retrieved documentation supplies relevant deployment instructions.
4. Recent conversation history supplies short-term context.
5. The current user message defines the immediate request.
6. Tool-call messages and tool results are appended by the agent when needed.

The final LLM call receives this complete context and must return JSON matching `schemas/assistant-response.json`.

## Persistent memory

Before answering, `MemoryExtractorService` makes a structured LLM call to determine whether the current message contains a durable fact. Examples include a production region, default service, persistent configuration, or long-term preference.

Accepted memories are stored by `MemoryService` in PostgreSQL. A memory is uniquely identified by its key. Saving an existing key updates its value; saving a new key creates a row. All stored memories are added to every chat request, so memory is currently application-wide rather than scoped to a user or conversation.

The memory API also exposes:

- `POST /memory` to save a supported memory key and value.
- `GET /memory` to return all stored memories.

## Retrieval-augmented generation

At application startup, `RunbookIndexer` loads `knowledge/deployment-runbook.txt`, splits it into chunks, creates an embedding for each chunk, and upserts those chunks into Qdrant.

For each chat request, `RunbookRetriever`:

1. Creates an embedding from the current user message.
2. Searches the configured Qdrant collection.
3. Selects the highest-scoring result.
4. Adds its text to the LLM context as deployment documentation.

Retrieval currently uses one result and does not enforce a minimum similarity score.

## Tool-calling loop

`AgentService` sends the assembled context and tool definitions to the LLM. When the model requests tools, the service appends the assistant tool-call message, dispatches each call through `ToolDispatcher`, and appends every result as a tool message.

The updated context is sent back to the LLM until it stops requesting tools or reaches `agent.max-iterations`. The current tools are:

- `getDeploymentStatus`
- `getDeploymentLogs`

`DeploymentTool` currently provides local example results. It can later be replaced with integrations that query real deployment systems without changing the orchestration flow.

## Conversation history

`ConversationService` stores conversation messages in an in-memory concurrent map keyed by conversation ID. Only the original user message and final assistant answer are retained. System messages, retrieved documentation, and tool interactions are reconstructed for each request.

The number of recent messages added to a request is controlled by `assistant.history-limit`. Conversation history is lost when the application restarts and is not shared between application instances.

## External services

The application depends on:

- An Ollama-compatible chat API for memory extraction, tool selection, and final response generation.
- An Ollama-compatible embedding API for indexing and retrieval.
- Qdrant for vector storage and similarity search.
- PostgreSQL for persistent application memory.

Their endpoints, models, and connection settings are defined in `application.yml`.

## LLM calls per request

A chat request makes at least three language-model calls:

1. Structured memory extraction.
2. Tool selection.
3. Structured final-answer generation.

Every additional tool-loop iteration adds another call. Embedding and Qdrant requests used for RAG are separate from these language-model calls.

If the tool-selection call returns no tool calls, its textual response is not used directly; the final structured-answer call still generates the response returned to the client.

## Package responsibilities

| Package | Responsibility |
| --- | --- |
| `client` | HTTP clients for the LLM, embedding model, and vector store |
| `config` | Typed application properties and Spring bean configuration |
| `controller` | REST endpoints and transport orchestration |
| `entity` | JPA persistence entities |
| `exception` | API error responses and exception handling |
| `model` | Internal LLM, tool-call, and vector-store data structures |
| `rag` | Runbook indexing, chunking, embedding, and retrieval |
| `repository` | Spring Data repositories |
| `request` | Incoming API payloads and validation |
| `response` | API and structured LLM responses |
| `service` | Application orchestration, memory, conversations, and agent behavior |
| `tool` | Tool dispatch and tool implementations |
| `util` | Shared resource-loading utilities |

## Important configuration

| Property | Purpose |
| --- | --- |
| `llm.base-url` | Chat API endpoint |
| `llm.model` | Model used for chat and structured generation |
| `embedding.base-url` | Embedding API endpoint |
| `embedding.model` | Embedding model used by RAG |
| `qdrant.base-url` | Qdrant endpoint |
| `qdrant.collection` | Collection containing runbook vectors |
| `assistant.history-limit` | Maximum recent conversation messages added to context |
| `agent.max-iterations` | Maximum number of tool-selection iterations |
| `spring.datasource.*` | PostgreSQL connection settings |
