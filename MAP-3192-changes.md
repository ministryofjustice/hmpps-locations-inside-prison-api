# MAP-3192 — Remove deprecated `certification` object

## Summary

The deprecated `Certification` DTO and its associated `certification` field have been fully removed from the API responses and sync request handling. The data it held (`certified` and `certifiedNormalAccommodation`) was already available via `certifiedCell` and the `capacity` object respectively — this change removes the redundant wrapper.

---

## Production code changes

### `dto/LocationDto.kt`
- Deleted the `Certification` data class entirely (certified flag, capacityOfCertifiedCell, certifiedNormalAccommodation, custom equals/hashCode)
- Removed the `certification: Certification?` field from the `Location` DTO

### `dto/LegacyLocation.kt`
- Removed the `certification: Certification?` field from `LegacyLocation`

### `dto/NomisSyncLocationRequest.kt`
- Removed the `certification: Certification?` field from `NomisSyncLocationRequest`
- Simplified the capacity mapping: `certifiedNormalAccommodation` now falls back to `0` rather than checking `certification?.certifiedNormalAccommodation`
- Simplified `certifiedCell` mapping: now uses `certifiedCell == true` directly instead of `(certifiedCell ?: certification?.certified) == true`

### `jpa/Cell.kt`
- Removed the `Certification` import
- Removed the call to `handleNomisCertSync()` during NOMIS sync upsert
- Deleted the deprecated `handleNomisCertSync()` method entirely (it previously wrote a separate history entry and updated capacity/certifiedCell from the old `certification` block)
- Removed the `certification = Certification(...)` line from the `toDto()` mapping

### `jpa/ResidentialLocation.kt`
- Removed the `CertificationDto` import
- Removed `calcPendingWorkingCapacity()` (unused helper)
- Removed `certification = CertificationDto(...)` from both `toDto()` mapping methods (standard and legacy)

### `jpa/VirtualResidentialLocation.kt`
- Removed `certification = null` from the `toDto()` mapping (field no longer exists)

---

## Test changes

The bulk of the diff is test JSON being restructured to match the new response shape. The same logical data is present — just reorganised:

- **Removed** every `"certification": { ... }` object from expected JSON payloads
- **Moved** `"certifiedNormalAccommodation": N` from inside the old `certification` block into the `"capacity"` object
- **Moved** `"certified": true/false` from inside the old `certification` block to a top-level `"certifiedCell": true/false` field

Files updated:
| File | Nature of change |
|---|---|
| `LocationResidentialResourceTest.kt` | ~38 certification blocks restructured |
| `DraftLocationResourceTest.kt` | ~6 certification blocks restructured |
| `LocationKeyResourceTest.kt` | ~5 certification blocks restructured |
| `LocationPrisonIdResourceTest.kt` | ~3 certification blocks restructured |
| `LocationResourceIntTest.kt` | JSON payloads updated |
| `LocationTransformResourceTest.kt` | JSON payloads updated |
| `SyncAndMigrateResourceIntTest.kt` | Sync request payloads updated (removed `certification` from request bodies) |
| `CertificationResourceTest.kt` | Minor adjustments to request/response assertions |
| `LocationTest.kt` | Removed `certification` field from a DTO construction |
