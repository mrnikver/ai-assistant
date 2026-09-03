# AI Assistant System Overview

## Purpose

This application is a deployment investigation assistant. It combines a local language model, persistent memory, retrieval-augmented generation (RAG), and an application-controlled tool loop to produce concise, structured answers.

## Request flow

`POST /chat` accepts a message and an optional conversation ID. If no ID is supplied, `ChatController` generates a UUID. The request is then processed by `AssistantService` in the following order:

```text
User request
    |
    +--> Extract durable memory with the LLM
    |       +--> Save accepted memory in PostgreSQL
    |
    +--> Assemble the agent context
    |       +--> Assistant system prompt
    |       +--> Persistent memories
    |       +--> Recent conversation messages
    |       +--> Current user message
    |
    +--> Run the bounded tool-calling loop
    |       +--> LLM answers or requests a registered tool
    |       +--> ToolRegistry validates and executes requested tools
    |       +--> Tool results become observations in the context
    |       +--> LLM receives observations and decides again
    |       +--> Repeat until an answer or agent.max-iterations
    |
    +--> Store the user message and final answer in conversation history
    |
    +--> Complete and retain the execution trace
    |
    +--> Return the answer, confidence, conversation ID, message ID, and trace summary
```

## Context assembly

The assistant uses a mutable list of `LLMMessage` objects as the context shared by the agent loop. Messages are added in a deliberate order:

1. The main system prompt defines the assistant's role and response style.
2. Persistent memory supplies durable application facts.
3. Recent conversation history supplies short-term context.
4. The current user message defines the immediate request.
5. Tool-call messages and tool observations are appended by the agent when needed.

Every agent turn receives this context and the registered tool definitions. The system prompt asks a no-tool response to use the same answer/confidence JSON shape as `schemas/assistant-response.json`; it becomes the result immediately. If a local model returns plain text despite that instruction, the client preserves it as a medium-confidence answer instead of failing the request.

## Persistent memory

Before answering, `MemoryExtractorService` makes a structured LLM call to determine whether the current message contains a durable fact. Examples include a production region, default service, persistent configuration, or long-term preference.

Accepted memories are stored by `MemoryService` in PostgreSQL. A memory is uniquely identified by its key. Saving an existing key updates its value; saving a new key creates a row. All stored memories are added to every chat request, so memory is currently application-wide rather than scoped to a user or conversation.

The memory API also exposes:

- `POST /memory` to save a supported memory key and value.
- `GET /memory` to return all stored memories.

## Retrieval-augmented generation

At application startup, `RunbookIndexer` loads `knowledge/deployment-runbook.txt`, splits it into chunks, and creates an embedding for the first chunk. The backend uses the resulting vector size to create the configured Qdrant collection with cosine distance when it does not already exist. It then creates the remaining embeddings and upserts all chunks into Qdrant.

When the LLM requests `search_knowledge_base`, `KnowledgeBaseSearchTool` delegates to `RunbookRetriever`, which:

1. Creates an embedding from the focused query selected by the LLM.
2. Searches the configured Qdrant collection.
3. Returns up to the requested `topK` results.
4. Lets the tool format those chunks as an observation for the next LLM turn.

Retrieval no longer runs automatically. The tool defaults to three results, accepts between one and ten, and does not currently enforce a minimum similarity score.

## Tool-calling loop

`AgentService` sends the assembled context and definitions from `ToolRegistry` to the LLM. When the model requests tools, the service appends the assistant tool-call message, asks the registry to validate and execute each call, and appends every result as a tool observation.

The updated context is sent back to the LLM until it returns a structured answer or reaches `agent.max-iterations`. The current tool is:

- `search_knowledge_base(query, topK?)`

`ToolRegistry` is an allow-list: a model-generated name cannot invoke an arbitrary Java method. Unknown tools, invalid arguments, and operational tool failures become controlled observations so the model can recover on a later turn. Add another capability by implementing `Tool`, exposing it as a Spring bean, and supplying an Ollama-compatible definition; Spring injects it into the registry without changing the loop.

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

A chat request makes at least two language-model calls:

1. Structured memory extraction.
2. An agent decision that either requests a tool or returns the structured final answer.

