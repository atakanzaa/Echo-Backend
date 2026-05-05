package com.echo.domain.capsule;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Java enum SEALED → PostgreSQL enum 'sealed' mapping.
 * Mirrors EntryStatusConverter; relies on Hibernate's varchar binding being
 * implicitly cast to the {@code capsule_status} PG enum at the JDBC layer.
 */
@Converter(autoApply = true)
public class CapsuleStatusConverter implements AttributeConverter<CapsuleStatus, String> {

    @Override
    public String convertToDatabaseColumn(CapsuleStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    @Override
    public CapsuleStatus convertToEntityAttribute(String value) {
        return value == null ? null : CapsuleStatus.valueOf(value.toUpperCase());
    }
}
