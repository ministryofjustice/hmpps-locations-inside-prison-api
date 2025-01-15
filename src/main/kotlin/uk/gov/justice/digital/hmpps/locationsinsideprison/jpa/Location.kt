package uk.gov.justice.digital.hmpps.locationsinsideprison.jpa

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import org.hibernate.Hibernate
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.SortNatural
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ChangeHistory
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationGroupDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisMigrationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PrisonHierarchyDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.formatLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.helper.GeneratedUuidV7
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ActiveLocationCannotBePermanentlyDeactivatedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.NaturalOrderComparator
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.Prisoner
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ResidentialPrisonerLocation
import java.io.Serializable
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDto

@Entity
@DiscriminatorColumn(name = "location_type_discriminator")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
abstract class Location(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  open val id: UUID? = null,

  private var code: String,

  private var pathHierarchy: String,

  @Enumerated(EnumType.STRING)
  open var locationType: LocationType,

  open val prisonId: String,

  @ManyToOne(fetch = FetchType.EAGER, cascade = [CascadeType.PERSIST])
  @JoinColumn(name = "parent_id")
  private var parent: Location? = null,

  open var localName: String? = null,

  open var comments: String? = null,

  open var orderWithinParentLocation: Int? = null,

  private var active: Boolean = true,
  private var archived: Boolean = false,
  private var archivedReason: String? = null,
  open var deactivatedDate: LocalDateTime? = null,
  @Enumerated(EnumType.STRING)
  open var deactivatedReason: DeactivatedReason? = null,
  open var deactivationReasonDescription: String? = null,
  open var proposedReactivationDate: LocalDate? = null,
  open var planetFmReference: String? = null,

  @BatchSize(size = 1000)
  @OneToMany(mappedBy = "parent", fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
  protected open val childLocations: MutableList<Location> = mutableListOf(),

  @BatchSize(size = 100)
  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @SortNatural
  protected open val history: SortedSet<LocationHistory> = sortedSetOf(),

  open val whenCreated: LocalDateTime,
  open var whenUpdated: LocalDateTime,
  open var updatedBy: String,
  open var deactivatedBy: String? = null,
) : Serializable {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun getKey() = "$prisonId-${getPathHierarchy()}"

  open fun setCode(code: String) {
    this.code = code
    updateHierarchicalPath()
  }

  open fun getDerivedLocationType() = locationType

  open fun getPathHierarchy(): String {
    return pathHierarchy
  }

  open fun setParent(parent: Location?) {
    removeParent()
    parent?.addChildLocation(this)
  }

  private fun removeParent() {
    parent?.removeChildLocation(this)
    parent = null
  }

  open fun getCode(): String {
    return code
  }

  open fun getParent(): Location? {
    return parent
  }

  private fun findArchivedParent(): Location? {
    fun findArchivedLocation(location: Location?): Location? {
      if (location == null) {
        return null
      }
      if (!location.isArchived()) {
        return findArchivedLocation(location.getParent())
      }
      return location
    }

    return findArchivedLocation(getParent())
  }

  protected fun findDeactivatedParent(): Location? {
    fun findDeactivatedLocation(location: Location?): Location? {
      if (location == null) {
        return null
      }
      if (location.isActive()) {
        return findDeactivatedLocation(location.getParent())
      }
      return location
    }

    return findDeactivatedLocation(getParent())
  }

  protected fun findArchivedLocationInHierarchy(): Location? {
    if (isArchived()) {
      return this
    }
    return findArchivedParent()
  }

  abstract fun findDeactivatedLocationInHierarchy(): Location?
  abstract fun hasDeactivatedParent(): Boolean
  open fun isActive() = active && !isPermanentlyDeactivated()
  open fun isArchived() = archived
  open fun isResidentialRoomOrConvertedCell() = false
  open fun isActiveAndAllParentsActive() = isActive() && !hasDeactivatedParent()
  open fun isDeactivatedByParent() = isActive() && hasDeactivatedParent()
  open fun isTemporarilyDeactivated() = !isActiveAndAllParentsActive() && !isPermanentlyDeactivated()
  open fun isPermanentlyDeactivated() = findArchivedLocationInHierarchy()?.archived == true

  fun addChildLocation(childLocation: Location): Location {
    childLocation.parent = this
    childLocations.add(childLocation)
    childLocation.updateHierarchicalPath()
    return this
  }

  private fun removeChildLocation(childLocation: Location): Location {
    childLocation.parent = null
    childLocations.remove(childLocation)
    childLocation.updateHierarchicalPath() // recalculate path hierarchy
    return this
  }

  fun findTopLevelLocation(): Location {
    return getParent()?.findTopLevelLocation() ?: this
  }

  fun getParentLocations(): List<Location> {
    val parents = mutableListOf<Location>()

    fun goUp(location: Location?) {
      if (location != null) {
        parents.add(location)
        goUp(location.getParent())
      }
    }

    goUp(this.getParent())
    return parents
  }

  private fun getLevel(): Int {
    fun goUp(location: Location?, level: Int): Int {
      if (location == null) {
        return level
      }
      return goUp(location.getParent(), level.inc())
    }

    return goUp(this, 0)
  }

  fun getHierarchy(): List<LocationSummary> {
    val locationSummary = mutableListOf<LocationSummary>()

    fun goUp(location: Location?) {
      if (location != null) {
        locationSummary.add(location.getLocationSummary())
        goUp(location.parent)
      }
    }

    goUp(this)
    return locationSummary.sortedBy { it.level }
  }

  private fun getDeactivationReason() =
    listOfNotBlank(deactivatedReason?.description, deactivationReasonDescription).joinToString(" - ")

  private fun getLocationSummary(): LocationSummary {
    return LocationSummary(
      id = id,
      code = getCode(),
      type = getDerivedLocationType(),
      pathHierarchy = getPathHierarchy(),
      prisonId = prisonId,
      localName = getDerivedLocalName(),
      level = getLevel(),
    )
  }

  private fun updateHierarchicalPath() {
    pathHierarchy = getHierarchicalPath()
    for (childLocation in childLocations) {
      childLocation.updateHierarchicalPath()
    }
  }

  private fun getHierarchicalPath(): String {
    return if (getParent() == null) {
      getCode()
    } else {
      "${getParent()!!.getHierarchicalPath()}-${getCode()}"
    }
  }

  private fun getActiveResidentialLocationsBelowThisLevel() =
    childLocations.filterIsInstance<ResidentialLocation>().filter { it.isActiveAndAllParentsActive() }

  fun cellLocations() = findAllLeafLocations().filterIsInstance<Cell>().filter { !it.isPermanentlyDeactivated() }

  private fun leafResidentialLocations() = findAllLeafLocations().filterIsInstance<ResidentialLocation>()
    .filter { !it.isPermanentlyDeactivated() && !it.isStructural() && !it.isArea() }

  fun findAllLeafLocations(): List<Location> {
    val leafLocations = mutableListOf<Location>()

    fun traverse(location: Location) {
      if (location.childLocations.isEmpty()) {
        leafLocations.add(location)
      } else {
        for (childLocation in location.childLocations) {
          traverse(childLocation)
        }
      }
    }

    traverse(this)
    return leafLocations
  }

  fun countCellAndNonResLocations() = leafResidentialLocations().count()

  fun findSubLocations(): List<Location> {
    val subLocations = mutableListOf<Location>()

    fun traverse(location: Location) {
      if (this != location) {
        subLocations.add(location)
      }
      for (childLocation in location.childLocations) {
        traverse(childLocation)
      }
    }

    traverse(this)
    return subLocations
  }

  fun addHistory(
    attributeName: LocationAttribute,
    oldValue: String?,
    newValue: String?,
    amendedBy: String,
    amendedDate: LocalDateTime,
    linkedTransaction: LinkedTransaction,
  ): LocationHistory? {
    val old = if (oldValue.isNullOrBlank()) null else oldValue
    val new = if (newValue.isNullOrBlank()) null else newValue
    return if (old != new) {
      val locationHistory = LocationHistory(
        location = this,
        attributeName = attributeName,
        oldValue = old,
        newValue = new,
        amendedBy = amendedBy,
        amendedDate = amendedDate,
        linkedTransaction = linkedTransaction,
      )
      history.add(locationHistory)
      return locationHistory
    } else {
      null
    }
  }

  open fun getHistoryAsList() = history.toList()

  open fun toDto(
    includeChildren: Boolean = false,
    includeParent: Boolean = false,
    includeHistory: Boolean = false,
    countInactiveCells: Boolean = false,
    includeNonResidential: Boolean = true,
    useHistoryForUpdate: Boolean = false,
    countCells: Boolean = false,
    formatLocalName: Boolean = false,
  ): LocationDto {
    val topHistoryEntry = if (useHistoryForUpdate) {
      history.maxByOrNull { it.amendedDate }
    } else {
      null
    }
    val deactivatedLocation = findDeactivatedLocationInHierarchy()
    return LocationDto(
      id = id!!,
      code = getCode(),
      status = getStatus(),
      locationType = getDerivedLocationType(),
      pathHierarchy = pathHierarchy,
      prisonId = prisonId,
      parentId = getParent()?.id,
      topLevelId = findTopLevelLocation().id!!,
      level = getLevel(),
      leafLevel = isLeafLevel(),
      lastModifiedDate = if (useHistoryForUpdate) {
        topHistoryEntry?.amendedDate ?: whenUpdated
      } else {
        whenUpdated
      },
      lastModifiedBy = if (useHistoryForUpdate) {
        topHistoryEntry?.amendedBy ?: updatedBy
      } else {
        updatedBy
      },
      localName = getDerivedLocalName(formatLocalName),
      comments = comments,
      active = isActiveAndAllParentsActive(),
      permanentlyInactive = isPermanentlyDeactivated(),
      permanentlyInactiveReason = archivedReason,
      planetFmReference = planetFmReference,
      deactivatedByParent = isDeactivatedByParent(),
      deactivatedDate = deactivatedLocation?.deactivatedDate,
      deactivatedReason = deactivatedLocation?.deactivatedReason,
      deactivationReasonDescription = deactivatedLocation?.deactivationReasonDescription,
      proposedReactivationDate = deactivatedLocation?.proposedReactivationDate,
      childLocations = if (includeChildren) {
        childLocations.filter { !it.isPermanentlyDeactivated() }
          .filter { includeNonResidential || it is ResidentialLocation }
          .map {
            it.toDto(
              includeChildren = true,
              includeHistory = includeHistory,
              includeNonResidential = includeNonResidential,
            )
          }
      } else {
        null
      },
      parentLocation = if (includeParent) {
        getParent()?.toDto(
          includeChildren = false,
          includeParent = true,
          includeHistory = includeHistory,
        )
      } else {
        null
      },
      changeHistory = if (includeHistory) {
        createHistory()
      } else {
        null
      },
      transactionHistory = if (includeHistory) {
        history
          .asSequence()
          .filter { it.linkedTransaction != null }
          .groupBy { it.linkedTransaction!! }
          .map { (transaction, _) -> transaction.toDto(this) }
          .sortedByDescending { it.txStartTime }
          .toList()
      } else {
        null
      },
      deactivatedBy = deactivatedBy,
    )
  }

  private fun createHistory(): List<ChangeHistory> {
    val withTx = history.asSequence()
      .filter { it.linkedTransaction != null }
      .sortedByDescending { it.linkedTransaction!!.txStartTime }.sortedBy { it.linkedTransaction!!.transactionId }.sortedByDescending { it.id }
      .groupBy { it.linkedTransaction!! }
      .map { (transaction, changes) ->
        changes.groupBy { it.attributeName }
          .filter { it.key.display }
          .mapNotNull { (attribute, history) -> toChangeHistory(attribute, transaction, history) }
      }.flatten().toList()

    val withoutTx = history.asSequence().filter { it.linkedTransaction == null }
      .sortedByDescending { it.amendedDate }.sortedByDescending { it.id }
      .filter { it.attributeName.display }
      .map { it.toDto() }.toList()

    return withTx.plus(withoutTx)
  }

  private fun toChangeHistory(
    attribute: LocationAttribute,
    transaction: LinkedTransaction,
    history: List<LocationHistory>,
  ) = if (history.size > 1) {
    val oldValues = history.mapNotNull { it.oldValue }
    val newValues = history.mapNotNull { it.newValue }
    val multipleValues = oldValues.size > 1 || newValues.size > 1

    ChangeHistory(
      transactionId = transaction.transactionId!!,
      transactionType = transaction.transactionType,
      attribute = attribute.description,
      multipleValues = multipleValues,
      oldValue = if (multipleValues) null else oldValues.firstOrNull(),
      oldValues = oldValues.ifEmpty { null },
      newValue = if (multipleValues) null else newValues.firstOrNull(),
      newValues = newValues.ifEmpty { null },
      amendedBy = transaction.transactionInvokedBy,
      amendedDate = transaction.txStartTime,
    )
  } else {
    history.firstOrNull()?.toDto()
  }

  private fun isLeafLevel() = findSubLocations().isEmpty() && !isStructural() && !isArea()

  fun toPrisonHierarchyDto(): PrisonHierarchyDto {
    val subLocations: List<PrisonHierarchyDto> = childLocations
      .filter { it.isActiveAndAllParentsActive() && (it.isStructural() || it.isCell()) }
      .map { it.toPrisonHierarchyDto() }

    return PrisonHierarchyDto(
      locationId = id!!,
      locationType = locationType,
      locationCode = getCode(),
      fullLocationPath = getPathHierarchy(),
      localName = getDerivedLocalName(true),
      level = getLevel(),
      subLocations = subLocations.ifEmpty { null },
    )
  }

  fun toLocationGroupDto(): LocationGroupDto {
    return LocationGroupDto(
      key = code,
      name = getDerivedLocalName(formatLocalName = true) ?: code,
      children = getActiveResidentialLocationsBelowThisLevel()
        .filter { it.isStructural() }
        .map { it.toLocationGroupDto() }
        .sortedWith(NaturalOrderComparator()),
    )
  }

  open fun toResidentialPrisonerLocation(mapOfPrisoners: Map<String, List<Prisoner>>): ResidentialPrisonerLocation =
    ResidentialPrisonerLocation(
      locationId = id!!,
      key = getKey(),
      locationCode = getCode(),
      locationType = getDerivedLocationType(),
      fullLocationPath = getPathHierarchy(),
      localName = if (isCell()) {
        getCode()
      } else {
        formatLocation(localName ?: getCode())
      },
      prisoners = mapOfPrisoners[getPathHierarchy()] ?: emptyList(),
      deactivatedReason = findDeactivatedLocationInHierarchy()?.deactivatedReason,
      status = getStatus(),
      isLeafLevel = isLeafLevel(),
      accommodationType = (this as? Cell)?.accommodationType,
      subLocations = this.childLocations.filter { !it.isPermanentlyDeactivated() }
        .filterIsInstance<ResidentialLocation>()
        .map {
          it.toResidentialPrisonerLocation(mapOfPrisoners)
        }
        .sortedWith(NaturalOrderComparator()),
    )

  private fun getDerivedLocalName(formatLocalName: Boolean = false) = if (!isCellOrConvertedCell()) {
    if (formatLocalName) {
      localName?.let { formatLocation(it) }
    } else {
      localName
    }
  } else {
    null
  }

  fun getStatus(ignoreParentStatus: Boolean = false): LocationStatus {
    return if ((ignoreParentStatus && isActive()) || isActiveAndAllParentsActive()) {
      if (isConvertedCell()) {
        LocationStatus.NON_RESIDENTIAL
      } else {
        LocationStatus.ACTIVE
      }
    } else {
      if (isPermanentlyDeactivated()) {
        LocationStatus.ARCHIVED
      } else {
        LocationStatus.INACTIVE
      }
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as Location

    return getKey() == other.getKey()
  }

  override fun hashCode(): Int {
    return getKey().hashCode()
  }

  open fun updateLocalName(localName: String?, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    if (!isCell()) {
      addHistory(
        LocationAttribute.DESCRIPTION,
        this.localName,
        localName,
        userOrSystemInContext,
        LocalDateTime.now(clock),
        linkedTransaction,
      )
      this.localName = localName
      this.updatedBy = userOrSystemInContext
      this.whenUpdated = LocalDateTime.now(clock)
    }
  }

  open fun updateComments(comments: String?, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    addHistory(LocationAttribute.COMMENTS, this.comments, comments, userOrSystemInContext, LocalDateTime.now(clock), linkedTransaction)
    this.comments = comments
    this.updatedBy = userOrSystemInContext
    this.whenUpdated = LocalDateTime.now(clock)
  }

  open fun updateCode(code: String?, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction): Location {
    if (code != null && this.getCode() != code) {
      addHistory(LocationAttribute.CODE, getCode(), code, userOrSystemInContext, LocalDateTime.now(clock), linkedTransaction)
      setCode(code)
      this.updatedBy = userOrSystemInContext
      this.whenUpdated = LocalDateTime.now(clock)
    }
    return this
  }

  open fun isConvertedCell(): Boolean = false

  open fun sync(upsert: NomisSyncLocationRequest, clock: Clock, linkedTransaction: LinkedTransaction): Location {
    addHistory(LocationAttribute.CODE, getCode(), upsert.code, upsert.lastUpdatedBy, LocalDateTime.now(clock), linkedTransaction)
    setCode(upsert.code)

    addHistory(
      LocationAttribute.LOCATION_TYPE,
      getDerivedLocationType().description,
      upsert.locationType.description,
      upsert.lastUpdatedBy,
      LocalDateTime.now(clock),
      linkedTransaction,
    )
    this.locationType = upsert.locationType

    addHistory(
      LocationAttribute.DESCRIPTION,
      this.localName,
      upsert.localName,
      upsert.lastUpdatedBy,
      LocalDateTime.now(clock),
      linkedTransaction,
    )
    this.localName = upsert.localName

    addHistory(
      LocationAttribute.COMMENTS,
      this.comments,
      upsert.comments,
      upsert.lastUpdatedBy,
      LocalDateTime.now(clock),
      linkedTransaction,
    )
    this.comments = upsert.comments

    addHistory(
      LocationAttribute.ORDER_WITHIN_PARENT_LOCATION,
      this.orderWithinParentLocation?.toString(),
      upsert.orderWithinParentLocation?.toString(),
      upsert.lastUpdatedBy,
      LocalDateTime.now(clock),
      linkedTransaction,
    )
    this.orderWithinParentLocation = upsert.orderWithinParentLocation

    this.updatedBy = upsert.lastUpdatedBy
    this.whenUpdated = LocalDateTime.now(clock)

    updateActiveStatusSyncOnly(upsert, clock, upsert.lastUpdatedBy, linkedTransaction)
    return this
  }

  private fun updateActiveStatusSyncOnly(
    upsert: NomisMigrationRequest,
    clock: Clock,
    updatedBy: String,
    linkedTransaction: LinkedTransaction,
  ) {
    if (upsert.deactivationReason?.mapsTo() != this.deactivatedReason) {
      if (upsert.isDeactivated()) {
        temporarilyDeactivate(
          deactivatedReason = upsert.deactivationReason!!.mapsTo(),
          deactivatedDate = upsert.deactivatedDate?.atStartOfDay() ?: LocalDateTime.now(clock),
          deactivationReasonDescription = upsert.comments,
          proposedReactivationDate = upsert.proposedReactivationDate,
          userOrSystemInContext = updatedBy,
          clock = clock,
          linkedTransaction = linkedTransaction,
        )
      } else {
        reactivate(updatedBy, clock, linkedTransaction)
      }
    }
  }

  open fun temporarilyDeactivate(
    deactivatedReason: DeactivatedReason,
    deactivatedDate: LocalDateTime,
    linkedTransaction: LinkedTransaction,
    deactivationReasonDescription: String? = null,
    planetFmReference: String? = null,
    proposedReactivationDate: LocalDate? = null,
    userOrSystemInContext: String,
    clock: Clock,
    deactivatedLocations: MutableSet<Location>? = null,
  ): Boolean {
    var dataChanged = false

    if (!isPermanentlyDeactivated()) {
      val amendedDate = LocalDateTime.now(clock)

      if (addHistory(
          LocationAttribute.STATUS,
          getStatus(ignoreParentStatus = true).description,
          LocationStatus.INACTIVE.description,
          userOrSystemInContext,
          amendedDate,
          linkedTransaction,
        ) != null
      ) {
        dataChanged = true
      }

      if (this is Cell) {
        addHistory(
          LocationAttribute.OPERATIONAL_CAPACITY,
          getWorkingCapacityIgnoreParent().toString(),
          "0",
          userOrSystemInContext,
          LocalDateTime.now(clock),
          linkedTransaction,
        )
      }

      if (addHistory(
          LocationAttribute.DEACTIVATION_REASON,
          this.getDeactivationReason(),
          listOfNotBlank(deactivatedReason.description, deactivationReasonDescription).joinToString(" - "),
          userOrSystemInContext,
          amendedDate,
          linkedTransaction,
        ) != null
      ) {
        dataChanged = true
      }
      if (addHistory(
          LocationAttribute.PROPOSED_REACTIVATION_DATE,
          this.proposedReactivationDate?.toString(),
          proposedReactivationDate?.toString(),
          userOrSystemInContext,
          amendedDate,
          linkedTransaction,
        ) != null
      ) {
        dataChanged = true
      }
      if (addHistory(
          LocationAttribute.PLANET_FM_NUMBER,
          this.planetFmReference,
          planetFmReference,
          userOrSystemInContext,
          amendedDate,
          linkedTransaction,
        ) != null
      ) {
        dataChanged = true
      }

      if (isActive()) {
        this.active = false
        this.deactivatedDate = deactivatedDate
        this.deactivatedBy = userOrSystemInContext
        log.info("Temporarily Deactivated Location [${getKey()}]")
      }

      if (dataChanged) {
        this.deactivatedReason = deactivatedReason
        this.deactivationReasonDescription = deactivationReasonDescription
        this.proposedReactivationDate = proposedReactivationDate
        this.planetFmReference = planetFmReference
        this.updatedBy = userOrSystemInContext
        this.whenUpdated = amendedDate
        deactivatedLocations?.add(this)
      }

      if (this is ResidentialLocation) {
        findSubLocations().filterIsInstance<ResidentialLocation>().forEach { location ->
          if (!location.isResidentialRoomOrConvertedCell()) {
            if (location.temporarilyDeactivate(
                deactivatedReason = deactivatedReason,
                deactivatedDate = deactivatedDate,
                deactivationReasonDescription = deactivationReasonDescription,
                linkedTransaction = linkedTransaction,
                planetFmReference = planetFmReference,
                proposedReactivationDate = proposedReactivationDate,
                userOrSystemInContext = userOrSystemInContext,
                clock = clock,
                deactivatedLocations = deactivatedLocations,
              )
            ) {
              dataChanged = true
            }
          }
        }
      }
    }

    return dataChanged
  }

  open fun update(upsert: PatchLocationRequest, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction): Location {
    updateCode(upsert.code, userOrSystemInContext, clock, linkedTransaction)
    if (upsert.localName != null && this.localName != upsert.localName) {
      updateLocalName(upsert.localName, userOrSystemInContext, clock, linkedTransaction)
    }
    return this
  }

  open fun updateDeactivatedDetails(
    deactivatedReason: DeactivatedReason,
    deactivationReasonDescription: String? = null,
    planetFmReference: String? = null,
    proposedReactivationDate: LocalDate? = null,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
  ) {
    if (!isTemporarilyDeactivated()) {
      log.warn("Location [${getKey()}] is not deactivated")
    } else {
      val amendedDate = LocalDateTime.now(clock)
      addHistory(
        LocationAttribute.DEACTIVATION_REASON,
        this.getDeactivationReason(),
        listOfNotBlank(deactivatedReason.description, deactivationReasonDescription).joinToString(" - "),
        userOrSystemInContext,
        amendedDate,
        linkedTransaction,
      )
      addHistory(
        LocationAttribute.PROPOSED_REACTIVATION_DATE,
        this.proposedReactivationDate?.toString(),
        proposedReactivationDate?.toString(),
        userOrSystemInContext,
        amendedDate,
        linkedTransaction,
      )
      addHistory(
        LocationAttribute.PLANET_FM_NUMBER,
        this.planetFmReference,
        planetFmReference,
        userOrSystemInContext,
        amendedDate,
        linkedTransaction,
      )

      this.deactivatedReason = deactivatedReason
      this.deactivationReasonDescription = deactivationReasonDescription
      this.proposedReactivationDate = proposedReactivationDate
      this.planetFmReference = planetFmReference
      this.updatedBy = userOrSystemInContext
      this.whenUpdated = amendedDate

      if (this is ResidentialLocation) {
        findSubLocations().forEach { location ->
          location.updateDeactivatedDetails(
            deactivatedReason = deactivatedReason,
            deactivationReasonDescription = deactivationReasonDescription,
            planetFmReference = planetFmReference,
            proposedReactivationDate = proposedReactivationDate,
            userOrSystemInContext = userOrSystemInContext,
            clock = clock,
            linkedTransaction = linkedTransaction,
          )
        }
      }

      log.info("Temporarily Deactivated Location Updated [${getKey()}]")
    }
  }

  open fun permanentlyDeactivate(
    reason: String,
    deactivatedDate: LocalDateTime,
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
    activeLocationCanBePermDeactivated: Boolean = false,
  ): Boolean {
    if (isPermanentlyDeactivated()) {
      log.warn("Location [${getKey()}] is already permanently deactivated")
      return false
    } else {
      if (isActiveAndAllParentsActive() && !activeLocationCanBePermDeactivated) {
        throw ActiveLocationCannotBePermanentlyDeactivatedException(getKey())
      }
      val amendedDate = LocalDateTime.now(clock)
      addHistory(
        LocationAttribute.STATUS,
        this.getStatus().description,
        LocationStatus.ARCHIVED.description,
        userOrSystemInContext,
        amendedDate,
        linkedTransaction,
      )
      addHistory(
        LocationAttribute.PERMANENT_DEACTIVATION,
        null,
        reason,
        userOrSystemInContext,
        amendedDate,
        linkedTransaction,
      )

      this.archived = true
      this.active = false
      this.deactivatedDate = deactivatedDate
      this.deactivatedReason = null
      this.proposedReactivationDate = null
      this.planetFmReference = null
      this.deactivationReasonDescription = null
      this.deactivatedBy = userOrSystemInContext
      this.archivedReason = reason
      this.updatedBy = userOrSystemInContext
      this.whenUpdated = amendedDate

      if (this is ResidentialLocation) {
        this.cellLocations().filter { !it.isConvertedCell() }.forEach { cellLocation ->
          cellLocation.setCapacity(maxCapacity = 0, workingCapacity = 0, userOrSystemInContext, clock, linkedTransaction)
          cellLocation.deCertifyCell(userOrSystemInContext, clock, linkedTransaction)
        }
      }
      log.info("Permanently Deactivated Location [${getKey()}]")
      return true
    }
  }

  open fun reactivate(
    userOrSystemInContext: String,
    clock: Clock,
    linkedTransaction: LinkedTransaction,
    locationsReactivated: MutableSet<Location>? = null,
    maxCapacity: Int? = null,
    workingCapacity: Int? = null,
    reactivatedLocations: MutableSet<Location>? = null,
    amendedLocations: MutableSet<Location>? = null,
  ): Boolean {
    this.getParent()?.reactivate(
      userOrSystemInContext = userOrSystemInContext,
      clock = clock,
      linkedTransaction = linkedTransaction,
      locationsReactivated = locationsReactivated,
      reactivatedLocations = reactivatedLocations,
      amendedLocations = amendedLocations,
    )
    val previousWorkingCapacity = if (this is ResidentialLocation) {
      calcWorkingCapacity()
    } else {
      null
    }

    val capacityAdjusted = maxCapacity != null || workingCapacity != null
    if (this is Cell && capacityAdjusted) {
      setCapacity(
        maxCapacity = maxCapacity ?: getMaxCapacity() ?: 0,
        workingCapacity = workingCapacity ?: getWorkingCapacity() ?: 0,
        userOrSystemInContext = userOrSystemInContext,
        clock = clock,
        linkedTransaction = linkedTransaction,
      )
      amendedLocations?.add(this)
      amendedLocations?.addAll(this.getParentLocations())
    }

    if (isTemporarilyDeactivated()) {
      val amendedDate = LocalDateTime.now(clock)
      addHistory(
        LocationAttribute.STATUS,
        this.getStatus().description,
        LocationStatus.ACTIVE.description,
        userOrSystemInContext,
        amendedDate,
        linkedTransaction,
      )
      addHistory(
        LocationAttribute.PROPOSED_REACTIVATION_DATE,
        proposedReactivationDate?.toString(),
        null,
        userOrSystemInContext,
        amendedDate,
        linkedTransaction,
      )
      addHistory(
        LocationAttribute.PLANET_FM_NUMBER,
        planetFmReference,
        null,
        userOrSystemInContext,
        amendedDate,
        linkedTransaction,
      )

      this.active = true
      this.deactivatedReason = null
      this.deactivatedDate = null
      this.deactivationReasonDescription = null
      this.planetFmReference = null
      this.proposedReactivationDate = null
      this.updatedBy = userOrSystemInContext
      this.whenUpdated = amendedDate
      this.deactivatedBy = null

      if (this is Cell && !isConvertedCell()) {
        certifyCell(userOrSystemInContext = userOrSystemInContext, clock = clock, linkedTransaction = linkedTransaction)
        this.residentialHousingType = this.accommodationType.mapToResidentialHousingType()
      }

      if (this is Cell && !capacityAdjusted) {
        addHistory(
          LocationAttribute.OPERATIONAL_CAPACITY,
          previousWorkingCapacity.toString(),
          getWorkingCapacityIgnoreParent().toString(),
          userOrSystemInContext,
          LocalDateTime.now(clock),
          linkedTransaction,
        )
      }
      reactivatedLocations?.add(this)
      amendedLocations?.addAll(this.getParentLocations())
      log.info("Re-activated Location [${getKey()}]")
      return true
    }

    return false
  }

  override fun toString(): String {
    return getKey()
  }

  private fun isCellOrConvertedCell() = this is Cell || isConvertedCell()
  open fun isCell() = false
  fun isVirtualResidentialLocation() = this is VirtualResidentialLocation
  fun isStructural() = locationType in ResidentialLocationType.entries.filter { it.structural }.map { it.baseType }
  fun isArea() = locationType in ResidentialLocationType.entries.filter { it.area }.map { it.baseType }

  open fun toLegacyDto(includeHistory: Boolean = false): LegacyLocation {
    return LegacyLocation(
      id = id!!,
      code = getCode(),
      locationType = getDerivedLocationType(),
      pathHierarchy = pathHierarchy,
      prisonId = prisonId,
      parentId = getParent()?.id,
      lastModifiedDate = whenUpdated,
      lastModifiedBy = updatedBy,
      localName = localName,
      comments = comments,
      orderWithinParentLocation = orderWithinParentLocation,
      active = isActiveAndAllParentsActive(),
      deactivatedDate = findDeactivatedLocationInHierarchy()?.deactivatedDate?.toLocalDate(),
      deactivatedReason = findDeactivatedLocationInHierarchy()?.deactivatedReason,
      proposedReactivationDate = findDeactivatedLocationInHierarchy()?.proposedReactivationDate,
      permanentlyDeactivated = isPermanentlyDeactivated(),
      changeHistory = if (includeHistory) history.map { it.toDto() } else null,
    )
  }
}

@Schema(description = "Location Hierarchy Summary")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LocationSummary(
  @Schema(description = "ID of location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  val id: UUID? = null,
  @Schema(
    description = "Prison ID where the location is situated",
    required = true,
    example = "MDI",
    minLength = 3,
    maxLength = 5,
    pattern = "^[A-Z]{2}I|ZZGHI$",
  )
  val prisonId: String,
  @Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  val code: String,
  @Schema(description = "Location type", example = "WING", required = true)
  val type: LocationType,
  @Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  val localName: String? = null,
  @Schema(description = "Full path of the location within the prison", example = "A-1-001", required = true)
  val pathHierarchy: String? = null,
  @Schema(
    description = "Current Level within hierarchy, starts at 1, e.g Wing = 1",
    examples = ["1", "2", "3"],
    required = true,
  )
  val level: Int,
)

fun listOfNotBlank(vararg elements: String?): List<String> = elements.filterNotNull().filter { it.isNotBlank() }
