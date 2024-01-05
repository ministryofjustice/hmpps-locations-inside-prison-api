package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
@RequestMapping("/test", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Test API endpoint",
  description = "Tests the API endpoint",
)
class HelloWorldResource() {
  @GetMapping("/hello")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Say hello",
    description = "Says hello",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns hello",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Data not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun sayHello(): HelloResponse {
    return HelloResponse("HELLO")
  }
}

@Schema(description = "Hello response")
data class HelloResponse(
  @Schema(description = "The message response", example = "Hello", required = true)
  val message: String,
)
