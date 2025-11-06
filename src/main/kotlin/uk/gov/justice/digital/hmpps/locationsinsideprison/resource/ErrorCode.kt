package uk.gov.justice.digital.hmpps.locationsinsideprison.resource

/**
 * Codes that can be used by api clients to uniquely discriminate between error types,
 * instead of relying on non-constant text descriptions of HTTP status codes.
 *
 * NB: Once defined, the values must not be changed
 */
enum class ErrorCode(val errorCode: Int) {
  LocationNotFound(101),
  ValidationFailure(102),
  LocationAlreadyExists(103),
  LocationCannotBeReactivated(104),
  LocationCannotBePermanentlyDeactivated(105),
  ZeroCapacityForNonSpecialistNormalAccommodationNotAllowed(106),
  PermanentlyDeactivatedLocationCannotByUpdated(107),
  TransactionNotFound(108),
  DeactivationErrorLocationsContainPrisoners(109),
  SignedOperationCapacityForPrisonNotFound(110),
  LocationPrefixNotFound(111),
  WorkingCapacityLimitExceeded(112),
  MaxCapacityLimitExceeded(113),
  WorkingCapacityExceedsMaxCapacity(114),
  MaxCapacityCannotBeZero(115),
  SignedOpCapCannotBeMoreThanMaxCap(116),
  MaxCapacityCannotBeBelowOccupancyLevel(117),
  OtherReasonNotProvidedForDeactivation(118),
  DuplicateLocalNameAtSameLevel(119),
  LocationCannotByUpdatedAsConvertedCell(120),
  ApprovalRequestNotFound(121),
  LockedLocationCannotBeUpdated(122),
  ApprovalRequestAlreadyExists(123),
  OnlyDraftLocationsCanBeDeleted(errorCode = 124),
  ApprovalRequestNotInPendingStatus(errorCode = 125),
  LocationDoesNotRequireApproval(errorCode = 126),
  LocationCannotBeUnlockedWhenNotLocked(errorCode = 127),
  SignedOpCapCannotChangedWithoutApproval(128),
  ApprovalRequestAtWrongLevel(errorCode = 129),
  DuplicateNonResidentialLocalNameInSamePrison(130),
  CreationForbiddenWhenApprovalPending(errorCode = 131),
}
