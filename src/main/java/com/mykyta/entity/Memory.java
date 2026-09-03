package com.mykyta.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "memories")
public class Memory {

    @Id
    private UUID id;

    @Column(name = "memory_key", nullable = false, unique = true)
    private String key;

    @Column(nullable = false)
    private String value;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Memory() {
    }

    public Memory(String key, String value) {
        this.id = UUID.randomUUID();
        this.key = key;
        this.value = value;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void updateValue(String value) {
        this.value = value;
        this.updatedAt = Instant.now();
    }
}
