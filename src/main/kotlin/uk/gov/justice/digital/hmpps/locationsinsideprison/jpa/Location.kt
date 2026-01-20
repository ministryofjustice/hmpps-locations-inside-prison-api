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
import jakarta.persistence.NamedAttributeNode
import jakarta.persistence.NamedEntityGraph
import jakarta.persistence.NamedEntityGraphs
import jakarta.persistence.NamedSubgraph
import jakarta.persistence.OneToMany
import jakarta.xml.bind.ValidationException
import org.hibernate.Hibernate
import org.hibernate.annotations.SortNatural
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.ChangeHistory
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.DerivedLocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LegacyLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationGroupDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.LocationStatus
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.NomisSyncLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PatchLocationRequest
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.PrisonHierarchyDto
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.formatLocation
import uk.gov.justice.digital.hmpps.locationsinsideprison.jpa.helper.GeneratedUuidV7
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.ActiveLocationCannotBePermanentlyDeactivatedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.resource.PendingApprovalOnLocationCannotBeUpdatedException
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.NaturalOrderComparator
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.Prisoner
import uk.gov.justice.digital.hmpps.locationsinsideprison.service.ResidentialPrisonerLocation
import java.io.Serializable
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import uk.gov.justice.digital.hmpps.locationsinsideprison.dto.Location as LocationDto

