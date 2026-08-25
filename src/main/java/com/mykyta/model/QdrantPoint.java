package com.mykyta.model;

import java.util.Map;

public record QdrantPoint(
        String id,
        double[] vector,
        Map<String, Object> payload
) {
}