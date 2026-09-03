# AI Assistant System Overview

## Purpose

This deployment investigation assistant combines a local LLM, persistent memory, conversation history, retrieval-augmented generation (RAG), and mocked operational data. A Supervisor owns user interaction and delegates evidence gathering to two independent specialists.

```text
User request
     |
     v
AssistantService
     +--> memory extraction
     +--> persistent memory + conversation history
     |
     v
Supervisor Agent
     +--> Knowledge Agent
     |       +--> search_knowledge_base
     |               +--> Embedding
     |               +--> Qdrant
     |
     +--> Runtime Agent
             +--> getDeploymentStatus
             +--> getDeploymentLogs
                     +--> mock deployment data
     |
     v
Supervisor synthesis --> final answer
```

## Request flow and context assembly

`POST /chat` accepts a message and optional conversation ID. `AssistantService` starts a trace, extracts supported durable memory, loads PostgreSQL memory, adds recent conversation messages and the current user message, and passes that context to `SupervisorAgent`. The Supervisor decides whether it can answer, needs one specialist, or needs both. Specialist findings return as tool observations; only the Supervisor produces the public `AssistantResponse`. The completed user message and final answer are saved in conversation history and the response includes confidence and a trace summary.

The Supervisor receives assistant system context, persistent memory, short-term history, and the current request. Specialists receive their own role prompt plus only the focused question delegated by the Supervisor. They do not inherit hidden Supervisor prompts, memory values, or its mutable tool conversation.

## Agent architecture

`AgentRuntime` implements the reusable bounded LLM/tool loop. An `AgentDefinition` supplies the agent name, type, system prompt, explicit concrete tool list, iteration limit, and delegating parent. For every model decision the runtime exposes definitions for only that list, validates calls through a request-scoped `ToolRegistry`, appends observations, and stops at the configured limit.

The hierarchy is fixed and non-recursive:

- `SupervisorAgent` may invoke `ask_knowledge_agent` and `ask_runtime_agent` only. It cannot directly execute RAG or runtime tools.
- `KnowledgeAgent` may invoke `search_knowledge_base` only. It cannot access runtime tools or claim current operational state.
- `RuntimeAgent` may invoke `getDeploymentStatus` and `getDeploymentLogs` only. It cannot access RAG or treat mock state as documentation.
- Specialized agents cannot invoke the Supervisor or one another.

Only application-controlled `AgentDefinition` and `ToolRegistry` configuration grants capabilities. Model-generated tool names are inert data until Java validates them.

## Supervisor behavior

The Supervisor classifies the evidence domains implied by the request. Documentation, architecture, source, and runbook questions go to Knowledge. Current health, deployment status, and operational logs go to Runtime. Combined questions can delegate to both before a final synthesis. Delegations are explicit tool calls, bounded by `agent.supervisor-max-iterations`, and visible in the execution trace.

## Knowledge Agent and RAG

At startup, `RunbookIndexer` indexes the bundled deployment runbook and `ProjectKnowledgeIndexer` scans configured backend and UI roots. Java, Markdown, TypeScript, and TSX content is chunked with source metadata, embedded, and upserted into Qdrant with stable IDs.

`search_knowledge_base(query, topK?)` remains a retrieval-only capability. It embeds the focused query, searches Qdrant, and returns ranked chunks as an observation. `topK` defaults to 3 and is limited to 1–10. Retrieval is never available to the Runtime Agent and never runs automatically outside a Knowledge Agent decision.

## Runtime Agent and restored mocked tools

The runtime capabilities were recovered from Git history predating the RAG refactor. Their names, descriptions, required `serviceName` argument, and deterministic behavior are preserved:

| Tool | payments-service | orders-service | unknown service |
| --- | --- | --- | --- |
| `getDeploymentStatus` | `FAILED` | `RUNNING` | `UNKNOWN` |
| `getDeploymentLogs` | `ERROR: Database connection refused` | `Deployment completed successfully` | `No deployment logs found` |

Both tools validate that `serviceName` is a non-blank string. They are intentionally mock operational data, not live infrastructure integrations.

## Tool calling and bounded execution

Each agent loop sends its current context and exact allow-list to Ollama. Requested calls are validated, executed, converted to controlled `ToolResult` observations, and returned to that same agent. Unknown tools, invalid arguments, and operational failures cannot invoke arbitrary Java code. Limits are independent: `agent.supervisor-max-iterations`, `agent.knowledge-max-iterations`, and `agent.runtime-max-iterations`.

## Persistent memory and conversation history

`MemoryExtractorService` performs a structured LLM call before orchestration. Accepted application-wide facts are upserted in PostgreSQL and supplied only to the Supervisor. `ConversationService` stores recent user messages and final answers in memory by conversation ID. Specialist prompts, delegations, retrieved chunks, and runtime observations are not persisted in conversation history.

## LLM calls per request

