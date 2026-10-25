package com.example.itline.web.dto;

import com.example.itline.domain.Record;

/**
 * То, что уходит клиенту.
 */
public record RecordDto(
        Long id,
        boolean active,
        String icon,
        String name,
        String description,
        long version
) {
    public static RecordDto of(Record r) {
        return new RecordDto(
                r.getId(), r.isActive(), r.getIcon(),
                r.getName(), r.getDescription(), r.getVersion()
        );
    }
}