package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.NotificationGroup
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.PrisonNotificationMailboxService
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.UpdateNotificationMailboxRequest

@RestController
@Validated
@RequestMapping("/prison-configuration", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(
  name = "Prison Configuration",
  description = "Allows views and updates on prison configuration",
)
class PrisonNotificationMailboxResource(
  private val prisonNotificationMailboxService: PrisonNotificationMailboxService,
) {

  @GetMapping("/notification-mailboxes/defaults/{notificationGroup}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_LOCATION_CONFIG_ADMIN')")
  @Operation(
    summary = "Get default notification mailbox email addresses",
    description = "Requires role LOCATION_CONFIG_ADMIN",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns default notification mailbox",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the LOCATION_CONFIG_ADMIN role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "No default notification mailbox found for this notification group",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getDefaultNotificationMailbox(
    @Schema(description = "Notification group", example = "CERT_VIEWER", required = true)
    @PathVariable
    notificationGroup: NotificationGroup,
  ) = prisonNotificationMailboxService.getDefaultMailboxes(notificationGroup)

  @PutMapping("/notification-mailboxes/defaults/{notificationGroup}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_LOCATION_CONFIG_ADMIN')")
  @Operation(
    summary = "Replace all default notification mailbox email addresses",
    description = "Overwrites any existing default email addresses for this notification group. Requires role LOCATION_CONFIG_ADMIN",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns updated default notification mailbox",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the LOCATION_CONFIG_ADMIN role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun replaceDefaultNotificationMailbox(
    @Schema(description = "Notification group", example = "CERT_VIEWER", required = true)
    @PathVariable
    notificationGroup: NotificationGroup,
    @RequestBody @Valid
    request: UpdateNotificationMailboxRequest,
  ) = prisonNotificationMailboxService.replaceDefaultMailboxes(notificationGroup, request.emailAddresses)

  @DeleteMapping("/notification-mailboxes/defaults/{notificationGroup}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ROLE_LOCATION_CONFIG_ADMIN')")
  @Operation(
    summary = "Delete all default notification mailbox email addresses",
    description = "Requires role LOCATION_CONFIG_ADMIN",
    responses = [
      ApiResponse(
        responseCode = "204",
        description = "Default notification mailbox deleted",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the LOCATION_CONFIG_ADMIN role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "No default notification mailbox found for this notification group",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun deleteDefaultNotificationMailbox(
    @Schema(description = "Notification group", example = "CERT_VIEWER", required = true)
    @PathVariable
    notificationGroup: NotificationGroup,
  ) = prisonNotificationMailboxService.deleteDefaultMailboxes(notificationGroup)

  @GetMapping("/{prisonId}/notification-mailboxes/{notificationGroup}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_LOCATION_CONFIG_ADMIN')")
  @Operation(
    summary = "Get notification mailbox email addresses for a prison",
    description = "Requires role LOCATION_CONFIG_ADMIN",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns notification mailbox",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the LOCATION_CONFIG_ADMIN role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "No notification mailbox found for this prison and notification group",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun getNotificationMailbox(
    @Schema(
      description = "Prison ID",
      required = true,
      example = "MDI",
      minLength = 3,
      maxLength = 5,
      pattern = "^[A-Z]{2}I|ZZGHI$",
    )
    @Size(min = 3, message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
    @PathVariable
    prisonId: String,
    @Schema(description = "Notification group", example = "CERT_ADMIN", required = true)
    @PathVariable
    notificationGroup: NotificationGroup,
    @Schema(description = "Include default mailbox when no prison-specific mailbox exists", example = "true")
    @RequestParam(defaultValue = "true")
    includeDefault: Boolean,
  ) = prisonNotificationMailboxService.getMailboxes(prisonId, notificationGroup, includeDefault)

  @PutMapping("/{prisonId}/notification-mailboxes/{notificationGroup}")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_LOCATION_CONFIG_ADMIN')")
  @Operation(
    summary = "Replace all notification mailbox email addresses for a prison",
    description = "Overwrites any existing email addresses for this prison and notification group. Requires role LOCATION_CONFIG_ADMIN",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Returns updated notification mailbox",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the LOCATION_CONFIG_ADMIN role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Prison not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun replaceNotificationMailbox(
    @Schema(
      description = "Prison ID",
      required = true,
      example = "MDI",
      minLength = 3,
      maxLength = 5,
      pattern = "^[A-Z]{2}I|ZZGHI$",
    )
    @Size(min = 3, message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
    @PathVariable
    prisonId: String,
    @Schema(description = "Notification group", example = "CERT_ADMIN", required = true)
    @PathVariable
    notificationGroup: NotificationGroup,
    @RequestBody @Valid
    request: UpdateNotificationMailboxRequest,
  ) = prisonNotificationMailboxService.replaceMailboxes(prisonId, notificationGroup, request.emailAddresses)

  @DeleteMapping("/{prisonId}/notification-mailboxes/{notificationGroup}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ROLE_LOCATION_CONFIG_ADMIN')")
  @Operation(
    summary = "Delete all notification mailbox email addresses for a prison",
    description = "Requires role LOCATION_CONFIG_ADMIN",
    responses = [
      ApiResponse(
        responseCode = "204",
        description = "Notification mailbox deleted",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Missing required role. Requires the LOCATION_CONFIG_ADMIN role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "No notification mailbox found for this prison and notification group",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  fun deleteNotificationMailbox(
    @Schema(
      description = "Prison ID",
      required = true,
      example = "MDI",
      minLength = 3,
      maxLength = 5,
      pattern = "^[A-Z]{2}I|ZZGHI$",
    )
    @Size(min = 3, message = "Prison ID cannot be blank")
    @Size(max = 5, message = "Prison ID must be 3 characters or ZZGHI")
    @Pattern(regexp = "^[A-Z]{2}I|ZZGHI$", message = "Prison ID must be 3 characters or ZZGHI")
    @PathVariable
    prisonId: String,
    @Schema(description = "Notification group", example = "CERT_ADMIN", required = true)
    @PathVariable
    notificationGroup: NotificationGroup,
  ) = prisonNotificationMailboxService.deleteMailboxes(prisonId, notificationGroup)
}
