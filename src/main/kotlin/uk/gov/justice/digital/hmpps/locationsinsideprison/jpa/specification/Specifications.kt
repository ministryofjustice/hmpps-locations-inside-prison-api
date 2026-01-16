package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.specification

import jakarta.persistence.criteria.Join
import org.springframework.data.jpa.domain.Specification
import kotlin.reflect.KProperty1

/** Build «equal to» specification from an entity’s property */
fun <T : Any, V : Any> KProperty1<T, V?>.buildSpecForEqualTo(value: V): Specification<T> = Specification { root, _, criteriaBuilder ->
  criteriaBuilder.equal(root.get<V>(name), value)
}

/** Build «not equal to» specification from an entity’s property */
fun <T : Any, V : Any> KProperty1<T, V>.buildSpecForNotEqualTo(value: V): Specification<T> = Specification { root, _, criteriaBuilder ->
  criteriaBuilder.notEqual(root.get<V>(name), value)
}

/** Build «in» specification from an entity’s property */
fun <T : Any, V : Any> KProperty1<T, V>.buildSpecForIn(values: Collection<V>): Specification<T> = Specification { root, _, criteriaBuilder ->
  criteriaBuilder.and(root.get<V>(name).`in`(values))
}

/** Build «equal to» specification joining to a related entity (via a collection property) */
fun <T : Any, R, V : Any> KProperty1<T, Collection<R>>.buildSpecForRelatedEntityPropertyEqualTo(
  property: KProperty1<R, V>,
  value: V,
): Specification<T> = Specification { root, _, criteriaBuilder ->
  val relatedEntities: Join<R, T> = root.join(name)
  criteriaBuilder.equal(relatedEntities.get<V>(property.name), value)
}
