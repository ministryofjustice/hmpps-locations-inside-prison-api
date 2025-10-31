package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.NonResidentialUsageType
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.ServiceType
import java.util.*

@Repository
interface NonResidentialLocationRepository :
  JpaRepository<NonResidentialLocation, UUID>,
  JpaSpecificationExecutor<NonResidentialLocation> {
  @Query("select nrl from NonResidentialLocation nrl join fetch nrl.nonResidentialUsages u where u.usageType = :usageType and nrl.prisonId = :prisonId")
  fun findAllByPrisonIdAndNonResidentialUsages(prisonId: String, usageType: NonResidentialUsageType): List<NonResidentialLocation>

  @Query("select nrl from NonResidentialLocation nrl left join fetch nrl.nonResidentialUsages u where nrl.prisonId = :prisonId")
  fun findAllByPrisonId(prisonId: String): List<NonResidentialLocation>

  @Query("select nrl from NonResidentialLocation nrl join fetch nrl.nonResidentialUsages u where nrl.prisonId = :prisonId")
  fun findAllByPrisonIdWithNonResidentialUsages(prisonId: String): List<NonResidentialLocation>

  @Query("select l from NonResidentialLocation l where concat(l.prisonId,'-',l.pathHierarchy) = :key")
  fun findOneByKey(key: String): NonResidentialLocation?

  @Query("select nrl from NonResidentialLocation nrl join fetch nrl.services u where u.serviceType = :serviceType and nrl.prisonId = :prisonId")
  fun findAllByPrisonIdAndNonResidentialService(prisonId: String, serviceType: ServiceType): List<NonResidentialLocation>

  @Query("select nrl from NonResidentialLocation nrl join fetch nrl.services u where nrl.prisonId = :prisonId")
  fun findAllByPrisonIdWithNonResidentialServices(prisonId: String): List<NonResidentialLocation>
}
