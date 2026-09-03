package com.mykyta.repository;

import com.mykyta.entity.Memory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemoryRepository extends JpaRepository<Memory, UUID> {

    Optional<Memory> findByKey(String key);
}
