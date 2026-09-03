package com.mykyta.controller;

import com.mykyta.entity.Memory;
import com.mykyta.request.MemoryRequest;
import com.mykyta.response.MemoryResetResponse;
import com.mykyta.service.MemoryService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/memory")
@Slf4j
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @PostMapping
    public void save(@Valid @RequestBody MemoryRequest request) {
        log.info("Manual memory save requested: key={}", request.key());
        memoryService.save(request.key(), request.value());
    }

    @GetMapping
    public List<Memory> getAll() {
        List<Memory> memories = memoryService.getAll();
        log.info("Memory list returned: count={}", memories.size());
        return memories;
    }

    /** Deletes persistent PostgreSQL memory only; conversation and knowledge stores are unaffected. */
    @DeleteMapping
    public MemoryResetResponse reset() {
        log.info("Persistent memory reset request received");
        try {
            long deletedCount = memoryService.deleteAll();
            log.info("Persistent memory reset succeeded: deletedCount={}", deletedCount);
            return new MemoryResetResponse(deletedCount);
        } catch (RuntimeException exception) {
            log.error("Persistent memory reset failed", exception);
            throw exception;
        }
    }
}
