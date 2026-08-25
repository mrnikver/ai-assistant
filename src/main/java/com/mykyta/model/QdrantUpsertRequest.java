package com.mykyta.model;

import java.util.List;

public record QdrantUpsertRequest(
        List<QdrantPoint> points
) {
}