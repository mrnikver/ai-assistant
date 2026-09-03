# Database Connectivity Failure

## Symptoms

- Deployment fails during startup.
- Logs contain connection refused, timeout, authentication failure, or DNS errors.
- Health checks may report the service as unhealthy.

## Investigation

1. Verify the configured database endpoint and port.
2. Confirm the database is available from the service network.
3. Validate credentials through the approved secret source without printing them.
4. Check firewall, security-group, DNS, and routing rules.
5. Review connection-pool limits and startup timeout configuration.

## Recovery

Correct connectivity or credentials before redeploying. Avoid repeated restarts until the failing layer is identified.
