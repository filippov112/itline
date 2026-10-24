package com.example.itline.web;

import com.example.itline.service.ConflictException;
import com.example.itline.service.NotFoundException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Единая точка превращения исключений в HTTP-статусы.
 *
 * Ключевые кейсы для ТЗ:
 *  - ConflictException / OptimisticLock* -> 409 (не 500!).
 *    Клиент по 409 делает reload() и остаётся консистентным с БД.
 *  - NotFoundException -> 404.
 *  - Ошибки валидации -> 400 с перечнем полей.
 *  - Любое прочее -> 500, но таких путей мы стараемся не оставлять.
 *
 * OptimisticLockingFailureException может прилететь как из Hibernate (flush), 
 * так и обёрнутым в Spring Data — ловим оба типа.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ConflictException.class,
                       OptimisticLockingFailureException.class,
                       OptimisticLockException.class})
    public ResponseEntity<Map<String, Object>> onConflict(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "conflict",
                "message", "Запись уже изменена или удалена в другой сессии"
        ));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> onNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "not_found",
                "message", e.getMessage()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onValidation(MethodArgumentNotValidException e) {
        // Отдаём карту "поле -> сообщение", чтобы клиент мог показать точную причину.
        var errors = e.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a
                ));
        return ResponseEntity.badRequest().body(Map.of(
                "error", "validation",
                "fields", errors
        ));
    }
}