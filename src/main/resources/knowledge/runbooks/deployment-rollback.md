# Deployment Rollback

## Preconditions

- A known-good release or image is available.
- Current impact justifies reverting before completing a forward fix.
- Any incompatible database migration has been assessed.

## Procedure

1. Record the failing release, observed status, and error evidence.
2. Select the most recent verified healthy release.
3. Roll back through the normal deployment mechanism.
4. Monitor readiness, error rate, and dependency health until stable.
5. Preserve incident evidence for follow-up analysis.

## Stop conditions

Stop and escalate if rollback requires destructive data changes or the previous release is incompatible with the current database schema.
