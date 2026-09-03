# Missing Deployment Logs

## Symptoms

- A deployment fails but expected application logs are absent.
- Only platform scheduling or container lifecycle events are visible.

## Investigation

1. Confirm a container was created and started.
2. Inspect platform events for image-pull, scheduling, mount, or permission failures.
3. Verify the log collector is healthy and configured for the workload namespace.
4. Check whether the process exits before logging initialization.

## Recovery

Resolve the earliest platform event first. Do not infer application behavior from missing logs; report the runtime evidence that is actually available.
