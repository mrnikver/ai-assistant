package com.mykyta.model;

/** Application-owned lifecycle for a validated state-changing tool request. */
public enum PendingActionStatus {
    AWAITING_CONFIRMATION,
    CONFIRMED,
    EXECUTING,
    EXECUTED,
    FAILED,
    EXPIRED,
    SUPERSEDED
}
