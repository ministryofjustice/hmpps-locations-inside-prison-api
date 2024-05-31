package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import java.util.UUID

@Repository
interface LocationRepository : JpaRepository<Location, UUID> {
  fun findAllByPrisonIdOrderByPathHierarchy(prisonId: String): List<Location>

  fun findOneByPrisonIdAndPathHierarchy(prisonId: String, pathHierarchy: String): Location?

  fun findAllByPrisonIdAndLocationTypeOrderByPathHierarchy(prisonId: String, locationType: LocationType): List<Location>

  @Query("select l from Location l where concat(l.prisonId,'-',l.pathHierarchy) IN (:keys)")
  fun findAllByKeys(keys: List<String>): List<Location>

  @Query("update location set residential_housing_type = :residentialHousingType where id = :id", nativeQuery = true)
  @Modifying
  fun updateResidentialHousingType(id: UUID, residentialHousingType: String)

  @Query("update location set residential_housing_type = null where id = :id", nativeQuery = true)
  @Modifying
  fun updateResidentialHousingTypeToNull(id: UUID)

  @Query("update location set location_type = :locationType where id = :id", nativeQuery = true)
  @Modifying
  fun updateLocationType(id: UUID, locationType: String)

  @Query("delete from location where id = :id", nativeQuery = true)
  @Modifying
  fun deleteLocationById(id: UUID)
}

@Repository
interface NonResidentialLocationRepository : JpaRepository<NonResidentialLocation, UUID> {
  @Query("select nrl from NonResidentialLocation nrl join nrl.nonResidentialUsages u where u.usageType = :usageType and nrl.prisonId = :prisonId")
  fun findAllByPrisonIdAndNonResidentialUsages(prisonId: String, usageType: NonResidentialUsageType): List<NonResidentialLocation>
}
