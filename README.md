## 🌱 Spring JPA Specification Generator

Specification Generator uses jakarta `@Entity` annotation to spot entities <br>
and generates corresponding Spec class for every entity.

Generated classes contain `Predicate` and `Specification<T>` pairs for non-object fields, <br>
enums annotated with `@Enumerated(EnumType.STRING)` and common collections from java.util.*.

Collections also get their own `Join<Z, X>` and `Fetch<Z, X>` methods, <br>
with fetch method having `Predicate` and `Specification<T>` wrap methods.