Every additional tool-loop iteration adds another call. Embedding and Qdrant requests used for RAG are separate from these language-model calls.

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
| `tool` | Registered tool contracts, allow-list execution, validation, and implementations |
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
| `agent.max-iterations` | Maximum LLM decision/tool iterations per request; defaults to 5 and must be at least 1 |
| `trace.max-entries` | Maximum completed traces kept in the local bounded store; defaults to 500 and evicts oldest first |
| `spring.datasource.*` | PostgreSQL connection settings |

## Ollama and qwen3 limitations

- Tool selection is probabilistic. A small local model may skip a useful search, choose a weak query, or request unnecessary retrieval even with a clear tool description.
- Tool-enabled calls do not also set Ollama's structured-output `format`. Some Ollama/model combinations fail to emit tool calls when tools and a response format are constrained together, so the system prompt requests final JSON and the client provides a plain-text fallback.
- The application validates names and arguments, but it cannot guarantee that qwen3 will repair a controlled tool error on its next turn.
- Large `topK` results consume model context. The tool caps `topK` at 10; deployments should still configure a sufficient Ollama context window for multi-step runs.
- Tool-calling quality depends on the installed qwen3 build and Ollama version. Keep the local model current and verify the model advertises tool support when upgrading.

## Request-flow logging

Every HTTP request receives an `X-Request-ID`. The backend accepts an existing value from the request header or generates a UUID, returns it in the response header, and includes it in every log entry produced on the request thread. Chat requests also include their conversation ID in the logging context.

At `INFO` level, logs describe the major lifecycle events: HTTP request boundaries, assistant stages, memory persistence, retrieval results, agent iterations, tool executions, and final response confidence. At `DEBUG` level, logs add message counts, input lengths, vector dimensions, resource loading, and external-client timings.

Prompt text, memory values, embedding vectors, credentials, and full external responses are deliberately excluded. Use `requestId` to trace one HTTP request and `conversationId` to find requests belonging to the same conversation. Logging levels and the console pattern are configured under `logging` in `application.yml`.

## Agent execution traces

Each chat request creates an application-owned execution trace. The trace explains observable system behavior without recording hidden chain of thought. It contains timings, statuses, model and tool names, bounded tool arguments, retrieval counts, memory lookup counts, and controlled error categories. It deliberately excludes prompts, assembled model context, retrieved chunk contents, memory values, credentials, raw external responses, and private reasoning.

The hierarchy is represented by globally unique `traceId` and `spanId` values plus each span's `parentSpanId`:

```text
Agent run
    +--> Structured memory-extraction LLM call
    +--> Memory lookup
    +--> Agent iteration 1
    |       +--> Agent LLM decision
    |       +--> Tool call
    |               +--> Knowledge-base search
    |                       +--> Embedding generation
    |                       +--> Qdrant vector search
    +--> Agent iteration 2
    |       +--> Agent LLM decision
    +--> Final response
```

The parent relationship represents causality, not merely clock order. For example, embedding and vector-search spans are children of the knowledge search that required them, which is a child of the validated tool call selected in an agent iteration. The UI can reconstruct arbitrary nesting from these identifiers without hard-coding a fixed tree depth.

`AssistantService` starts and completes the root trace. `AgentService` records iterations, `LLMClient` records every Ollama chat call, `ToolRegistry` records allowed and rejected tool requests, `KnowledgeBaseSearchTool` records retrieval intent and counts, the embedding and vector clients record external retrieval stages, and `MemoryService` records actual persistent-memory reads. Summary counts are derived from the recorded span types rather than maintained as separate mutable counters.

`POST /chat` returns the summary with the assistant message, including the `traceId`, total duration, operation counts, and status. It does not return detailed spans. `GET /api/traces/{traceId}` returns an API DTO containing the full flat span set only when the user opens “View execution.” The generated `messageId` lets the frontend keep the summary associated with the exact assistant message that produced it.

`TraceStore` separates trace capture from retention. The current `InMemoryTraceStore` keeps at most `trace.max-entries` completed traces and evicts the oldest trace when that bound is exceeded. Traces are therefore lost on restart, are not shared across application instances, and may return HTTP 404 after eviction. A production deployment can replace this implementation with durable storage without changing agent execution or the public trace contract.

The internal trace model intentionally resembles OpenTelemetry spans—IDs, parent relationships, timestamps, duration, status, type, name, and attributes—without adding an OpenTelemetry dependency. A future exporter can map completed `AgentTrace` and `TraceSpan` records to an OpenTelemetry SDK or collector while retaining the current sanitization boundary and API DTOs.
