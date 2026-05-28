package com.echo.domain.journal;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts Java enum UPLOADING → PostgreSQL enum 'uploading'.
 * The PostgreSQL entry_status type is defined in lowercase.
 */
@Converter(autoApply = true)
public class EntryStatusConverter implements AttributeConverter<EntryStatus, String> {

    @Override
    public String convertToDatabaseColumn(EntryStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    @Override
    public EntryStatus convertToEntityAttribute(String value) {
        return value == null ? null : EntryStatus.valueOf(value.toUpperCase());
    }
}
