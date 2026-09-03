# Service Unhealthy

## Symptoms

- Readiness or liveness checks fail.
- The deployment is running but does not receive traffic.
- Error rate or dependency failures increase after rollout.

## Investigation

1. Check deployment status and recent service logs.
2. Separate application startup failures from health-check configuration failures.
3. Verify required databases, queues, and downstream services are reachable.
4. Compare resource usage and configuration with the last healthy release.

## Recovery

Restore the failed dependency or configuration. Roll back when the new release is the likely cause and remediation cannot be completed safely in place.
