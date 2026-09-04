# Project instructions

## Scope and structure

- This repository contains the Java 21 / Spring Boot backend. The React frontend is the sibling repository `../ai-assistant-ui`.
- Read the nearest `AGENTS.md`, relevant source, and maintained documentation before changing code. Treat `src/main/resources/docs/system-overview.md` as an architecture guide, then verify behavior in source because the implementation is authoritative.
- Keep changes scoped to the request. Preserve existing package, API, configuration, and architectural boundaries; extend an existing abstraction before creating a parallel path.
- Preserve unrelated working-tree changes and review the final diff for accidental edits.

## Development workflow

- Use the project Git flow: feature branch -> commit -> push -> pull request -> `main`. Never make feature changes directly on `main`.
- Do not add tests unless the user explicitly requests them.
- Update JavaDoc or maintained architecture documentation when a changed contract, invariant, or non-obvious flow would otherwise become misleading. Avoid comments that only restate code.
- Never commit secrets or local credentials. Treat application configuration, prompts, memory, model payloads, tool arguments/results, and traces as potentially sensitive.
- For cross-stack work, inspect the frontend API types and consumers before changing a backend contract, and keep the two repositories' branches and diffs independently reviewable.

## Verification

- For backend changes, run the most relevant Maven check; use `./mvnw` when a wrapper exists, otherwise `mvn`. This project currently has no Maven wrapper, so the normal full check is `mvn verify`.
- Report checks that could not run and the reason. Do not hide failures by weakening checks or deleting unrelated outputs.

