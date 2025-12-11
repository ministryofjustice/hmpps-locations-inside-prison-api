package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.Location
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.LocationType
import java.util.*

@Repository
interface LocationRepository : JpaRepository<Location, UUID> {

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  fun findAllByPrisonIdOrderByPathHierarchy(prisonId: String): List<Location>

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  fun findOneByPrisonIdAndPathHierarchy(prisonId: String, pathHierarchy: String): Location?

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  fun findAllByPrisonIdAndLocationTypeOrderByPathHierarchy(prisonId: String, locationType: LocationType): List<Location>

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  fun findAllByPrisonIdAndParentIdAndLocalName(prisonId: String, parentId: UUID, localName: String): List<Location>

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  fun findAllByPrisonIdAndParentIsNullAndLocalName(prisonId: String, localName: String): List<Location>

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  @Query("select l from Location l where concat(l.prisonId,'-',l.pathHierarchy) = :key")
  fun findOneByKey(key: String): Location?

  @EntityGraph(value = "resi.location.graph", type = EntityGraph.EntityGraphType.LOAD)
  @Query("select l from Location l where concat(l.prisonId,'-',l.pathHierarchy) IN (:keys)")
  fun findAllByKeys(keys: List<String>): List<Location>

  @Query("update location set residential_housing_type = :residentialHousingType, certified_cell = false, accommodation_type = :accommodationType, location_type_discriminator = (case when location_type = 'CELL' then 'CELL' when code IN ('RECP', 'COURT', 'TAP', 'CSWAP') then 'VIRTUAL' else 'RESIDENTIAL' end) where id = :id", nativeQuery = true)
  @Modifying
  fun updateResidentialHousingType(id: UUID, residentialHousingType: String, accommodationType: String)

  @Query("update location set residential_housing_type = null, certified_cell = null, accommodation_type = null, location_type_discriminator = 'NON_RESIDENTIAL' where id = :id", nativeQuery = true)
  @Modifying
  fun updateResidentialHousingTypeToNull(id: UUID)

  @Query("update location set location_type = :locationType, certified_cell = false, location_type_discriminator = (case when residential_housing_type IS NULL then 'NON_RESIDENTIAL' when :locationType = 'CELL' then 'CELL' when code IN ('RECP', 'COURT', 'TAP', 'CSWAP') then 'VIRTUAL' else 'RESIDENTIAL' end) where id = :id", nativeQuery = true)
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
