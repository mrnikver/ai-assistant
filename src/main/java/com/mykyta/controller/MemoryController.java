package com.mykyta.controller;

import com.mykyta.memory.Memory;
import com.mykyta.model.MemoryRequest;
import com.mykyta.service.MemoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/memory")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @PostMapping
    public void save(
            @Valid @RequestBody MemoryRequest request
    ) {
        memoryService.save(
                request.key(),
                request.value()
        );
    }

    @GetMapping
    public List<Memory> getAll() {
        return memoryService.getAll();
    }
}