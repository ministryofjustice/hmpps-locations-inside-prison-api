package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationGroupDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import java.util.Properties
import java.util.function.Predicate
import java.util.regex.Pattern

@Service
class LocationGroupFromPropertiesService(
  @Qualifier("residentialGroups") private val properties: Properties,
) {

  fun getLocationGroups(prisonId: String): List<LocationGroupDto> {
    val fullKeys = properties.stringPropertyNames()
    return fullKeys.asSequence()
      .filter { it.startsWith(prisonId) }
      .map { it.substring(prisonId.length + 1) }
      .filterNot { it.contains("_") }
      .sorted()
      .map { LocationGroupDto(it, it, getAvailableSubGroups(prisonId, it)) }
      .toList()
  }

  private fun getAvailableSubGroups(prisonId: String, groupName: String): List<LocationGroupDto> {
    val fullKeys = properties.stringPropertyNames()
    val agencyAndGroupName = "${prisonId}_${groupName}_"
    return fullKeys.asSequence()
      .filter { it.startsWith(agencyAndGroupName) }
      .map { it.substring(agencyAndGroupName.length) }
      .sorted()
      .map { LocationGroupDto(it, it, emptyList()) }
      .toList()
  }

  fun locationGroupFilter(prisonId: String, groupName: String): Predicate<Location> {
    val patterns = properties.getProperty("${prisonId}_$groupName")
      ?: throw EntityNotFoundException("Group $groupName does not exist for prisonId $prisonId.")
    val patternStrings = patterns.split(",")
    return patternStrings.asSequence()
      .map(Pattern::compile)
      .map { pattern -> Predicate { l: Location -> pattern.matcher(l.getPathHierarchy()).matches() } }
      .reduce(Predicate<Location>::or)
  }
}
