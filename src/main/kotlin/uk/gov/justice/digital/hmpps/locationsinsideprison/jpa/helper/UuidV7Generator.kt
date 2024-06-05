package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.helper

import com.fasterxml.uuid.Generators
import com.fasterxml.uuid.NoArgGenerator
import org.hibernate.annotations.IdGeneratorType
import org.hibernate.annotations.ValueGenerationType
import org.hibernate.engine.spi.SharedSessionContractImplementor
import org.hibernate.generator.BeforeExecutionGenerator
import org.hibernate.generator.EventType
import org.hibernate.generator.EventTypeSets
import java.util.EnumSet
import java.util.UUID

class UuidV7Generator : BeforeExecutionGenerator {
  companion object {
    val uuidGenerator: NoArgGenerator = Generators.timeBasedEpochGenerator(null)
  }

  override fun getEventTypes(): EnumSet<EventType> {
    return EventTypeSets.INSERT_ONLY
  }

  override fun generate(
    session: SharedSessionContractImplementor?,
    owner: Any?,
    currentValue: Any?,
    eventType: EventType?,
  ): UUID {
    // NB: the default `org.hibernate.id.uuid.UuidGenerator` also ignores session, owner, currentValue and eventType
    return uuidGenerator.generate()
  }
}

@IdGeneratorType(UuidV7Generator::class)
@ValueGenerationType(generatedBy = UuidV7Generator::class)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD)
annotation class GeneratedUuidV7
