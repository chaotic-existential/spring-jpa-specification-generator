package ru.solnyshko.common.spring.jpa.annotation.processor.util;

public enum FieldCategory {
    PRIMARY_ID,
    FOREIGN_ID,
    ENUM,
    COLLECTION,
    CHARACTER,
    STRING,
    NUMERIC,
    TEMPORAL,
    BOOLEAN,
    OBJECT
}
