---
name: observability-review
description: Review backend logging and execution tracing for agent-loop diagnosability, correlation, bounded summaries, duplication, and sensitive-data safety. Use for observability audits or review-only requests, not general agent correctness reviews.
---

# Observability review

Read `AGENTS.md`, the tracing section of `src/main/resources/docs/system-overview.md`, and the concrete logging, `observability`, and `trace` implementations. Trace at least one direct-answer path, one tool path, and an error/limit path where the source supports them.

## Required signal

Confirm a developer can reconstruct every agent-loop iteration using concise, correlated events or spans that capture:

- request, conversation, trace, span, and parent identifiers as applicable;
- agent identity, LLM purpose, iteration number, and duration;
- bounded summaries of LLM input and output;
- requested tool and safe arguments;
- bounded tool result/observation and whether it fed the next model call;
- final status and termination reason, including failures and iteration limits.

## Safety and quality

Check the centralized sanitizer and summarizer rather than sampling only log statements. Flag raw or oversized prompts, history, memory values, retrieved chunks, embeddings, external responses, credentials, authorization/cookies, secret-like keys, or exception details that can expose sensitive values. Also flag duplicated lifecycle events, inconsistent trace/log policies, missing correlation, misleading metadata, and high-volume success logs with little diagnostic value.

Report findings by severity with exact paths/lines, impact, and focused remediation. Keep correctness findings outside observability scope in a separate note or recommend `$agent-flow-review`. Do not modify code unless explicitly asked.

