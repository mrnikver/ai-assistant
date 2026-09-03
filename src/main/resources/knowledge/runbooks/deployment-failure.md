# Deployment Failure

## Symptoms

- Deployment status is failed or rollout never becomes ready.
- Startup logs contain a concrete error or repeated readiness failures.

## Investigation

1. Record the deployment status and relevant log error.
2. Identify the first failing dependency rather than later retry noise.
3. Compare image, configuration, and dependency endpoints with the last successful release.
4. Confirm health-check timing is appropriate for normal startup.

## Recovery

Fix the identified dependency or configuration issue and redeploy. If impact is ongoing and a known-good release exists, follow the rollback runbook.
