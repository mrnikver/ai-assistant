# Create project-level feature change tracker

## Status

COMPLETED

## Original Request

Create a project-level Codex skill named `feature-change-tracker` that automatically maintains a permanent, concise Markdown history for every implementation request. Store one collision-safe, predictably named file per task under `docs/change-history/`, update it throughout implementation and delivery, preserve completed history, and document the request, requirements, plan, implementation, affected files, decisions, deviations, validation, result, remaining work, and Git delivery details. Also show the skill location, history directory, template, example, and automatic invocation criteria.

## Requirements

- Create a repository-local, automatically discoverable `feature-change-tracker` skill.
- Track features, fixes, refactors, architecture and infrastructure changes, UI work, tool additions, and agent/RAG/memory/guardrail changes.
- Create one `YYYY-MM-DD-short-task-name.md` record per task and avoid overwrites on naming collisions.
- Create the record early as `IN_PROGRESS`, update the same record during iterations, and finish as `COMPLETED` or `PARTIAL` according to actual completion.
- Use the requested sections and record only factual implementation and validation details.
- Preserve completed historical records and create a linked new record for later changes.
- Follow the repository's branch-to-PR delivery flow without committing directly to `main`.

## Implementation Plan

Create the skill under `.agents/skills/feature-change-tracker/`, add a reusable task template, add this file as the first example record, validate the skill structure and repository diff, then commit, push, and open a PR.

## Implementation

Added a project-local Codex skill with explicit automatic invocation boundaries, a lifecycle for creating and updating one durable record per implementation task, collision-safe naming rules, preservation rules for completed history, and factual-content safeguards. Added UI metadata that permits implicit invocation and a reusable reference template. This task's record is the first concrete example.

## Files Changed

- `.agents/skills/feature-change-tracker/SKILL.md` — Defines automatic routing, lifecycle, naming, and preservation rules.
- `.agents/skills/feature-change-tracker/agents/openai.yaml` — Provides UI metadata and implicit invocation policy.
- `.agents/skills/feature-change-tracker/references/task-template.md` — Supplies the reusable task-record structure.
- `docs/change-history/2026-09-04-create-feature-change-tracker.md` — Records this task and serves as the first example.

## Design Decisions

- The skill is project-local so its behavior travels with this repository.
- Review-only, research, explanation, and status requests are excluded unless they become authorized implementation work, preventing noisy history entries.
- The detailed template is a reference file so the main skill remains concise while preserving a consistent record format.

## Deviations

None.

## Validation

- Parsed the `SKILL.md` frontmatter and `agents/openai.yaml` successfully with Ruby's YAML parser.
- Inspected the skill, template, example record, repository status, and scoped diff for placeholders, required headings, invocation policy, and unrelated changes.
- The bundled `quick_validate.py` could not run because PyYAML is not installed in the available Python environment (`ModuleNotFoundError: No module named 'yaml'`).
- Maven was not run because this documentation-only change does not affect Java code or the application build.

## Result

Codex can now automatically recognize implementation requests in this project, create a durable task record under `docs/change-history/`, and keep that record aligned with the work through implementation and delivery.

## Remaining Work

None.

## Git / Delivery

- Branch: `docs/feature-change-tracker`
- Related commit: `e26090b` (`Add feature change tracking skill`)
- Pull request: [#28](https://github.com/mrnikver/ai-assistant/pull/28)
- Merge status: Merged into `main` as `051b7d31fd2dcde24f62c446030a611ab59b2430` on 2026-09-04.
