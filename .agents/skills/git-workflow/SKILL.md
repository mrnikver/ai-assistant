---
name: git-workflow
description: Deliver project changes through a safe branch, commit, push, pull-request, review, and main-sync workflow. Use whenever starting or publishing backend or frontend repository changes.
---

# Git workflow

Apply this workflow independently in every affected repository. Preserve unrelated working-tree and index changes throughout.

Use command-line tooling for the entire workflow. Never use Codex UI, browser automation, or web forms for Git or GitHub operations. Use the `git` CLI for repository operations and the `gh` CLI for GitHub pull-request operations. If the required CLI authentication is unavailable, report the blocker and ask the user to authenticate it; do not fall back to UI.

## Start work

1. Inspect `git status`, the current branch, remotes, and relevant repository instructions.
2. Fetch the remote. Start from an up-to-date `main`; use a fast-forward-only pull so divergent history is never rewritten implicitly.
3. Create a descriptive branch such as `feature/<topic>`, `fix/<topic>`, `docs/<topic>`, or `chore/<topic>`. The branch name must never contain `codex`.
4. Make and verify the requested change. Never implement a feature directly on `main`.

## Publish for review

1. Review the diff and status. Stage and commit only files belonging to the request, especially when unrelated changes already exist.
2. Push the feature branch and open a pull request targeting `main`. Include a concise summary and verification notes.
3. Give the user the pull-request link and stop. Do not merge, squash, rebase, or delete the branch while review is pending.

## Synchronize after review

When the user says the reviewed change was pushed or merged, first verify that remote `main` contains the pull-request change. If it does, switch to `main` and run a fast-forward-only pull, then report the synchronized commit. If it does not, keep the feature branch intact and explain that the PR has not reached `main`; never force or bypass the review workflow.
