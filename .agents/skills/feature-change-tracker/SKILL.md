---
name: feature-change-tracker
description: Maintain a permanent, per-task Markdown history for implementation work in this project. Use automatically for new features, bug fixes, refactors, architecture or infrastructure changes, UI changes, tool additions, and agent, RAG, memory, or guardrail changes; do not use for read-only explanations, reviews, research, or status checks unless they lead to an authorized project change.
---

# Feature Change Tracker

Create and maintain one concise engineering history record for each implementation request.

## Storage and naming

- Store records in `docs/change-history/` from the repository root.
- Name each record `YYYY-MM-DD-short-task-name.md`, using the task's start date and a short lowercase kebab-case slug.
- Before creating a record, search for an existing record for the same task and continue updating it across iterations.
- If the intended path already belongs to another task, append a short numeric suffix such as `-2`; never overwrite an unrelated record.
- Preserve completed records. If later work changes an earlier implementation, create a new record and link the earlier record when useful.

## Workflow

1. At the start of authorized implementation work, read [references/task-template.md](references/task-template.md).
2. Create the record before substantive implementation. Capture the user's request faithfully, derive only concrete requirements supported by it, outline the initial plan, and set `Status` to `IN_PROGRESS`.
3. Keep the same record current when meaningful implementation decisions, constraints, or scope changes arise. Do not create a new record merely because the task takes multiple turns or iterations.
4. Before delivery, update Implementation, Files Changed, Design Decisions, Deviations, Validation, Result, Remaining Work, and Git / Delivery with facts from the completed work.
5. Set `Status` to `COMPLETED` only when all requested work is complete. Use `PARTIAL` when the delivered result intentionally or unavoidably leaves requested requirements unfinished. Use `PLANNED` only for a plan that has not begun.
6. If delivery metadata changes later in the same delivery flow, update Git / Delivery in the existing record without rewriting its historical account.

## Recording rules

- Preserve original intent and distinguish the request, plan, and implemented result.
- Keep entries concise and useful to an engineer without access to the conversation.
- List important files and components, not generated or low-level noise.
- Do not paste full diffs, logs, secrets, credentials, prompts, model payloads, tool arguments/results, or sensitive traces.
- Do not invent requirements, decisions, outcomes, or validation. Clearly label checks that were not run and why.
- State `None.` in Deviations or Remaining Work when applicable.
- Treat the history file as part of the task: include it in review, commits, and delivery.
- Follow the repository's normal branch and pull-request workflow; tracking a change does not authorize implementation, commits, pushes, or merges beyond the user's request and project instructions.
