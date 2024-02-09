package ru.solnyshko.common.spring.jpa.annotation.processor.util;

import com.squareup.javapoet.TypeName;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

@Setter(AccessLevel.PRIVATE)
@Accessors(chain = true)
public class FieldMetadata {
    protected TypeName fieldTypeName;
    protected String fieldName;

    protected FieldCategory fieldCategory;
    protected boolean isNullable = false;
    protected boolean isPrimitive = false;

    private FieldMetadata() {}

    public static FieldMetadata of(Element enclosedElement) {
        FieldMetadata fieldMetadata = new FieldMetadata()
                .setFieldName(enclosedElement.toString())
                .setFieldTypeName(TypeName.get(enclosedElement.asType()));

        if (isCollection(TypeName.get(enclosedElement.asType()))) {
            return fieldMetadata.setFieldCategory(FieldCategory.COLLECTION);
        }

        if (isEnumeratedStringField(enclosedElement)){
            return fieldMetadata.setFieldCategory(FieldCategory.ENUM);
        }

        if (isPrimaryIdField(enclosedElement)) {
            return fieldMetadata.setFieldCategory(FieldCategory.PRIMARY_ID);
        }

        switch (enclosedElement.asType().toString()) {
            case "java.lang.String" -> fieldMetadata
                    .setFieldCategory(FieldCategory.STRING)
                    .setNullable(true);

            case "java.lang.Character" -> fieldMetadata
                    .setFieldCategory(FieldCategory.CHARACTER)
                    .setNullable(true);

            case "char" -> fieldMetadata
                    .setFieldCategory(FieldCategory.CHARACTER)
                    .setPrimitive(true);

            case "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double" -> fieldMetadata
                    .setFieldCategory(FieldCategory.NUMERIC)
                    .setNullable(true);

            case "byte", "short", "int", "long", "float", "double" -> fieldMetadata
                    .setFieldCategory(FieldCategory.NUMERIC)
                    .setPrimitive(true);

            case "java.time.LocalDate", "java.time.LocalTime", "java.time.LocalDateTime" -> fieldMetadata
                    .setFieldCategory(FieldCategory.TEMPORAL)
                    .setNullable(true);

            case "java.lang.Boolean" -> fieldMetadata
                    .setFieldCategory(FieldCategory.BOOLEAN)
                    .setNullable(true);

            case "boolean" -> fieldMetadata
                    .setFieldCategory(FieldCategory.BOOLEAN)
                    .setPrimitive(true);
        }

        if (isForeignIdField(enclosedElement)) {
            // <!> Overrides category to FOREIGN_ID preserving set metadata
            return fieldMetadata.setFieldCategory(FieldCategory.FOREIGN_ID)
                    .setNullable(!fieldMetadata.isPrimitive);
        }

        if (fieldMetadata.fieldCategory == null) {
            fieldMetadata.setFieldCategory(FieldCategory.OBJECT);
        }

        return fieldMetadata;
    }

    private static boolean isPrimaryIdField(Element enclosedElement) {
        return enclosedElement.getAnnotationMirrors().stream().anyMatch(
                mirror -> mirror.getAnnotationType().toString().equals(Id.class.getName()));
    }

    private static boolean isForeignIdField(Element enclosedElement) {
        String elementName = enclosedElement.toString();
        return elementName.endsWith("Id") || elementName.endsWith("Uuid");
    }

    private static boolean isEnumeratedStringField(Element enclosedElement) {
        return enclosedElement.getAnnotationMirrors()
                .stream()
                .filter(mirror -> mirror.getAnnotationType().toString().equals(Enumerated.class.getName()))
                .anyMatch(mirror -> getAnnotationValue(mirror).equals("STRING"));
    }

    private static boolean isCollection(TypeName elementTypeName) {
        // Other collection classes may be included from here if needed
        return elementTypeName.toString().contains("java.util")
                && (elementTypeName.toString().contains("Set<")
                || elementTypeName.toString().contains("List<")
                || elementTypeName.toString().contains("Collection<<"));
    }

    private static String getAnnotationValue(AnnotationMirror mirror) {
        return mirror.getElementValues().entrySet().stream()
                .filter(entry -> entry.getKey().toString().equals("value()"))
                .map(item -> item.getValue().getValue().toString())
                .findFirst()
                .orElseThrow();
    }
}
