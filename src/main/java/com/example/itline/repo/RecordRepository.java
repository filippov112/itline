package com.example.itline.repo;

import com.example.itline.domain.Record;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Базовый CRUD
 */
public interface RecordRepository extends JpaRepository<Record, Long> {
}