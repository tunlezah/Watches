package io.github.tunlezah.watches.data.db

import io.github.tunlezah.watches.core.model.BoxItem
import io.github.tunlezah.watches.core.model.BoxItemState
import io.github.tunlezah.watches.core.model.BoxPapersItem
import io.github.tunlezah.watches.core.model.Condition
import io.github.tunlezah.watches.core.model.Currencies
import io.github.tunlezah.watches.core.model.DocumentKind
import io.github.tunlezah.watches.core.model.ServiceEntry
import io.github.tunlezah.watches.core.model.ServiceWork
import io.github.tunlezah.watches.core.model.WatchDocument
import io.github.tunlezah.watches.core.model.Money
import io.github.tunlezah.watches.core.model.MovementType
import io.github.tunlezah.watches.core.model.NormalizedRect
import io.github.tunlezah.watches.core.model.OtherSpec
import io.github.tunlezah.watches.core.model.Photo
import io.github.tunlezah.watches.core.model.PhotoKind
import io.github.tunlezah.watches.core.model.Provenance
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.model.WatchStatus
import io.github.tunlezah.watches.core.model.WatchSummary
import io.github.tunlezah.watches.core.model.keyedOf
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

private fun Long.toInstant() = Instant.fromEpochMilliseconds(this)
private fun String.toDate(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

fun WatchEntity.toModel(complications: Collection<String>): Watch = Watch(
    id = id,
    status = keyedOf<WatchStatus>(status) ?: WatchStatus.OWNED,
    archivedAt = archivedAt?.toInstant(),
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
    coverPhotoId = coverPhotoId,
    brand = brand,
    collection = collection,
    modelName = modelName,
    reference = reference,
    nickname = nickname,
    yearFrom = yearFrom,
    yearTo = yearTo,
    limitedNumber = limitedNumber,
    limitedTotal = limitedTotal,
    caseMaterial = caseMaterial,
    diameterMm = diameterMm,
    thicknessMm = thicknessMm,
    lugToLugMm = lugToLugMm,
    lugWidthMm = lugWidthMm,
    bezel = bezel,
    crystal = crystal,
    caseBack = caseBack,
    crown = crown,
    waterResistanceM = waterResistanceM,
    dialColour = dialColour,
    dialFinish = dialFinish,
    indices = indices,
    hands = hands,
    lume = lume,
    caliber = caliber,
    baseCaliber = baseCaliber,
    movementType = keyedOf<MovementType>(movementType),
    jewels = jewels,
    frequencyBph = frequencyBph,
    powerReserveH = powerReserveH,
    hacking = hacking,
    handWinding = handWinding,
    complications = complications.toSet(),
    strapType = strapType,
    strapMaterial = strapMaterial,
    clasp = clasp,
    fittedStrap = fittedStrap,
    acquiredOn = acquiredOn?.toDate(),
    acquiredFrom = acquiredFrom,
    pricePaid = if (priceMinor != null && priceCurrency != null) Money(priceMinor, priceCurrency) else null,
    condition = keyedOf<Condition>(condition),
    serialNumber = serialNumber,
    privateNotes = privateNotes,
    story = story,
    serviceIntervalMonths = serviceIntervalMonths,
    serviceReminder = serviceReminder,
    warrantyStart = warrantyStart?.toDate(),
    warrantyMonths = warrantyMonths,
)

fun Watch.toEntity(): WatchEntity = WatchEntity(
    id = id,
    status = status.key,
    archivedAt = archivedAt?.toEpochMilliseconds(),
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
    coverPhotoId = coverPhotoId,
    brand = brand,
    collection = collection,
    modelName = modelName,
    reference = reference,
    nickname = nickname,
    yearFrom = yearFrom,
    yearTo = yearTo,
    limitedNumber = limitedNumber,
    limitedTotal = limitedTotal,
    caseMaterial = caseMaterial,
    diameterMm = diameterMm,
    thicknessMm = thicknessMm,
    lugToLugMm = lugToLugMm,
    lugWidthMm = lugWidthMm,
    bezel = bezel,
    crystal = crystal,
    caseBack = caseBack,
    crown = crown,
    waterResistanceM = waterResistanceM,
    dialColour = dialColour,
    dialFinish = dialFinish,
    indices = indices,
    hands = hands,
    lume = lume,
    caliber = caliber,
    baseCaliber = baseCaliber,
    movementType = movementType?.key,
    jewels = jewels,
    frequencyBph = frequencyBph,
    powerReserveH = powerReserveH,
    hacking = hacking,
    handWinding = handWinding,
    strapType = strapType,
    strapMaterial = strapMaterial,
    clasp = clasp,
    fittedStrap = fittedStrap,
    acquiredOn = acquiredOn?.toString(),
    acquiredFrom = acquiredFrom,
    priceMinor = pricePaid?.amountMinor,
    priceCurrency = pricePaid?.currency,
    condition = condition?.key,
    serialNumber = serialNumber,
    privateNotes = privateNotes,
    story = story,
    serviceIntervalMonths = serviceIntervalMonths,
    serviceReminder = serviceReminder,
    warrantyStart = warrantyStart?.toString(),
    warrantyMonths = warrantyMonths,
)

fun WatchSummaryRow.toModel(): WatchSummary = WatchSummary(
    id = id,
    brand = brand,
    modelName = modelName,
    collection = collection,
    reference = reference,
    nickname = nickname,
    status = keyedOf<WatchStatus>(status) ?: WatchStatus.OWNED,
    movementType = keyedOf<MovementType>(movementType),
    diameterMm = diameterMm,
    acquiredOn = acquiredOn?.toDate(),
    createdAt = createdAt.toInstant(),
    archived = archivedAt != null,
    complications = complications?.split('|')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
    coverThumb = coverThumb,
    coverDisplay = coverDisplay,
    dialColour = dialColour,
    caseMaterial = caseMaterial,
)

fun PhotoEntity.toModel(): Photo {
    val l = cropLeft
    val t = cropTop
    val r = cropRight
    val b = cropBottom
    return Photo(
    id = id,
    watchId = watchId,
    position = position,
    originalFile = originalFile,
    displayFile = displayFile,
    thumbFile = thumbFile,
    width = width,
    height = height,
    crop = if (l != null && t != null && r != null && b != null) runCatching { NormalizedRect(l, t, r, b) }.getOrNull() else null,
    kind = keyedOf<PhotoKind>(kind) ?: PhotoKind.USER,
    attribution = attribution,
    attributionUrl = attributionUrl,
    addedAt = addedAt.toInstant(),
)
}

fun Photo.toEntity(): PhotoEntity = PhotoEntity(
    id = id,
    watchId = watchId,
    position = position,
    originalFile = originalFile,
    displayFile = displayFile,
    thumbFile = thumbFile,
    width = width,
    height = height,
    cropLeft = crop?.left,
    cropTop = crop?.top,
    cropRight = crop?.right,
    cropBottom = crop?.bottom,
    kind = kind.key,
    attribution = attribution,
    attributionUrl = attributionUrl,
    addedAt = addedAt.toEpochMilliseconds(),
)

fun FieldProvenanceEntity.toModel() = Provenance(fieldKey, source, sourceUrl, fetchedAt?.toInstant(), userEdited)

fun Provenance.toEntity(watchId: String) =
    FieldProvenanceEntity(watchId, fieldKey, source, sourceUrl, fetchedAt?.toEpochMilliseconds(), userEdited)

fun OtherSpecEntity.toModel() = OtherSpec(id, label, value, source, sourceUrl, fetchedAt?.toInstant(), position)

fun OtherSpec.toEntity(watchId: String) =
    OtherSpecEntity(id, watchId, label, value, source, sourceUrl, fetchedAt?.toEpochMilliseconds(), position)

fun ServiceEntryEntity.toModel() = ServiceEntry(
    id = id,
    watchId = watchId,
    servicedOn = servicedOn.toDate() ?: LocalDate(1970, 1, 1),
    servicedBy = servicedBy,
    work = work.split(',').mapNotNull { keyedOf<ServiceWork>(it.trim()) }.toSet(),
    cost = costMinor?.let { Money(it, costCurrency ?: Currencies.DEFAULT) },
    warrantyMonths = warrantyMonths,
    notes = notes,
    createdAt = createdAt.toInstant(),
    updatedAt = updatedAt.toInstant(),
)

fun ServiceEntry.toEntity() = ServiceEntryEntity(
    id = id,
    watchId = watchId,
    servicedOn = servicedOn.toString(),
    servicedBy = servicedBy?.trim()?.ifEmpty { null },
    work = ServiceWork.entries.filter { it in work }.joinToString(",") { it.key },
    costMinor = cost?.amountMinor,
    costCurrency = cost?.currency,
    warrantyMonths = warrantyMonths,
    notes = notes?.trim()?.ifEmpty { null },
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
)

fun BoxPapersItemEntity.toModel(): BoxPapersItem? {
    val i = keyedOf<BoxItem>(item) ?: return null
    val st = keyedOf<BoxItemState>(state) ?: return null
    return BoxPapersItem(i, st, note)
}

fun BoxPapersItem.toEntity(watchId: String) = BoxPapersItemEntity(watchId, item.key, state.key, null, note)

fun DocumentEntity.toModel() = WatchDocument(
    id = id,
    watchId = watchId,
    serviceEntryId = serviceEntryId,
    kind = keyedOf<DocumentKind>(kind) ?: DocumentKind.OTHER,
    title = title,
    file = file,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    addedAt = addedAt.toInstant(),
)

fun WatchDocument.toEntity() = DocumentEntity(id, watchId, serviceEntryId, kind.key, title, file, mimeType, sizeBytes, addedAt.toEpochMilliseconds())
