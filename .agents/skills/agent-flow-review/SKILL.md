---
name: agent-flow-review
description: Review the AI agent execution flow end to end for correctness, safety, bounded execution, tool handling, context feedback, memory, and trace behavior. Use for audits and review-only investigations, not ordinary feature implementation.
---

# Agent flow review

Review behavior rather than proposing a redesign. Read `AGENTS.md` and `src/main/resources/docs/system-overview.md`, then validate every relevant claim in source.

## Trace the flow

Follow the concrete call and data path, where applicable:

`HTTP request -> conversation/context loading -> persistent memory retrieval -> LLM input construction -> LLM call -> tool decision -> ToolRegistry/allow-list -> tool execution -> tool observation -> next agent-loop iteration -> final answer -> persistence -> logging/tracing`

Include Supervisor-to-specialist delegation and each specialist loop. Distinguish documented intent from implemented behavior and identify paths that skip a stage.

## Verify

- Every agent loop is bounded and has effective max-iteration protection, including error and repeated-tool paths.
- Each agent exposes only its configured tools; registry lookup enforces that allow-list and unknown tools cannot dispatch arbitrary code.
- Missing/malformed tool arguments, tool failures, and unexpected results become controlled observations or explicit failures.
- A tool observation is appended to the same agent context used by the next LLM decision, with the correct call association.
- Final answers and conversation history persist only the intended public content; persistent memory extraction, lookup, update, and reset have the documented scope.
- LLM input/output traces are useful and bounded while excluding secrets, raw prompts, full history/memory, retrieved chunks, embeddings, credentials, and oversized payloads.
- Logs/traces expose request or conversation correlation, iteration, tool choice, safe arguments/result summaries, outcome, duration, and termination reason without duplication.

## Report

List findings first, ordered `CRITICAL`, `HIGH`, `MEDIUM`, then `LOW`. For each finding include the affected path/line, concrete behavior, impact, and a focused remediation. Separate verified findings from questions or assumptions. If no findings exist, say so and note remaining coverage gaps. Do not change code unless the user separately asks for fixes.

