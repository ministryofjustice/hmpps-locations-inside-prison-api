package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationGroupDto
import java.util.Properties

@Service
class LocationGroupFromPropertiesService(
  @Qualifier("whereaboutsGroups") private val groupsProperties: Properties,
) {

  fun getLocationGroups(prisonId: String): List<LocationGroupDto> {
    val fullKeys = groupsProperties.stringPropertyNames()
    return fullKeys.asSequence()
      .filter { it.startsWith(prisonId) }
      .map { it.substring(prisonId.length + 1) }
      .filterNot { it.contains("_") }
      .sorted()
      .map { LocationGroupDto(it, it, getAvailableSubGroups(prisonId, it)) }
      .toList()
  }

  private fun getAvailableSubGroups(prisonId: String, groupName: String): List<LocationGroupDto> {
    val fullKeys = groupsProperties.stringPropertyNames()
    val agencyAndGroupName = "${prisonId}_${groupName}_"
    return fullKeys.asSequence()
      .filter { it.startsWith(agencyAndGroupName) }
      .map { it.substring(agencyAndGroupName.length) }
      .sorted()
      .map { LocationGroupDto(it, it, emptyList()) }
      .toList()
  }
}
