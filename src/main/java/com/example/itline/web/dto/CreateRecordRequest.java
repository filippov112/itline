package com.example.itline.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Валидация создания.
 * - name/description непустые (ТЗ: при пустых полях окно не закрывается,
 *   но клиент валидирует сам; сервер обязан тоже отвергать 400).
 * - icon — только из трёх известных значений.
 *
 * active в JSON может отсутствовать -> @NotNull не ставим,
 * primitive boolean дефолтится в false, но клиент всегда шлёт.
 */
public record CreateRecordRequest(
        boolean active,
        @NotNull
        @Pattern(regexp = "icon[123]", message = "Допустимо: icon1, icon2, icon3")
        String icon,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 2000) String description
) {}