package com.mykyta.model;

/** Application-owned outcome of a chat request. */
public enum AssistantResponseStatus {
    ANSWER,
    CONFIRMATION_REQUIRED,
    ACTION_EXECUTED,
    ACTION_EXPIRED,
    ACTION_ALREADY_RESOLVED
}
