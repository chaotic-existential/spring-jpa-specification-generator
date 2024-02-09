package ru.solnyshko.common.spring.jpa.annotation.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import ru.solnyshko.common.spring.jpa.annotation.processor.util.FieldMetadata;
import ru.solnyshko.common.spring.jpa.annotation.processor.util.TypeSpecBuilderUtil;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Generated;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SupportedAnnotationTypes("jakarta.persistence.Entity")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
public class SimpleSpecificationGenerator extends AbstractProcessor {

    @Override
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
        List<FieldMetadata> fieldsMetadata = new ArrayList<>();

        for (Element enclosedElement : classElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.FIELD) {
                fieldsMetadata.add(FieldMetadata.of(enclosedElement));
            }
        }

        String specClassName = className + "Spec";
        String specClassPath = packageName + "." + specClassName;

        JavaFileObject builderFile = processingEnv.getFiler().createSourceFile(specClassPath);
        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            JavaFile javaFile = buildJavaFile(
                    packageName,
                    specClassName,
                    className,
                    fieldsMetadata
            );

            out.write(javaFile.toString());
        }
    }

    public JavaFile buildJavaFile(
            String packageName,
            String className,
            String entityName,
            List<FieldMetadata> fieldsMetadata
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

        ClassName entityTypeName = ClassName.get(
                packageName,
                entityName
        );

        TypeSpecBuilderUtil.generateSpecMethods(
                typeSpecBuilder,
                entityTypeName,
                fieldsMetadata
        );

        return JavaFile
                .builder(packageName, typeSpecBuilder.build())
                .build();
    }
}
