package com.mykyta.trace;

/** Describes whether a completed trace operation succeeded or failed. */
public enum TraceStatus {
    /** Operation completed normally. */ SUCCESS,
    /** Operation failed or returned a controlled failure. */ ERROR
}
