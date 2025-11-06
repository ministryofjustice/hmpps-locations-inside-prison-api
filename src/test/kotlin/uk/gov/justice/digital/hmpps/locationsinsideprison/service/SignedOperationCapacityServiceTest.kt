package uk.gov.justice.digital.hmpps.locationsinsideprison.service

import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.SignedOperationCapacityValidRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.integration.TestBase.Companion.clock
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.SignedOperationCapacity
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.LinkedTransactionRepository
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.repository.SignedOperationCapacityRepository
import java.time.LocalDateTime
import java.util.*

class SignedOperationCapacityServiceTest {
  private val locationService: LocationService = mock()
  private val signedOperationCapacityRepository: SignedOperationCapacityRepository = mock()
  private val linkedTransactionRepository: LinkedTransactionRepository = mock()
  private val prisonConfigurationService: PrisonConfigurationService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val service: SignedOperationCapacityService = SignedOperationCapacityService(
    locationService,
    signedOperationCapacityRepository,
    linkedTransactionRepository,
    prisonConfigurationService,
    telemetryClient,
    clock,
  )

  @BeforeEach
  fun setUp() {
    whenever(linkedTransactionRepository.save(any())).thenReturn(Mockito.mock())
    whenever(prisonConfigurationService.getPrisonConfiguration(any())).thenReturn(
      PrisonConfigurationDto(
        prisonId = "MDI",
        resiLocationServiceActive = ServiceStatus.ACTIVE,
        includeSegregationInRollCount = ServiceStatus.INACTIVE,
        certificationApprovalRequired = ServiceStatus.INACTIVE,
        nonResiServiceActive = ServiceStatus.INACTIVE,
      ),
    )
  }

  private val residentialSummary = ResidentialSummary(
    prisonSummary = PrisonSummary(
      prisonName = "HMP Moorland",
      signedOperationalCapacity = 0,
      workingCapacity = 100,
      maxCapacity = 135,
      numberOfCellLocations = 100,
    ),
    subLocations = emptyList(),
    topLevelLocationType = "Wing",
  )

  @Test
  fun `Get signed operation capacity for the prison when record found`() {
    whenever(signedOperationCapacityRepository.findByPrisonId(any())).thenReturn(
      SignedOperationCapacity(
        prisonId = "MDI",
        signedOperationCapacity = 130,
        whenUpdated = LocalDateTime.now(clock),
        updatedBy = "Updated by",
      ),
    )
    val result = service.getSignedOperationalCapacity("MDI")
    assertThat(result?.signedOperationCapacity).isEqualTo(130)
  }

  @Test
  fun `Get null for the prison when record not found`() {
    whenever(signedOperationCapacityRepository.findById(any())).thenReturn(Optional.empty())
    val result = service.getSignedOperationalCapacity("MDI")
    assertThat(result).isNull()
  }

  @Test
  fun `Create signed operation capacity`() {
    val signedOperationCapacity = 130
    val prisonId = "MDI"
    val updatedBy = "USER"
    val request: SignedOperationCapacityValidRequest = mock()
    val signedOpCapResponse: SignedOperationCapacity = mock()

    val prisonSignedOperationCap = SignedOperationCapacityDto(
      signedOperationCapacity = signedOperationCapacity,
      prisonId = prisonId,
      updatedBy = updatedBy,
      whenUpdated = LocalDateTime.now(clock),
    )
    whenever(request.signedOperationCapacity).thenReturn(signedOperationCapacity)
    whenever(request.prisonId).thenReturn(prisonId)
    whenever(request.updatedBy).thenReturn(updatedBy)
    whenever(signedOpCapResponse.toSignedOperationCapacityDto()).thenReturn(prisonSignedOperationCap)
    whenever(signedOperationCapacityRepository.findById(any())).thenReturn(Optional.empty())
    whenever(signedOperationCapacityRepository.save(any())).thenReturn(signedOpCapResponse)
    whenever(locationService.calculateMaxCapOfPrison(prisonId = prisonId)).thenReturn(130)
    val result = service.saveSignedOperationalCapacity(request)
    assertThat(result.newRecord).isTrue()

    verify(signedOperationCapacityRepository).save(
      SignedOperationCapacity(
        signedOperationCapacity = signedOperationCapacity,
        prisonId = prisonId,
        updatedBy = updatedBy,
        whenUpdated = LocalDateTime.now(clock),
      ),
    )
    verify(telemetryClient).trackEvent(any(), anyMap(), anyOrNull())
  }

  @Test
  fun `Update signed operation capacity`() {
    val signedOperationCapacity = 130
    val prisonId = "MDI"
    val updatedBy = "USER"
    val request: SignedOperationCapacityValidRequest = mock()
    val signedOperationCapacityResponse: SignedOperationCapacity = mock()

    val existingRecord = SignedOperationCapacity(
      signedOperationCapacity = signedOperationCapacity,
      prisonId = prisonId,
      updatedBy = updatedBy,
      whenUpdated = LocalDateTime.now(clock),
    )

    whenever(request.signedOperationCapacity).thenReturn(signedOperationCapacity)
    whenever(request.prisonId).thenReturn(prisonId)
    whenever(request.updatedBy).thenReturn(updatedBy)
    whenever(signedOperationCapacityRepository.findByPrisonId(any())).thenReturn(existingRecord)
    whenever(signedOperationCapacityRepository.save(any())).thenReturn(signedOperationCapacityResponse)
    whenever(locationService.calculateMaxCapOfPrison(prisonId = prisonId)).thenReturn(130)

    service.saveSignedOperationalCapacity(request)

    verify(telemetryClient).trackEvent(any(), anyMap(), anyOrNull())
  }
}
