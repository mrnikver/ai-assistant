package com.mykyta.controller;

import com.mykyta.entity.Memory;
import com.mykyta.request.MemoryRequest;
import com.mykyta.service.MemoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/memory")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @PostMapping
    public void save(@Valid @RequestBody MemoryRequest request) {
        memoryService.save(request.key(), request.value());
    }

    @GetMapping
    public List<Memory> getAll() {
        return memoryService.getAll();
    }
}
