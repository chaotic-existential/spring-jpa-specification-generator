package ru.solnyshko.common.spring.jpa.annotation.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.WildcardTypeName;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Generated;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@SupportedAnnotationTypes("jakarta.persistence.Entity")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
public class SimpleSpecificationGenerator extends AbstractProcessor {

    @Override
    @SneakyThrows
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);

            for (Element annotatedElement : annotatedElements) {
                if (annotatedElement.getKind() == ElementKind.CLASS) {
                    String packageName = processingEnv.getElementUtils().getPackageOf(annotatedElement).toString();
                    String className = annotatedElement.getSimpleName().toString();

                    TypeElement classElement = (TypeElement) annotatedElement;
                    generateSpecClass(packageName, className, classElement);
                }
            }
        }

        return true;
    }

    @SneakyThrows
    private void generateSpecClass(String packageName, String className, Element classElement) {
        Map<String, TypeName> idFields = new HashMap<>();
        Map<String, TypeName> enumFields = new HashMap<>();
        Map<String, TypeName> collectionFields = new HashMap<>();
        Map<String, TypeName> regularFields = new HashMap<>();

        for (Element enclosedElement : classElement.getEnclosedElements()) {

            if (enclosedElement.getKind() == ElementKind.FIELD) {
                String elementClassName = enclosedElement.toString();
                TypeName elementTypeName = TypeName.get(enclosedElement.asType());

                if (isIdField(enclosedElement)) {
                    idFields.put(elementClassName, elementTypeName);

                } else if (isEnumeratedStringField(enclosedElement)) {
                    enumFields.put(elementClassName, elementTypeName);

                } else if (isCollection(elementTypeName)) {
                    collectionFields.put(elementClassName, elementTypeName);

                } else {
                    regularFields.put(elementClassName, elementTypeName);
                }
            }
        }

        String specClassName = className + "Spec";
        String specClassPath = packageName + "." + specClassName;

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(specClassPath);
        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            String classContent = buildSpecClass(
                    packageName,
                    specClassName,
                    className,
                    idFields,
                    enumFields,
                    collectionFields,
                    regularFields
            );

            out.write(classContent);
        }
    }

    private static boolean isIdField(Element enclosedElement) {
        // May encapsulate any way of determining if a field is used for identification
        // For such fields only isNull, isEq and isIn specs will be generated
        return enclosedElement.getAnnotationMirrors().stream().anyMatch(
                mirror -> mirror.getAnnotationType().toString().equals(Id.class.getName()));
    }

    private static boolean isEnumeratedStringField(Element enclosedElement) {
        return enclosedElement.getAnnotationMirrors()
                .stream()
                .filter(mirror -> mirror.getAnnotationType().toString().equals(Enumerated.class.getName()))
                .anyMatch(mirror -> getAnnotationValue(mirror).equals("STRING"));
    }

    private static boolean isCollection(TypeName elementTypeName) {
        // Other collection classes may be included here if needed
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

    private String buildSpecClass (
            String packageName,
            String className,
            String entityName,
            Map<String, TypeName> idFields,
            Map<String, TypeName> enumFields,
            Map<String, TypeName> collectionFields,
            Map<String, TypeName> regularFields
    ) {
        AnnotationSpec utilityClassAnnotationSpec = AnnotationSpec
                .builder(UtilityClass.class)
                .build();

        AnnotationSpec generatedAnnotationSpec = AnnotationSpec
                .builder(Generated.class)
                .addMember("value", "$S", this.getClass().getName())
                .build();

        TypeSpec.Builder typeSpecBuilder = TypeSpec
                .classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(utilityClassAnnotationSpec)
                .addAnnotation(generatedAnnotationSpec);

        ClassName entityTypeName = ClassName.get(packageName, entityName);
        SpecMethodsBuilder specMethodsBuilder = new SpecMethodsBuilder(typeSpecBuilder, entityTypeName);

        idFields.forEach((fieldName, fieldTypeName) -> {
            ParameterSpec fieldParameter = ParameterSpec
                    .builder(fieldTypeName, fieldName)
                    .build();

            specMethodsBuilder.addIsNullMethods(fieldParameter);
            specMethodsBuilder.addEqualMethods(fieldParameter);
            specMethodsBuilder.addInCollectionMethods(fieldParameter);
            specMethodsBuilder.addInVarargsElementsMethods(fieldParameter);
        });

        enumFields.forEach((fieldName, fieldTypeName) -> {
            ParameterSpec fieldParameter = ParameterSpec
                    .builder(fieldTypeName, fieldName)
                    .build();

            specMethodsBuilder.addEqualMethods(fieldParameter);
            specMethodsBuilder.addInCollectionMethods(fieldParameter);
            specMethodsBuilder.addInVarargsElementsMethods(fieldParameter);
        });

        collectionFields.forEach((fieldName, fieldTypeName) -> {
            ParameterSpec fieldParameter = ParameterSpec
                    .builder(fieldTypeName, fieldName)
                    .build();

            specMethodsBuilder.addIsEmptyMethods(fieldParameter);
            specMethodsBuilder.addIsMemberMethods(fieldParameter);
            specMethodsBuilder.addJoinMethods(fieldParameter);
            specMethodsBuilder.addFetchMethods(fieldParameter);
        });

        regularFields.forEach((fieldName, fieldTypeName) -> {
            ParameterSpec fieldParameter = ParameterSpec
                    .builder(fieldTypeName, fieldName)
                    .build();

            switch (fieldTypeName.toString()) {
                case "java.lang.String" -> {
                    specMethodsBuilder.addIsNullMethods(fieldParameter);
                    specMethodsBuilder.addEqualMethods(fieldParameter);
                    specMethodsBuilder.addInCollectionMethods(fieldParameter);
                    specMethodsBuilder.addInVarargsElementsMethods(fieldParameter);
                    specMethodsBuilder.addLikeMethods(fieldParameter);
                    specMethodsBuilder.addStartsWithMethods(fieldParameter);
                    specMethodsBuilder.addEndsWithMethods(fieldParameter);
                }

                case "char" -> {
                    specMethodsBuilder.addEqualMethods(fieldParameter);
                    specMethodsBuilder.addInVarargsElementsMethods(fieldParameter);
                }

                case "java.lang.Character" -> {
                    specMethodsBuilder.addEqualMethods(fieldParameter);
                    specMethodsBuilder.addInCollectionMethods(fieldParameter);
                    specMethodsBuilder.addInVarargsElementsMethods(fieldParameter);
                }

                case "byte", "short", "int", "long", "float", "double" -> {
                    specMethodsBuilder.addEqualMethods(fieldParameter);
                    specMethodsBuilder.addInVarargsElementsMethods(fieldParameter);
                    specMethodsBuilder.addGreaterThanMethods(fieldParameter, false);
                    specMethodsBuilder.addGreaterThanOrEqualToMethods(fieldParameter, false);
                    specMethodsBuilder.addLessThanMethods(fieldParameter, false);
                    specMethodsBuilder.addLessThanOrEqualToMethods(fieldParameter, false);
                    specMethodsBuilder.addBetweenMethods(fieldParameter);
                }

                case "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double" -> {
                    specMethodsBuilder.addIsNullMethods(fieldParameter);
                    specMethodsBuilder.addEqualMethods(fieldParameter);
                    specMethodsBuilder.addInCollectionMethods(fieldParameter);
                    specMethodsBuilder.addInVarargsElementsMethods(fieldParameter);
                    specMethodsBuilder.addGreaterThanMethods(fieldParameter, false);
                    specMethodsBuilder.addGreaterThanOrEqualToMethods(fieldParameter, false);
                    specMethodsBuilder.addLessThanMethods(fieldParameter, false);
                    specMethodsBuilder.addLessThanOrEqualToMethods(fieldParameter, false);
                    specMethodsBuilder.addBetweenMethods(fieldParameter);
                }

                case "java.time.LocalDate", "java.time.LocalTime", "java.time.LocalDateTime" -> {
                    specMethodsBuilder.addIsNullMethods(fieldParameter);
                    specMethodsBuilder.addEqualMethods(fieldParameter);
                    specMethodsBuilder.addGreaterThanMethods(fieldParameter, true);
                    specMethodsBuilder.addGreaterThanOrEqualToMethods(fieldParameter, true);
                    specMethodsBuilder.addLessThanMethods(fieldParameter, true);
                    specMethodsBuilder.addLessThanOrEqualToMethods(fieldParameter, true);
                    specMethodsBuilder.addBetweenMethods(fieldParameter);
                }

                case "boolean", "java.lang.Boolean" -> {
                    specMethodsBuilder.addIsTrueMethods(fieldParameter);
                }

                default -> {
                    specMethodsBuilder.addIsNullMethods(fieldParameter);
                }
            }
        });

        return JavaFile
                .builder(packageName, typeSpecBuilder.build())
                .build()
                .toString();
    }

    // SPEC METHODS BUILDER

    private static class SpecMethodsBuilder {
        private final TypeSpec.Builder typeSpecBuilder;

        private final TypeName entityTypeName;
        private final ParameterizedTypeName parameterizedSpecification;
        private final ParameterizedTypeName parameterizedFrom;

        private static final ParameterSpec criteriaBuilderParameterSpec;
        private static final ParameterSpec criteriaQueryParameterSpec;

        static {
            criteriaBuilderParameterSpec = ParameterSpec
                    .builder(CriteriaBuilder.class, "cb")
                    .build();

            ParameterizedTypeName criteriaQueryTypeName = ParameterizedTypeName.get(
                    ClassName.get(CriteriaQuery.class),
                    WildcardTypeName.subtypeOf(Object.class)
            );

            criteriaQueryParameterSpec = ParameterSpec
                    .builder(criteriaQueryTypeName, "query")
                    .build();
        }

        private SpecMethodsBuilder(TypeSpec.Builder builder, TypeName typeName) {
            typeSpecBuilder = builder;
            entityTypeName = typeName;
            parameterizedSpecification = ParameterizedTypeName.get(
                    ClassName.get(Specification.class),
                    entityTypeName
            );

            parameterizedFrom = ParameterizedTypeName.get(
                    ClassName.get(From.class),
                    WildcardTypeName.subtypeOf(Object.class),
                    entityTypeName
            );
        }

        // UTILITY

        private static ClassName getInnerClassTypeName(ParameterSpec fieldParameterSpec) {
            String collectionType = fieldParameterSpec.type.toString();
            String innerType = collectionType.substring(
                    collectionType.indexOf("<") + 1,
                    collectionType.indexOf(">")
            );

            int typeNameSeparator = innerType.lastIndexOf(".");
            return ClassName.get(
                    innerType.substring(0, typeNameSeparator),
                    innerType.substring(typeNameSeparator + 1)
            );
        }

        private static String getCapitalizedFieldName(String fieldName) {
            return fieldName.toUpperCase().charAt(0) + fieldName.substring(1);
        }

        private static String getNegatedBooleanName(String fieldName) {
            boolean isWithIsSuffix = fieldName.startsWith("is");
            boolean isWithHasSuffix = fieldName.startsWith("has");

            if (isWithIsSuffix) {
                String fieldNameWOSuffix = fieldName.substring(2);
                return "isNot" + getCapitalizedFieldName(fieldNameWOSuffix);
            }

            if (isWithHasSuffix) {
                String fieldNameWOSuffix = fieldName.substring(3);
                return "hasNo" + getCapitalizedFieldName(fieldNameWOSuffix); // NO BITCHES 😉?
            }

            return "not" + getCapitalizedFieldName(fieldName);
        }

        private static String buildSpecificationStatement(
                String predicateMethodName,
                ParameterSpec[] parameterSpecs,
                ParameterSpec... criteriaParameterSpecs
        ) {
            StringBuilder specificationStatement = new StringBuilder(
                    String.format("return (root, query, cb) -> %s(root", predicateMethodName)
            );

            for (ParameterSpec parameterSpec : criteriaParameterSpecs) {
                specificationStatement.append(", ").append(parameterSpec.name);
            }

            for (ParameterSpec parameterSpec : parameterSpecs) {
                specificationStatement.append(", ").append(parameterSpec.name);
            }

            specificationStatement.append(")");
            return specificationStatement.toString();
        }

        private static CodeBlock buildJoinStatement(String field, JoinType joinType) {
            String statementTemplate = "return root.join(\"%s\", $T.%s)";
            String rawStatement = String.format(statementTemplate, field, joinType);
            return CodeBlock.of(rawStatement, JoinType.class);
        }

        private static CodeBlock buildFetchStatement(String field, JoinType joinType) {
            String statementTemplate = "return root.fetch(\"%s\", $T.%s)";
            String rawStatement = String.format(statementTemplate, field, joinType);
            return CodeBlock.of(rawStatement, JoinType.class);
        }

        private static CodeBlock buildFetchPredicateWrapStatement(String leftFetchMethodName) {
            String fetchPredicateStatementTemplate = "%s(root); query.distinct(true); return null";
            String formattedStatement = String.format(fetchPredicateStatementTemplate, leftFetchMethodName);
            return CodeBlock.of(formattedStatement);
        }

        private static boolean isArrayType(TypeName typeName) {
            return typeName.toString().contains("[]");
        }

        // METHODS

        private void addIsNullMethods(ParameterSpec fieldParameterSpec) {
            String genericMethodName = fieldParameterSpec.name + "IsNull";
            String predicateStatement = String.format("" +
                            "return cb.isNull(root.get(\"%s\"))",
                    fieldParameterSpec.name
            );

            addPredicateWithSpecificationFilterMethods(
                    genericMethodName,
                    predicateStatement
            );

            String genericNegatedMethodName = fieldParameterSpec.name + "IsNotNull";
            String predicateNegatedStatement = predicateStatement + ".not()";

            addPredicateWithSpecificationFilterMethods(
                    genericNegatedMethodName,
                    predicateNegatedStatement
            );
        }

        private void addEqualMethods(ParameterSpec fieldParameterSpec) {
            String genericMethodName = fieldParameterSpec.name + "Eq";
            String predicateStatement = String.format("" +
                            "return cb.equal(root.get(\"%s\"), %s)",
                    fieldParameterSpec.name,
                    fieldParameterSpec.name
            );

            addPredicateWithSpecificationFilterMethods(
                    genericMethodName,
                    predicateStatement,
                    fieldParameterSpec
            );

            String genericNegatedMethodName = fieldParameterSpec.name + "NotEq";
            String predicateNegatedStatement = predicateStatement + ".not()";

            addPredicateWithSpecificationFilterMethods(
                    genericNegatedMethodName,
                    predicateNegatedStatement,
                    fieldParameterSpec
            );
        }

        private void addInVarargsElementsMethods(ParameterSpec fieldParameterSpec) {
            String genericMethodName = fieldParameterSpec.name + "In";
            TypeName arrayTypeName = ArrayTypeName.of(fieldParameterSpec.type);

            ParameterSpec collectionParameterSpec = ParameterSpec
                    .builder(arrayTypeName, "elements")
                    .build();

            String predicateStatement = String.format("" +
                            "return root.get(\"%s\").in((Object)%s)",
                    fieldParameterSpec.name,
                    collectionParameterSpec.name
            );

            addPredicateWithSpecificationFilterMethods(
                    genericMethodName,
                    predicateStatement,
                    collectionParameterSpec
            );

            String genericNegatedMethodName = fieldParameterSpec.name + "NotIn";
            String predicateNegatedStatement = predicateStatement + ".not()";

            addPredicateWithSpecificationFilterMethods(
                    genericNegatedMethodName,
                    predicateNegatedStatement,
                    collectionParameterSpec
            );
        }

        private void addInCollectionMethods(ParameterSpec fieldParameterSpec) {
            String genericMethodName = fieldParameterSpec.name + "In";

            ParameterizedTypeName collectionTypeName = ParameterizedTypeName.get(
                    ClassName.get(Collection.class),
                    fieldParameterSpec.type
            );

            ParameterSpec collectionParameterSpec = ParameterSpec
                    .builder(collectionTypeName, "collection")
                    .build();

            String predicateStatement = String.format("" +
                            "return root.get(\"%s\").in(%s)",
                    fieldParameterSpec.name,
                    collectionParameterSpec.name
            );

            addPredicateWithSpecificationFilterMethods(
                    genericMethodName,
                    predicateStatement,
                    collectionParameterSpec
            );

            String genericNegatedMethodName = fieldParameterSpec.name + "NotIn";
            String predicateNegatedStatement = predicateStatement + ".not()";

            addPredicateWithSpecificationFilterMethods(
                    genericNegatedMethodName,
                    predicateNegatedStatement,
                    collectionParameterSpec
            );
        }

        private void addLikeMethods(ParameterSpec fieldParameterSpec) {
            String genericMethodName = fieldParameterSpec.name + "Like";
            String predicateStatement = String.format("" +
                            "return cb.like(cb.lower(cb.trim(root.get(\"%s\"))), " +
                            "\"%%\" + %s.toLowerCase().trim() + \"%%\")",
                    fieldParameterSpec.name,
                    fieldParameterSpec.name
            );

            addPredicateWithSpecificationFilterMethods(
                    genericMethodName,
                    predicateStatement,
                    fieldParameterSpec
            );

            String genericNegatedMethodName = fieldParameterSpec.name + "NotLike";
            String predicateNegatedStatement = predicateStatement + ".not()";

            addPredicateWithSpecificationFilterMethods(
                    genericNegatedMethodName,
                    predicateNegatedStatement,
                    fieldParameterSpec
            );
        }

        private void addEndsWithMethods(ParameterSpec fieldParameterSpec) {
            String genericMethodName = fieldParameterSpec.name + "EndsWith";
            String predicateStatement = String.format("" +
                            "return cb.like(cb.lower(cb.trim(root.get(\"%s\"))), " +
                            "\"%%\" + %s.toLowerCase().trim())",
                    fieldParameterSpec.name,
                    fieldParameterSpec.name
            );

            addPredicateWithSpecificationFilterMethods(
                    genericMethodName,
                    predicateStatement,
                    fieldParameterSpec
            );
        }

        private void addStartsWithMethods(ParameterSpec fieldParameterSpec) {
            String genericMethodName = fieldParameterSpec.name + "StartsWith";
            String predicateStatement = String.format("" +
                            "return cb.like(cb.lower(cb.trim(root.get(\"%s\"))), " +
                            "%s.toLowerCase().trim() + \"%%\")",
                    fieldParameterSpec.name,
                    fieldParameterSpec.name
            );

            addPredicateWithSpecificationFilterMethods(
                    genericMethodName,
                    predicateStatement,
                    fieldParameterSpec
            );
        }

        private void addGreaterThanMethods(ParameterSpec fieldParameterSpec, boolean isTemporalField) {
            String genericMethodName = isTemporalField
                    ? fieldParameterSpec.name + "After"
                    : fieldParameterSpec.name + "GreaterThan";

            String predicateStatement = String.format("" +
                            "return cb.greaterThan(root.get(\"%s\"), %s)",
                    fieldParameterSpec.name,
                    fieldParameterSpec.name
            );

            addPredicateWithSpecificationFilterMethods(
                    genericMethodName,
                    predicateStatement,
                    fieldParameterSpec
            );
        }

        private void addGreaterThanOrEqualToMethods(ParameterSpec fieldParameterSpec, boolean isTemporalField) {
            String genericMethodName = isTemporalField
                    ? fieldParameterSpec.name + "AfterOrAt"
                    : fieldParameterSpec.name + "GreaterThanOrEqualTo";

            String predicateStatement = String.format("" +
                            "return cb.greaterThanOrEqualTo(root.get(\"%s\"), %s)",
                    fieldParameterSpec.name,
                    fieldParameterSpec.name
            );

            addPredicateWithSpecificationFilterMethods(
                    genericMethodName,
                    predicateStatement,
                    fieldParameterSpec
            );
        }

        private void addLessThanMethods(ParameterSpec fieldParameterSpec, boolean isTemporalField) {
            String genericMethodName = isTemporalField
                    ? fieldParameterSpec.name + "Before"
                    : fieldParameterSpec.name + "LessThan";

            String predicateStatement = String.format("" +
                            "return cb.lessThan(root.get(\"%s\"), %s)",
                    fieldParameterSpec.name,
                    fieldParameterSpec.name
            );

            addPredicateWithSpecificationFilterMethods(
                    genericMethodName,
                    predicateStatement,
                    fieldParameterSpec
            );
        }

        private void addLessThanOrEqualToMethods(ParameterSpec fieldParameterSpec, boolean isTemporalField) {
            String genericMethodName = isTemporalField
                    ? fieldParameterSpec.name + "BeforeOrAt"
                    : fieldParameterSpec.name + "LessThanOrEqualTo";

            String predicateStatement = String.format("" +
                            "return cb.lessThanOrEqualTo(root.get(\"%s\"), %s)",
                    fieldParameterSpec.name,
                    fieldParameterSpec.name
            );

            addPredicateWithSpecificationFilterMethods(
                    genericMethodName,
                    predicateStatement,
                    fieldParameterSpec
            );
        }

        private void addBetweenMethods(ParameterSpec fieldParameterSpec) {
            String genericMethodName = fieldParameterSpec.name + "Between";
            String fromParameterName = fieldParameterSpec.name + "From";
            String toParameterName = fieldParameterSpec.name + "To";

            ParameterSpec fromParameterSpec = ParameterSpec
                    .builder(fieldParameterSpec.type, fromParameterName)
                    .build();

            ParameterSpec toParameterSpec = ParameterSpec
                    .builder(fieldParameterSpec.type, toParameterName)
                    .build();

            String predicateStatement = String.format("" +
                            "return cb.between(root.get(\"%s\"), %s, %s)",
                    fieldParameterSpec.name,
                    fromParameterSpec.name,
                    toParameterSpec.name
            );

            addPredicateWithSpecificationFilterMethods(
                    genericMethodName,
                    predicateStatement,
                    fromParameterSpec,
                    toParameterSpec
            );

            String genericNegatedMethodName = fieldParameterSpec.name + "NotBetween";
            String predicateNegatedStatement = predicateStatement + ".not()";

            addPredicateWithSpecificationFilterMethods(
                    genericNegatedMethodName,
                    predicateNegatedStatement,
                    fromParameterSpec,
                    toParameterSpec
            );
        }

        private void addIsTrueMethods(ParameterSpec fieldParameterSpec) {
            String genericMethodName = fieldParameterSpec.name;

            String predicateStatement = String.format("" +
                            "return cb.isTrue(root.get(\"%s\"))",
                    fieldParameterSpec.name
            );

            addPredicateWithSpecificationFilterMethods(
                    genericMethodName,
                    predicateStatement
            );

            String genericNegatedMethodName = getNegatedBooleanName(fieldParameterSpec.name);
            String predicateNegatedStatement = predicateStatement + ".not()";

            addPredicateWithSpecificationFilterMethods(
                    genericNegatedMethodName,
                    predicateNegatedStatement
            );
        }

        private void addIsEmptyMethods(ParameterSpec fieldParameterSpec) {
            String genericMethodName = fieldParameterSpec.name + "IsEmpty";
            String predicateStatement = String.format("" +
                            "return cb.isEmpty(root.get(\"%s\"))",
                    fieldParameterSpec.name
            );

            addPredicateWithSpecificationFilterMethods(
                    genericMethodName,
                    predicateStatement
            );

            String genericNegatedMethodName = fieldParameterSpec.name + "IsNotEmpty";
            String predicateNegatedStatement = predicateStatement + ".not()";

            addPredicateWithSpecificationFilterMethods(
                    genericNegatedMethodName,
                    predicateNegatedStatement
            );
        }

        private void addIsMemberMethods(ParameterSpec fieldParameterSpec) {
            String capitalizedFieldName = getCapitalizedFieldName(fieldParameterSpec.name);
            String genericMethodName = "is" + capitalizedFieldName + "Member";

            ClassName innerTypeName = getInnerClassTypeName(fieldParameterSpec);
            ParameterSpec elementParameterSpec = ParameterSpec
                    .builder(innerTypeName, "element")
                    .build();

            String predicateStatement = String.format("" +
                            "return cb.isMember(%s, root.get(\"%s\"))",
                    elementParameterSpec.name,
                    fieldParameterSpec.name
            );

            addPredicateWithSpecificationFilterMethods(
                    genericMethodName,
                    predicateStatement,
                    elementParameterSpec
            );

            String genericNegatedMethodName = "isNot" + capitalizedFieldName + "Member";
            String predicateNegatedStatement = predicateStatement + ".not()";

            addPredicateWithSpecificationFilterMethods(
                    genericNegatedMethodName,
                    predicateNegatedStatement,
                    elementParameterSpec
            );
        }

        private void addJoinMethods(ParameterSpec fieldParameterSpec) {
            String capitalizedFieldName = getCapitalizedFieldName(fieldParameterSpec.name);
            ClassName innerClassTypeName = getInnerClassTypeName(fieldParameterSpec);

            String leftJoinMethodName = "leftJoin" + capitalizedFieldName;
            CodeBlock leftJoinStatement = buildJoinStatement(
                    fieldParameterSpec.name,
                    JoinType.LEFT
            );

            addJoinMethod(
                    leftJoinMethodName,
                    leftJoinStatement,
                    innerClassTypeName
            );

            String innerJoinMethodName = "innerJoin" + capitalizedFieldName;
            CodeBlock innerJoinStatement = buildJoinStatement(
                    fieldParameterSpec.name,
                    JoinType.INNER
            );

            addJoinMethod(
                    innerJoinMethodName,
                    innerJoinStatement,
                    innerClassTypeName
            );

            String rightJoinMethodName = "rightJoin" + capitalizedFieldName;
            CodeBlock rightJoinStatement = buildJoinStatement(
                    fieldParameterSpec.name,
                    JoinType.RIGHT
            );

            addJoinMethod(
                    rightJoinMethodName,
                    rightJoinStatement,
                    innerClassTypeName
            );
        }

        private void addFetchMethods(ParameterSpec fieldParameterSpec) {
            String capitalizedFieldName = getCapitalizedFieldName(fieldParameterSpec.name);
            ClassName innerClassTypeName = getInnerClassTypeName(fieldParameterSpec);

            String leftFetchMethodName = "leftFetch" + capitalizedFieldName;
            CodeBlock leftFetchStatement = buildFetchStatement(
                    fieldParameterSpec.name,
                    JoinType.LEFT
            );

            addFetchMethod(
                    leftFetchMethodName,
                    leftFetchStatement,
                    innerClassTypeName
            );

            CodeBlock leftFetchPredicateWrapStatement = buildFetchPredicateWrapStatement(
                    leftFetchMethodName
            );

            addPredicateWithSpecificationQueryMethods(
                    leftFetchMethodName,
                    leftFetchPredicateWrapStatement
            );

            String innerFetchMethodName = "innerFetch" + capitalizedFieldName;
            CodeBlock innerFetchStatement = buildFetchStatement(
                    fieldParameterSpec.name,
                    JoinType.INNER
            );

            addFetchMethod(
                    innerFetchMethodName,
                    innerFetchStatement,
                    innerClassTypeName
            );

            CodeBlock innerFetchPredicateWrapStatement = buildFetchPredicateWrapStatement(
                    innerFetchMethodName
            );

            addPredicateWithSpecificationQueryMethods(
                    innerFetchMethodName,
                    innerFetchPredicateWrapStatement
            );

            String rightFetchMethodName = "rightFetch" + capitalizedFieldName;
            CodeBlock rightFetchStatement = buildFetchStatement(
                    fieldParameterSpec.name,
                    JoinType.RIGHT
            );

            addFetchMethod(
                    rightFetchMethodName,
                    rightFetchStatement,
                    innerClassTypeName
            );

            CodeBlock rightFetchPredicateWrapStatement = buildFetchPredicateWrapStatement(
                    rightFetchMethodName
            );

            addPredicateWithSpecificationQueryMethods(
                    rightFetchMethodName,
                    rightFetchPredicateWrapStatement
            );
        }

        // ADD PREDICATE & SPECIFICATION PAIR TO SPEC BUILDER

        private void addPredicateWithSpecificationFilterMethods(
                String genericMethodName,
                String predicateStatement,
                ParameterSpec... parameterSpecs
        ) {
            addPredicateWithSpecificationToTypeSpec(
                    genericMethodName,
                    CodeBlock.of(predicateStatement),
                    parameterSpecs,
                    criteriaBuilderParameterSpec
            );
        }

        private void addPredicateWithSpecificationQueryMethods(
                String genericMethodName,
                CodeBlock predicateStatement,
                ParameterSpec... parameterSpecs
        ) {
            addPredicateWithSpecificationToTypeSpec(
                    genericMethodName,
                    predicateStatement,
                    parameterSpecs,
                    criteriaQueryParameterSpec
            );
        }

        private void addPredicateWithSpecificationToTypeSpec(
                String genericMethodName,
                CodeBlock predicateStatement,
                ParameterSpec[] parameterSpecs,
                ParameterSpec... criteriaParameterSpecs
        ) {
            addPredicateMethod(
                    genericMethodName,
                    predicateStatement,
                    parameterSpecs,
                    criteriaParameterSpecs
            );

            String specificationStatement = buildSpecificationStatement(
                    genericMethodName,
                    parameterSpecs,
                    criteriaParameterSpecs
            );

            addSpecificationMethod(
                    genericMethodName,
                    CodeBlock.of(specificationStatement),
                    parameterSpecs
            );
        }

        // ADD SEPARATE METHODS TO SPEC BUILDER

        private void addPredicateMethod(
                String methodName,
                CodeBlock statement,
                ParameterSpec[] parameterSpecs,
                ParameterSpec[] criteriaParameterSpecs
        ) {
            MethodSpec methodSpec = buildPredicateMethod(
                    methodName,
                    statement,
                    parameterSpecs,
                    criteriaParameterSpecs
            );

            typeSpecBuilder.addMethod(methodSpec);
        }

        private void addSpecificationMethod(
                String methodName,
                CodeBlock statement,
                ParameterSpec[] parameterSpecs
        ) {
            MethodSpec methodSpec = buildSpecificationMethod(
                    methodName,
                    statement,
                    parameterSpecs
            );

            typeSpecBuilder.addMethod(methodSpec);
        }

        private void addJoinMethod(
                String methodName,
                CodeBlock statement,
                TypeName joinEntityTypeName
        ) {
            MethodSpec methodSpec = buildJoinMethod(
                    methodName,
                    statement,
                    joinEntityTypeName
            );

            typeSpecBuilder.addMethod(methodSpec);
        }

        private void addFetchMethod(
                String methodName,
                CodeBlock statement,
                TypeName joinEntityTypeName
        ) {
            MethodSpec methodSpec = buildFetchMethod(
                    methodName,
                    statement,
                    joinEntityTypeName
            );

            typeSpecBuilder.addMethod(methodSpec);
        }

        // BUILD METHOD SPECS

        private MethodSpec buildPredicateMethod(
                String methodName,
                CodeBlock statement,
                ParameterSpec[] parameterSpecs,
                ParameterSpec... criteriaParameterSpecs
        ) {
            MethodSpec.Builder methodSpecBuilder = MethodSpec
                    .methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(Predicate.class)
                    .addParameter(parameterizedFrom, "root")
                    .addStatement(statement);

            for (ParameterSpec parameterSpec : criteriaParameterSpecs) {
                methodSpecBuilder.addParameter(parameterSpec);
            }

            for (ParameterSpec parameterSpec : parameterSpecs) {
                methodSpecBuilder.addParameter(parameterSpec);
            }

            if (parameterSpecs.length != 0) {
                ParameterSpec lastParameter = parameterSpecs[parameterSpecs.length - 1];
                methodSpecBuilder.varargs(isArrayType(lastParameter.type));
            }

            return methodSpecBuilder
                    .build();
        }

        private MethodSpec buildSpecificationMethod(
                String methodName,
                CodeBlock statement,
                ParameterSpec... parameterSpecs
        ) {
            MethodSpec.Builder methodSpecBuilder = MethodSpec
                    .methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(parameterizedSpecification)
                    .addStatement(statement);

            for (ParameterSpec parameterSpec : parameterSpecs) {
                methodSpecBuilder.addParameter(parameterSpec);
            }

            if (parameterSpecs.length != 0) {
                ParameterSpec lastParameter = parameterSpecs[parameterSpecs.length - 1];
                methodSpecBuilder.varargs(isArrayType(lastParameter.type));
            }

            return methodSpecBuilder
                    .build();
        }

        private MethodSpec buildJoinMethod(
                String methodName,
                CodeBlock statement,
                TypeName joinEntityTypeName
        ) {
            ParameterizedTypeName parametrizedJoin = ParameterizedTypeName.get(
                    ClassName.get(Join.class),
                    entityTypeName,
                    joinEntityTypeName
            );

            MethodSpec.Builder methodSpecBuilder = MethodSpec
                    .methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(parametrizedJoin)
                    .addParameter(parameterizedFrom, "root")
                    .addStatement(statement);

            return methodSpecBuilder
                    .build();
        }

        private MethodSpec buildFetchMethod(
                String methodName,
                CodeBlock statement,
                TypeName joinEntityTypeName
        ) {
            ParameterizedTypeName parametrizedFetch = ParameterizedTypeName.get(
                    ClassName.get(Fetch.class),
                    entityTypeName,
                    joinEntityTypeName
            );

            MethodSpec.Builder methodSpecBuilder = MethodSpec
                    .methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(parametrizedFetch)
                    .addParameter(parameterizedFrom, "root")
                    .addStatement(statement);

            return methodSpecBuilder
                    .build();
        }
    }
}
