package com.mykyta.response;

/** Confirms a persistent-memory reset without exposing deleted values. */
public record MemoryResetResponse(long deletedCount) { }
