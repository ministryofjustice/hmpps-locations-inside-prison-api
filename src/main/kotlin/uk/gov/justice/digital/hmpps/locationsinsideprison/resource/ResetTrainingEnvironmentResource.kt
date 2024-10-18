package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.TrainingService

@RestController
@Validated
@RequestMapping("/reset-training", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Training Environment Reset",
  description = "Resets all training data",
)
@Profile("train")
class ResetTrainingEnvironmentResource(
  private val trainingService: TrainingService,
) {
  @PutMapping
  @Operation(
    summary = "Resets all prisons in the training environment",
    description = "No role required as hidden and only exists in the training environment",
    hidden = true,
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns 200 on success",
      ),
    ],
  )
  @Hidden
  fun resetTrainingEnvironment() {
    trainingService.setupTrainingPrisons()
  }
}
