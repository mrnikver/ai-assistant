package com.mykyta.model;

import java.util.Map;

public record ToolFunction(
        Integer index,
        String name,
        Map<String, Object> arguments
) {}