Every request uses one memory-extraction LLM call and at least one Supervisor call. Each delegation starts an independent specialist model loop, normally requiring a tool-selection call and a result-interpretation call. The Supervisor then makes another call to synthesize specialist observations. Combined investigations therefore use more calls than single-domain or direct-answer requests. Embedding and Qdrant requests are not chat LLM calls.

## Tracing

Tracing records observable behavior only. `traceId`, `spanId`, and `parentSpanId` reconstruct arbitrary nesting such as:

```text
Agent run
  +-- memory extraction / lookup
  +-- Supervisor Agent
      +-- Supervisor iteration / LLM decision
      +-- ask_knowledge_agent
      |   +-- Knowledge Agent
      |       +-- agent iteration / LLM decision
      |       +-- search_knowledge_base
      |           +-- embedding
      |           +-- vector search
      +-- ask_runtime_agent
          +-- Runtime Agent
              +-- agent iteration / LLM decision
              +-- mocked runtime tool
  +-- final response
```

Agent spans expose safe metadata including agent name/type, delegating parent, allowed tool names, iteration, status, and duration. Every LLM span contains structured `input` and `output` summaries produced by one shared observability summarizer. Input summaries include purpose, agent ownership, message/history/memory counts, tool names, the bounded current request, and bounded tool or agent observations. Output summaries distinguish delegation, tool calls, final responses, structured results, plain-text fallback, and errors; they include sanitized arguments or a bounded answer preview when useful.

Raw system prompts, assembled contexts, complete history, memory values, full retrieved chunks, embeddings, hidden reasoning, credentials, authorization data, and raw external responses remain excluded. A centralized recursive sanitizer redacts secret-like keys (including passwords, tokens, API/access/private keys, credentials, authorization, and cookies), bounds collections and nesting, and truncates text with an explicit `... [truncated]` marker according to `trace.llm-preview-max-chars`. The same structured summaries feed both logs and traces, preventing policy drift.

At `INFO`, backend logs record concise semantic lifecycle events: trace ID, agent, purpose, iteration, counts, output type, selected tools, duration, and error category. At `DEBUG`, logs include the same sanitized bounded input/output structures available in the trace. Request and conversation IDs remain available through the logging context; trace IDs are included directly in LLM event messages. Completed traces are retained in the bounded in-memory `TraceStore` and loaded by `GET /api/traces/{traceId}`.

## UI architecture overview

The React UI includes two related views. “View execution” renders the generic parent-child trace tree at arbitrary depth and visually distinguishes the Supervisor, specialist agents, LLM calls, domain tools, RAG internals, and final response. The interactive architecture dialog shows the Supervisor above sibling Knowledge and Runtime agents, their allowed tools, data dependencies, supporting memory/history, Ollama calls, and trace storage. Clicking any node explains its responsibility and capability boundary; the request-flow walkthrough highlights delegation and data flow.

## Package responsibilities

| Package | Responsibility |
| --- | --- |
| `agent` | Agent definitions, types, reusable runtime, Supervisor, and specialists |
| `client` | Ollama chat, embedding, and vector-store HTTP clients |
| `config` | Typed configuration and independent agent limits |
| `controller` | REST validation and transport orchestration |
| `entity`, `repository` | PostgreSQL persistent memory |
| `model`, `request`, `response` | Internal and API data contracts |
| `rag` | Runbook/project indexing, chunking, embedding, and retrieval |
| `service` | Request coordination, mock deployment data, memory, history, and traces |
| `tool` | Tool contracts, scoped allow-list validation, RAG, and runtime tools |
| `trace` | Sanitized hierarchical execution observability |

## Configuration

| Property | Purpose |
| --- | --- |
| `llm.base-url`, `llm.model` | Ollama chat endpoint and model for all agents |
| `agent.supervisor-max-iterations` | Maximum Supervisor decisions/delegations (default 4) |
| `agent.knowledge-max-iterations` | Maximum Knowledge Agent decisions (default 3) |
| `agent.runtime-max-iterations` | Maximum Runtime Agent decisions (default 3) |
| `assistant.history-limit` | Recent conversation messages supplied to the Supervisor |
| `embedding.*`, `qdrant.*` | RAG embedding and vector-search configuration |
| `project-knowledge.*` | Project indexing roots and chunk size |
| `spring.datasource.*` | PostgreSQL persistent-memory connection |
| `trace.max-entries` | Maximum retained in-memory traces |
| `trace.llm-preview-max-chars` | Maximum characters in any sanitized LLM text preview (default 500) |

## Known limitations

- Routing and tool selection are probabilistic and depend on the configured local model.
- Tool-enabled Ollama calls omit structured `format`; malformed final JSON falls back to medium-confidence plain text.
- Runtime results are deterministic mocks, not live service health.
- Persistent memory is application-wide rather than user-scoped.
- Conversation history and traces are in memory and disappear on restart; traces may also be evicted.
- RAG has no minimum similarity threshold, and large retrievals consume model context.
- Specialist execution is sequential when a Supervisor response requests both delegations.
