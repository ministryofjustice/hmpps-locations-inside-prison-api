package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import java.util.*

@Repository
interface NonResidentialLocationRepository : JpaRepository<NonResidentialLocation, UUID> {
  @Query("select nrl from NonResidentialLocation nrl join nrl.nonResidentialUsages u where u.usageType = :usageType and nrl.prisonId = :prisonId")
  fun findAllByPrisonIdAndNonResidentialUsages(prisonId: String, usageType: NonResidentialUsageType): List<NonResidentialLocation>

  @Query("select l from NonResidentialLocation l where concat(l.prisonId,'-',l.pathHierarchy) = :key")
  fun findOneByKey(key: String): NonResidentialLocation?
}
