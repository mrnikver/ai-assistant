---
name: backend-feature
description: Implement or modify Java backend functionality in AI Assist while preserving its Spring and agent architecture. Use for backend feature, behavior, API, configuration, or maintenance changes; not for review-only requests.
---

# Backend feature

Follow the repository `AGENTS.md` and keep the requested change inside the existing architecture.

## Workflow

1. Trace the current execution path from its public entry point through services, agents, tools, clients, persistence, and response contracts as applicable. Read nearby configuration and `src/main/resources/docs/system-overview.md`, but verify claims in source.
2. Identify the existing package boundary and abstraction that owns the behavior. Prefer extending it over introducing a second implementation path. Keep transport, orchestration, domain behavior, external clients, persistence, and observability responsibilities separated.
3. Make the smallest coherent change. Preserve constructor injection, typed configuration/contracts, scoped tool capabilities, and established error handling. Update JavaDoc for changed public contracts or non-obvious invariants.
4. If an API contract changes, inspect consumers in `../ai-assistant-ui` and call out any coordinated change required. Do not silently break the sibling frontend.
5. Run the relevant Maven check, normally `mvn verify`; use a narrower check only when proportionate and explain it.
6. Review `git diff` and `git status` for unrelated or generated changes before reporting the result.

Do not broaden the task into production-code cleanup.
