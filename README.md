## 🌱 Spring JPA Specification Generator

Specification Generator uses jakarta `@Entity` annotation to spot entities <br>
and generates corresponding Spec class for every entity.

Generated classes contain `Predicate` and `Specification<T>` pairs of filters for object fields, <br>
enums annotated with `@Enumerated(EnumType.STRING)` and common collections from java.util.*.

Collections also get their own `Join<Z, X>` and `Fetch<Z, X>` methods, <br>
with fetch method having `Predicate` and `Specification<T>` wrap methods.

## 🌱 What is generated in particular ?

In the same package where your entity belongs EntitySpec class is generated on build.<br>
Lombok `@UtilityClass` annotation is used to restict modification & usage of the class.

```java
import javax.annotation.processing.Generated;
import lombok.experimental.UtilityClass;

// Criteria API imports omitted ...

@UtilityClass
@Generated("{...}")
class EntitySpec {

    // Spec methods omitted ...
}
```

For every entity field a bunch of methods are generated depending on this field type.

```java
// <!> Note: null checks are not generated for primitives, enums & collections.

public Predicate fieldNameIsNull(From<?, T> root, CriteriaBuilder cb) {
  return cb.isNull(root.get("fieldName"));
}

public Specification<T> fieldNameIsNull() {
  return (root, query, cb) -> idIsNull(root, cb);
}

public Predicate fieldNameEq(From<?, T> root, CriteriaBuilder cb, T fieldName) {
  return cb.equal(root.get("fieldName"), fieldName);
}

public Specification<T> fieldNameEq(T fieldName) {
  return (root, query, cb) -> fieldNameEq(root, cb);
}

public Predicate fieldNameIn(From<?, T> root, CriteriaBuilder cb, Collection<T> collection) {
  return root.get("fieldName").in(collection);
}

public Specification<T> fieldNameIn(Collection<T> collection) {
  return (root, query, cb) -> fieldNameIn(root, cb);
}
```

```java
// <!> Note: in checks accept not only collections, but varargs as well.
// <!> Varargs are cast to (Object...) so that it works fine with primitives.

public Predicate fieldNameIn(From<?, T> root, CriteriaBuilder cb, T... elements) {
  return root.get("fieldName").in((Object)elements);
}

public Specification<T> fieldNameIn(T... elements) {
  return (root, query, cb) -> fieldNameIn(root, cb);
}
```

```java
// <!> Method names for boolean checks depend on prefix.
// <!> IsBoolean, hasBoolean & boolean are recognized.

public Predicate isBoolean(From<?, Channel> root, CriteriaBuilder cb) {
  return cb.isTrue(root.get("isBoolean"));
}

public Specification<Channel> isBoolean() {
  return (root, query, cb) -> isBoolean(root, cb);
}
```

```java
// Collection fields filters:

public Predicate fieldNameIsEmpty(From<?, T> root, CriteriaBuilder cb) {
  return cb.isEmpty(root.get("fieldName"));
}

public Specification<T> fieldNameIsEmpty() {
  return (root, query, cb) -> fieldNameIsEmpty(root, cb);
}

public Predicate isFieldNameMember(From<?, T> root, CriteriaBuilder cb, T element) {
  return cb.isMember(element, root.get("fieldName"));
}

public Specification<T> isFieldNameMember(T element) {
  return (root, query, cb) -> isFieldNameMember(root, cb, element);
}

// Collection fields join, fetch & fetch wraps:

public Join<Z, X> leftJoinFieldName(From<?, Z> root) {
  return root.join("fieldName", JoinType.LEFT);
}

public Fetch<Z, X> leftFetchFieldName(From<?, Z> root) {
  return root.fetch("fieldName", JoinType.LEFT);
}

public Predicate leftFetchFieldName(From<?, Z> root, CriteriaQuery<?> query) {
  leftFetchFieldName(root); query.distinct(true); return null;
}

public Specification<User> leftFetchFieldName() {
  return (root, query, cb) -> leftFetchFieldName(root, query);
}
```

```java
// Negated versions of methods omitted ...
// Other generated methods omitted ...
```