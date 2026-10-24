package com.example.itline.service;

/**
 * Доменное исключение: попытка изменить/удалить запись,
 * версия которой уже устарела.
 *
 * Специально не наследуем от OptimisticLockingFailureException:
 * его может бросить Hibernate в разных местах, а нам нужен один
 * контролируемый путь -> 409 в контроллере.
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}