val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@NamedEntityGraphs(
  value = [
    NamedEntityGraph(
      name = "resi.location.graph",
      attributeNodes = [
        NamedAttributeNode("parent"),
        NamedAttributeNode(value = "childLocations"),
      ],
      subclassSubgraphs = [
        NamedSubgraph(
          name = "residential.subgraph",
          type = ResidentialLocation::class,
          attributeNodes = [
            NamedAttributeNode("capacity"),
          ],
        ),
        NamedSubgraph(
          name = "non.residential.subgraph",
          type = NonResidentialLocation::class,
          attributeNodes = [
            NamedAttributeNode("nonResidentialUsages"),
            NamedAttributeNode("services"),
          ],
        ),
        NamedSubgraph(
          name = "cell.subgraph",
          type = Cell::class,
          attributeNodes = [
            NamedAttributeNode("usedFor"),
            NamedAttributeNode("specialistCellTypes"),
            NamedAttributeNode("attributes"),
            NamedAttributeNode("pendingChange"),
          ],
        ),
      ],
    ),
  ],
)
@Entity
@DiscriminatorColumn(name = "location_type_discriminator")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
abstract class Location(
  @Id
  @GeneratedUuidV7
  @Column(name = "id", updatable = false, nullable = false)
  open val id: UUID? = null,

  open var code: String,

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

  @Enumerated(EnumType.STRING)
  open var status: LocationStatus,

  private var archivedReason: String? = null,
  open var deactivatedDate: LocalDateTime? = null,
  @Enumerated(EnumType.STRING)
  open var deactivatedReason: DeactivatedReason? = null,
  open var deactivationReasonDescription: String? = null,
  open var proposedReactivationDate: LocalDate? = null,
  open var planetFmReference: String? = null,

  @OneToMany(mappedBy = "parent", fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
  @SortNatural
  protected open val childLocations: SortedSet<Location> = sortedSetOf(),

  @OneToMany(mappedBy = "location", fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
  @SortNatural
  protected open val history: SortedSet<LocationHistory> = sortedSetOf(),

  open val whenCreated: LocalDateTime,
  open var whenUpdated: LocalDateTime,
  open var updatedBy: String,
  open var deactivatedBy: String? = null,
) : Comparable<Location>,
  Serializable {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    private val COMPARATOR = compareBy<Location>
      { it.getKey() }
  }

  override fun compareTo(other: Location) = COMPARATOR.compare(this, other)

  fun getKey() = "$prisonId-${getPathHierarchy()}"

  open fun setLocationCode(code: String) {
    this.code = code
    updateHierarchicalPath()
  }

  open fun getDerivedLocationType() = locationType

  open fun getPathHierarchy(): String = pathHierarchy

  open fun setParent(parent: Location?) {
    removeParent()
    parent?.addChildLocation(this)
  }

  private fun removeParent() {
    parent?.removeChildLocation(this)
    parent = null
  }

  open fun getLocationCode(): String = code

  open fun getParent(): Location? = parent

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

  private fun findArchivedLocationInHierarchy(): Location? {
    if (isArchived()) {
      return this
    }
    return findArchivedParent()
  }

  abstract fun findDeactivatedLocationInHierarchy(): Location?
  abstract fun hasDeactivatedParent(): Boolean

  open fun isActive() = status == LocationStatus.ACTIVE && !isPermanentlyDeactivated()
  open fun isArchived() = status == LocationStatus.ARCHIVED
  fun isDraft() = status == LocationStatus.DRAFT
  open fun hasPendingCertificationApproval() = false
  open fun isNonResidential() = false
  open fun isResidentialRoomOrConvertedCell() = false
  open fun isActiveAndAllParentsActive() = isActive() && !hasDeactivatedParent()

  open fun isDeactivatedByParent() = isActive() && hasDeactivatedParent()
  open fun isTemporarilyDeactivated() = !isActiveAndAllParentsActive() && !isPermanentlyDeactivated() && !isDraft()
  open fun isPermanentlyDeactivated() = findArchivedLocationInHierarchy()?.status == LocationStatus.ARCHIVED
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

  fun findTopLevelLocation(): Location = getParent()?.findTopLevelLocation() ?: this

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

  protected fun getLevel(): Int {
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

  private fun getDeactivationReason() = listOfNotBlank(deactivatedReason?.description, deactivationReasonDescription).joinToString(" - ")

  private fun getLocationSummary(): LocationSummary = LocationSummary(
    id = id,
    code = getLocationCode(),
    type = getDerivedLocationType(),
    pathHierarchy = getPathHierarchy(),
    prisonId = prisonId,
    localName = getDerivedLocalName(),
    level = getLevel(),
  )

  private fun updateHierarchicalPath() {
    pathHierarchy = getHierarchicalPath()
    for (childLocation in childLocations) {
      childLocation.updateHierarchicalPath()
    }
  }

  private fun getHierarchicalPath(): String = if (getParent() == null) {
    getLocationCode()
  } else {
    "${getParent()!!.getHierarchicalPath()}-${getLocationCode()}"
  }

  private fun getActiveResidentialLocationsBelowThisLevel() = getResidentialLocationsBelowThisLevel().filter { it.isActiveAndAllParentsActive() }

  fun getResidentialLocationsBelowThisLevel() = childLocations.filterIsInstance<ResidentialLocation>()

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

  fun findLocation(key: String) = findSubLocations().find { it.getKey() == key }

  fun findSubLocations(parentsAfterChildren: Boolean = false): List<Location> {
    val subLocations = mutableListOf<Location>()

    fun traverse(location: Location) {
      if (!parentsAfterChildren && this != location) {
        subLocations.add(location)
      }
      for (childLocation in location.childLocations) {
        traverse(childLocation)
      }
      if (parentsAfterChildren && this != location) {
        subLocations.add(location)
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
    if (!isDraft()) {
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
        locationHistory
      } else {
        null
      }
    } else {
      return null
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
      code = getLocationCode(),
      status = getDerivedStatus(),
      locked = hasPendingCertificationApproval(),
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
    val withTx = history
      .asSequence()
      .mapNotNull { it.linkedTransaction }
      .distinct()
      .sortedByDescending { it.txStartTime }
      .flatMap { tx -> tx.toDto(this).toChangeHistory() }
      .toList()

    val withoutTx = history.asSequence()
      .filter { it.linkedTransaction == null && it.attributeName.display }
      .sortedByDescending { it.amendedDate }
      .map { it.toDto() }.toList()

    return withTx.plus(withoutTx)
  }

  private fun isLeafLevel() = findSubLocations().isEmpty() && !isStructural() && !isArea()

  protected fun isInHierarchy(locationToFind: Location): Boolean {
    // Walk up the hierarchy of the location to find
    var current: Location? = this
    while (current != null) {
      if (current.id == locationToFind.id) {
        return true
      }
      current = current.getParent()
    }
    return false
  }

  fun toPrisonHierarchyDto(maxLevel: Int? = null, includeInactive: Boolean = false): PrisonHierarchyDto {
    val subLocations: List<PrisonHierarchyDto> = childLocations
      .filter { (includeInactive || it.isActiveAndAllParentsActive()) && (it.isStructural() || it.isCell()) && (maxLevel == null || it.getLevel() <= maxLevel) }
      .map { it.toPrisonHierarchyDto(maxLevel = maxLevel, includeInactive) }

    return PrisonHierarchyDto(
      locationId = id!!,
      locationType = locationType,
      locationCode = getLocationCode(),
      fullLocationPath = getPathHierarchy(),
      localName = getDerivedLocalName(true),
      level = getLevel(),
      status = status,
      subLocations = subLocations.ifEmpty { null },
    )
  }

  fun toLocationGroupDto(): LocationGroupDto = LocationGroupDto(
    key = code,
    name = getDerivedLocalName(formatLocalName = true) ?: code,
    children = getActiveResidentialLocationsBelowThisLevel()
      .filter { it.isStructural() }
      .map { it.toLocationGroupDto() }
      .sortedWith(NaturalOrderComparator()),
  )

  open fun toResidentialPrisonerLocation(mapOfPrisoners: Map<String, List<Prisoner>>): ResidentialPrisonerLocation = ResidentialPrisonerLocation(
    locationId = id!!,
    key = getKey(),
    locationCode = getLocationCode(),
    locationType = getDerivedLocationType(),
    fullLocationPath = getPathHierarchy(),
    localName = if (isCell()) {
      getLocationCode()
    } else {
      formatLocation(localName ?: getLocationCode())
    },
    prisoners = mapOfPrisoners[getPathHierarchy()] ?: emptyList(),
    deactivatedReason = findDeactivatedLocationInHierarchy()?.deactivatedReason,
    status = getDerivedStatus(),
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

  fun getDerivedStatus(ignoreParentStatus: Boolean = false): DerivedLocationStatus = if ((ignoreParentStatus && isActive()) || isActiveAndAllParentsActive()) {
    if (isConvertedCell()) {
      if (hasPendingCertificationApproval()) DerivedLocationStatus.LOCKED_NON_RESIDENTIAL else DerivedLocationStatus.NON_RESIDENTIAL
    } else {
      if (hasPendingCertificationApproval()) DerivedLocationStatus.LOCKED_ACTIVE else DerivedLocationStatus.ACTIVE
    }
  } else {
    if (isPermanentlyDeactivated()) {
      DerivedLocationStatus.ARCHIVED
    } else if (isDraft()) {
      if (hasPendingCertificationApproval()) DerivedLocationStatus.LOCKED_DRAFT else DerivedLocationStatus.DRAFT
    } else {
      if (hasPendingCertificationApproval()) DerivedLocationStatus.LOCKED_INACTIVE else DerivedLocationStatus.INACTIVE
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false

    other as Location

    return getKey() == other.getKey()
  }

  override fun hashCode(): Int = getKey().hashCode()

  open fun updateLocalName(localName: String?, userOrSystemInContext: String, clock: Clock, linkedTransaction: LinkedTransaction) {
    if (hasPendingCertificationApproval()) {
      throw PendingApprovalOnLocationCannotBeUpdatedException(getKey())
    }
    if (!isCell()) {
      addHistory(
        LocationAttribute.LOCAL_NAME,
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
    if (code != null && this.getLocationCode() != code) {
      addHistory(LocationAttribute.CODE, getLocationCode(), code, userOrSystemInContext, LocalDateTime.now(clock), linkedTransaction)
      setLocationCode(code)
      this.updatedBy = userOrSystemInContext
      this.whenUpdated = LocalDateTime.now(clock)
    }
    return this
  }

  open fun isConvertedCell(): Boolean = false

  open fun sync(upsert: NomisSyncLocationRequest, clock: Clock, linkedTransaction: LinkedTransaction): Location {
    addHistory(LocationAttribute.CODE, getLocationCode(), upsert.code, upsert.lastUpdatedBy, LocalDateTime.now(clock), linkedTransaction)
    setLocationCode(upsert.code)

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
      LocationAttribute.LOCAL_NAME,
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
    upsert: NomisSyncLocationRequest,
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
    deactivatedLocations: MutableSet<Location>? = null,
    requestApproval: Boolean = false,
    reasonForChange: String? = null,
  ): Boolean {
    var dataChanged = false

    if (!isPermanentlyDeactivated()) {
      if (hasPendingCertificationApproval()) {
        throw PendingApprovalOnLocationCannotBeUpdatedException(getKey())
      }
      val amendedDate = deactivatedDate

      if (addHistory(
          LocationAttribute.STATUS,
          getDerivedStatus(ignoreParentStatus = true).description,
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
          LocationAttribute.WORKING_CAPACITY,
          getWorkingCapacityIgnoreParent().toString(),
          "0",
          userOrSystemInContext,
          amendedDate,
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
          this.proposedReactivationDate?.format(DATE_FORMAT),
          proposedReactivationDate?.format(DATE_FORMAT),
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

      val workingCapacityChange = if (this is ResidentialLocation) {
        calcWorkingCapacity()
      } else {
        0
      }
      if (isActive() || isDraft()) {
        this.status = LocationStatus.INACTIVE
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
                deactivatedLocations = deactivatedLocations,
              )
            ) {
              dataChanged = true
            }
          }
        }

        if (requestApproval) {
          if (reasonForChange == null) {
            throw ValidationException("Reason for change must be provided when requesting approval for deactivation")
          }
          requestApprovalForDeactivation(
            requestedDate = deactivatedDate,
            requestedBy = userOrSystemInContext,
            workingCapacityChange = -workingCapacityChange,
            reasonForChange = reasonForChange,
            deactivatedReason = deactivatedReason,
            deactivationReasonDescription = deactivationReasonDescription,
            proposedReactivationDate = proposedReactivationDate,
            planetFmReference = planetFmReference,
          )
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
    if (upsert.comments != null && this.comments != upsert.comments) {
      updateComments(upsert.comments, userOrSystemInContext, clock, linkedTransaction)
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
      if (hasPendingCertificationApproval()) {
        throw PendingApprovalOnLocationCannotBeUpdatedException(getKey())
      }
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
        this.proposedReactivationDate?.format(DATE_FORMAT),
        proposedReactivationDate?.format(DATE_FORMAT),
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
      if (hasPendingCertificationApproval()) {
        throw PendingApprovalOnLocationCannotBeUpdatedException(getKey())
      }
      val amendedDate = LocalDateTime.now(clock)
      addHistory(
        LocationAttribute.STATUS,
        this.getDerivedStatus().description,
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

      this.status = LocationStatus.ARCHIVED
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
          cellLocation.setCapacity(maxCapacity = 0, workingCapacity = 0, certifiedNormalAccommodation = 0, userOrSystemInContext, amendedDate = amendedDate, linkedTransaction)
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
    certifiedNormalAccommodation: Int? = null,
    reactivatedLocations: MutableSet<Location>? = null,
    amendedLocations: MutableSet<Location>? = null,
  ): Boolean {
    if (hasPendingCertificationApproval()) {
      throw PendingApprovalOnLocationCannotBeUpdatedException(getKey())
    }
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
    val amendedDate = LocalDateTime.now(clock)
    val capacityAdjusted = maxCapacity != null || workingCapacity != null
    if (this is Cell && capacityAdjusted) {
      setCapacity(
        maxCapacity = maxCapacity ?: getMaxCapacity(includePendingChange = true) ?: 0,
        workingCapacity = workingCapacity ?: getWorkingCapacity() ?: 0,
        certifiedNormalAccommodation = certifiedNormalAccommodation ?: getCertifiedNormalAccommodation() ?: 0,
        userOrSystemInContext = userOrSystemInContext,
        amendedDate = amendedDate,
        linkedTransaction = linkedTransaction,
      )
      amendedLocations?.add(this)
      amendedLocations?.addAll(this.getParentLocations())
    }

    if (isTemporarilyDeactivated()) {
      addHistory(
        LocationAttribute.STATUS,
        this.getDerivedStatus().description,
        LocationStatus.ACTIVE.description,
        userOrSystemInContext,
        amendedDate,
        linkedTransaction,
      )
      addHistory(
        LocationAttribute.PROPOSED_REACTIVATION_DATE,
        proposedReactivationDate?.format(DATE_FORMAT),
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

      this.status = LocationStatus.ACTIVE
      this.deactivatedReason = null
      this.deactivatedDate = null
      this.deactivationReasonDescription = null
      this.planetFmReference = null
      this.proposedReactivationDate = null
      this.updatedBy = userOrSystemInContext
      this.whenUpdated = amendedDate
      this.deactivatedBy = null

      if (this is Cell && !isConvertedCell()) {
        certifyCell(
          cellUpdatedBy = userOrSystemInContext,
          updatedDate = amendedDate,
          linkedTransaction = linkedTransaction,
        )
        this.residentialHousingType = this.accommodationType.mapToResidentialHousingType()
      }

      if (this is Cell && !capacityAdjusted) {
        addHistory(
          LocationAttribute.WORKING_CAPACITY,
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

  override fun toString(): String = getKey()

  private fun isCellOrConvertedCell() = this is Cell || isConvertedCell()
  open fun isCell() = false
  fun isVirtualResidentialLocation() = this is VirtualResidentialLocation
  fun isStructural() = locationType in ResidentialLocationType.entries.filter { it.structural }.map { it.baseType }
  fun isArea() = locationType in ResidentialLocationType.entries.filter { it.area }.map { it.baseType }

  open fun toLegacyDto(includeHistory: Boolean = false): LegacyLocation = LegacyLocation(
    id = id!!,
    code = getLocationCode(),
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

@Schema(description = "Location Hierarchy Summary")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class LocationSummary(
  @param:Schema(description = "ID of location", example = "c73e8ad1-191b-42b8-bfce-2550cc858dab", required = false)
  val id: UUID? = null,
  @param:Schema(
    description = "Prison ID where the location is situated",
    required = true,
    example = "MDI",
    minLength = 3,
    maxLength = 5,
    pattern = "^[A-Z]{2}I|ZZGHI$",
  )
  val prisonId: String,
  @param:Schema(description = "Code of the location", required = true, example = "001", minLength = 1)
  val code: String,
  @param:Schema(description = "Location type", example = "WING", required = true)
  val type: LocationType,
  @param:Schema(description = "Alternative description to display for location", example = "Wing A", required = false)
  val localName: String? = null,
  @param:Schema(description = "Full path of the location within the prison", example = "A-1-001", required = true)
  val pathHierarchy: String? = null,
  @param:Schema(
    description = "Current Level within hierarchy, starts at 1, e.g Wing = 1",
    examples = ["1", "2", "3"],
    required = true,
  )
  val level: Int,
)

fun listOfNotBlank(vararg elements: String?): List<String> = elements.filterNotNull().filter { it.isNotBlank() }
