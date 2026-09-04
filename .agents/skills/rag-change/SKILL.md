---
name: rag-change
description: Modify retrieval-augmented generation, project knowledge, runbook indexing, embeddings, Qdrant, or knowledge-search behavior while preserving tool-selected retrieval. Use for RAG and knowledge-base implementation changes.
---

# RAG change

Follow `AGENTS.md`. Start with the RAG sections of `src/main/resources/docs/system-overview.md`, then trace the current implementation in `rag`, `tool`, `client`, configuration, and agent code.

## Invariants

- Retrieval remains an LLM-selected Knowledge Agent action through `search_knowledge_base`; never run RAG automatically for every request.
- Keep the Knowledge Agent isolated from runtime-state tools, and do not allow mock runtime data to become knowledge evidence.
- Reuse the existing ingestion, retrieval, tool, and client paths. Do not introduce a duplicate embedding, vector-search, or retrieval route.

## Review before changing

Map ingestion sources, classification/sanitization, chunk boundaries, stable IDs, metadata, embedding/upsert behavior, stale-point cleanup, retrieval filters, limits, ranking, and the observation returned to the agent. Check how `topK` defaults and bounds are validated and whether returned evidence is relevant, sufficiently attributed, and context-bounded.

For lifecycle changes, evaluate collection existence, dimension compatibility, re-indexing, stale data, and `rag.reset-on-startup`. Preserve the distinction between normal idempotent startup and explicit destructive local/dev reset behavior.

After implementation, verify the relevant Maven build and inspect the diff. Update maintained architecture documentation when behavior or an invariant changes.
