package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification

import jakarta.persistence.criteria.Join
import org.springframework.data.jpa.domain.Specification
import kotlin.reflect.KProperty1

/** Build «equal to» specification from an entity’s property */
fun <T : Any, V : Any> KProperty1<T, V?>.buildSpecForEqualTo(value: V): Specification<T> = Specification { root, _, criteriaBuilder ->
  criteriaBuilder.equal(root.get<V>(name), value)
}

/** Build the «like» specification from an entity’s property (contains + case-insensitive) */
fun <T : Any> KProperty1<T, String?>.buildSpecForLike(value: String): Specification<T> = Specification { root, _, criteriaBuilder ->
  val escapeChar = '\\'
  val pattern = "%${value.trim().escapeLike(escapeChar)}%".lowercase()

  criteriaBuilder.like(
    criteriaBuilder.lower(root.get(this.name)),
    pattern,
    escapeChar,
  )
}

/**
 * Escape LIKE wildcards so user input is treated literally.
 * Escapes: %, _, and the escape char itself.
 */
private fun String.escapeLike(escapeChar: Char): String = buildString(length) {
  for (ch in this@escapeLike) {
    if (ch == escapeChar || ch == '%' || ch == '_') append(escapeChar)
    append(ch)
  }
}

/** Build «not equal to» specification from an entity’s property */
fun <T : Any, V : Any> KProperty1<T, V>.buildSpecForNotEqualTo(value: V): Specification<T> = Specification { root, _, criteriaBuilder ->
  criteriaBuilder.notEqual(root.get<V>(name), value)
}

/** Build «in» specification from an entity’s property */
fun <T : Any, V : Any> KProperty1<T, V>.buildSpecForIn(values: Collection<V>): Specification<T> = Specification { root, _, criteriaBuilder ->
  criteriaBuilder.and(root.get<V>(name).`in`(values))
}

/** Build «is empty» specification from an entity’s collection property */
fun <T : Any, R> KProperty1<T, Collection<R>>.buildSpecForIsEmpty(): Specification<T> = Specification { root, _, criteriaBuilder ->
  criteriaBuilder.isEmpty(root.get(name))
}

/** Build «is empty» specification from an entity’s collection property name */
fun <T : Any> buildSpecForIsEmpty(propertyName: String): Specification<T> = Specification { root, _, criteriaBuilder ->
  criteriaBuilder.isEmpty(root.get(propertyName))
}

/** Build «equal to» specification joining to a related entity (via a collection property) */
fun <T : Any, R, V : Any> KProperty1<T, Collection<R>>.buildSpecForRelatedEntityPropertyEqualTo(
  property: KProperty1<R, V>,
  value: V,
): Specification<T> = Specification { root, _, criteriaBuilder ->
  val relatedEntities: Join<R, T> = root.join(name)
  criteriaBuilder.equal(relatedEntities.get<V>(property.name), value)
}
