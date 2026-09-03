package com.mykyta.service;

import com.mykyta.entity.Memory;
import com.mykyta.model.MemoryKey;
import com.mykyta.repository.MemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.mykyta.trace.AgentTracer;
import com.mykyta.trace.TraceScope;
import com.mykyta.trace.TraceSpanType;

import java.util.List;
import java.util.Optional;

/** Owns persistent application memory and records actual reads in active execution traces. */
@Service
@Slf4j
public class MemoryService {

    private final MemoryRepository memoryRepository;
    private final AgentTracer agentTracer;

    /**
     * Creates the memory service.
     * @param memoryRepository durable memory persistence
     * @param agentTracer request trace collector
     */
    public MemoryService(MemoryRepository memoryRepository, AgentTracer agentTracer) {
        this.memoryRepository = memoryRepository;
        this.agentTracer = agentTracer;
    }

    /**
     * Creates or updates one supported durable memory fact.
     * @param key allow-listed memory category
     * @param value current value to persist
     */
    public void save(MemoryKey key, String value) {
        String dbKey = key.name();
        Optional<Memory> existingMemory = memoryRepository.findByKey(dbKey);

        Memory memory = existingMemory
                .map(existing -> {
                    existing.updateValue(value);
                    return existing;
                })
                .orElseGet(() -> new Memory(dbKey, value));

        memoryRepository.save(memory);
        log.info("Memory persisted: key={}, operation={}", key, existingMemory.isPresent() ? "update" : "create");
    }

    /**
     * Loads current persistent memories without placing their values in trace metadata.
     * @return all stored memory records
     */
    public List<Memory> getAll() {
        try (TraceScope span = agentTracer.startSpan(TraceSpanType.MEMORY_LOOKUP, "Load persistent memory")) {
            try {
                List<Memory> memories = memoryRepository.findAll();
                span.metadata("resultCount", memories.size());
                log.debug("Persistent memories loaded: count={}", memories.size());
                return memories;
            } catch (RuntimeException exception) {
                span.fail(exception);
                throw exception;
            }
        }
    }
}
