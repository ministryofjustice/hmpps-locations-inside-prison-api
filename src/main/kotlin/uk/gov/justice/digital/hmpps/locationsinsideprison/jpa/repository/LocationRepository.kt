package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import java.util.*

@Repository
interface LocationRepository : JpaRepository<Location, UUID> {
  fun findAllByPrisonIdOrderByPathHierarchy(prisonId: String): List<Location>

  fun findOneByPrisonIdAndPathHierarchy(prisonId: String, pathHierarchy: String): Location?

  fun findAllByPrisonIdAndLocationTypeOrderByPathHierarchy(prisonId: String, locationType: LocationType): List<Location>

  fun findAllByPrisonIdAndLocalName(prisonId: String, localName: String): List<Location>

  @Query("select l from Location l where concat(l.prisonId,'-',l.pathHierarchy) = :key")
  fun findOneByKey(key: String): Location?

  @Query("select l from Location l where concat(l.prisonId,'-',l.pathHierarchy) IN (:keys)")
  fun findAllByKeys(keys: List<String>): List<Location>

  @Query("update location set residential_housing_type = :residentialHousingType, accommodation_type = :accommodationType where id = :id", nativeQuery = true)
  @Modifying
  fun updateResidentialHousingType(id: UUID, residentialHousingType: String, accommodationType: String)

  @Query("update location set residential_housing_type = null, accommodation_type = null where id = :id", nativeQuery = true)
  @Modifying
  fun updateResidentialHousingTypeToNull(id: UUID)

  @Query("update location set location_type = :locationType where id = :id", nativeQuery = true)
  @Modifying
  fun updateLocationType(id: UUID, locationType: String)

  @Query("delete from location where id = :id", nativeQuery = true)
  @Modifying
  fun deleteLocationById(id: UUID)

  @Query("call setup_prison_demo_locations(:prisonId, :username)", nativeQuery = true)
  @Modifying
  fun setupTrainingPrison(prisonId: String, username: String)

  @Query("select distinct prisonId from Location")
  fun findDistinctListOfPrisonIds(): List<String>
}
