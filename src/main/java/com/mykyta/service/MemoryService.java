package com.mykyta.service;

import com.mykyta.memory.Memory;
import com.mykyta.memory.MemoryRepository;
import com.mykyta.model.MemoryKey;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemoryService {

    private final MemoryRepository memoryRepository;

    public MemoryService(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    public void save(
            MemoryKey key,
            String value
    ) {
        String dbKey = key.name();

        Memory memory = memoryRepository.findByKey(dbKey)
                .map(existing -> {
                    existing.updateValue(value);
                    return existing;
                })
                .orElseGet(() -> new Memory(dbKey, value));

        memoryRepository.save(memory);
    }

    public List<Memory> getAll() {
        return memoryRepository.findAll();
    }
}