package com.mykyta.service;

import com.mykyta.entity.Memory;
import com.mykyta.model.MemoryKey;
import com.mykyta.repository.MemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class MemoryService {

    private final MemoryRepository memoryRepository;

    public MemoryService(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

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

    public List<Memory> getAll() {
        List<Memory> memories = memoryRepository.findAll();
        log.debug("Persistent memories loaded: count={}", memories.size());
        return memories;
    }
}
