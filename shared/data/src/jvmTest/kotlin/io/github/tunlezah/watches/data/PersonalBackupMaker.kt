package io.github.tunlezah.watches.data

import io.github.tunlezah.watches.core.Ids
import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.model.DocumentKind
import io.github.tunlezah.watches.core.model.MovementType
import io.github.tunlezah.watches.core.model.OtherSpec
import io.github.tunlezah.watches.core.model.PhotoKind
import io.github.tunlezah.watches.core.model.Provenance
import io.github.tunlezah.watches.core.model.Watch
import io.github.tunlezah.watches.core.model.WatchStatus
import io.github.tunlezah.watches.core.model.keyedOf
import io.github.tunlezah.watches.data.backup.AppInfo
import io.github.tunlezah.watches.data.backup.BackupRepository
import io.github.tunlezah.watches.data.files.FileStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test

/**
 * Builds a collection from a catalogue file through the app's own repositories (watches, photos,
 * documents) and writes it out with the app's own backup exporter, so the archive is exactly what
 * the app would write. Runs only when WATCHES_CATALOGUE and WATCHES_BACKUP_OUT are set.
 *
 * Watches, photos and documents can carry the IDs an earlier backup gave them, so a corrected
 * backup restored with Merge replaces the earlier rows instead of adding to them.
 */
class PersonalBackupMaker {

    @Test fun make() = runBlocking<Unit> {
        val catalogue = System.getenv("WATCHES_CATALOGUE")?.takeIf { it.isNotBlank() } ?: return@runBlocking
        val out = System.getenv("WATCHES_BACKUP_OUT")?.takeIf { it.isNotBlank() } ?: return@runBlocking
        val root = Json.parseToJsonElement(FileSystem.SYSTEM.read(catalogue.toPath()) { readUtf8() }).jsonObject

        val dir = newTestDir()
        val db = openTestDatabase(dir / "watches.db")
        try {
            val clock = TimeSource.System
            val files = FileStore(dir / "files")
            val processor = testImageProcessor()
            val watches = WatchRepository(db, files, clock)
            val photos = PhotoRepository(db, files, processor, clock)
            val documents = DocumentRepository(db, files, processor, watches, clock)

            for (entry in root.getValue("watches").jsonArray.map { it.jsonObject }) {
                val f = entry.getValue("fields").jsonObject
                fun s(k: String) = f[k]?.jsonPrimitive?.content?.trim()?.ifBlank { null }
                fun d(k: String) = f[k]?.jsonPrimitive?.doubleOrNull
                fun i(k: String) = f[k]?.jsonPrimitive?.intOrNull
                fun b(k: String) = f[k]?.jsonPrimitive?.booleanOrNull
                val now = clock.now()
                val watch = Watch(
                    id = "", createdAt = now, updatedAt = now, status = WatchStatus.OWNED,
                    brand = s("brand"), collection = s("collection"), modelName = s("model_name"), reference = s("reference"),
                    nickname = s("nickname"), yearFrom = i("year_from"), yearTo = i("year_to"), limitedTotal = i("limited_total"),
                    caseMaterial = s("case_material"), diameterMm = d("diameter_mm"), thicknessMm = d("thickness_mm"),
                    lugToLugMm = d("lug_to_lug_mm"), lugWidthMm = d("lug_width_mm"), bezel = s("bezel"), crystal = s("crystal"),
                    caseBack = s("case_back"), crown = s("crown"), waterResistanceM = i("water_resistance_m"),
                    dialColour = s("dial_colour"), dialFinish = s("dial_finish"), indices = s("indices"), hands = s("hands"), lume = s("lume"),
                    caliber = s("caliber"), baseCaliber = s("base_caliber"),
                    movementType = s("movement_type")?.let { k -> keyedOf<MovementType>(k) ?: error("${entry["key"]}: unknown movement type $k") },
                    jewels = i("jewels"), frequencyBph = i("frequency_bph"), powerReserveH = d("power_reserve_h"),
                    hacking = b("hacking"), handWinding = b("hand_winding"),
                    complications = f["complications"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet(),
                    strapType = s("strap_type"), strapMaterial = s("strap_material"), clasp = s("clasp"), fittedStrap = s("fitted_strap"),
                    privateNotes = s("private_notes"), story = s("story"),
                    serviceIntervalMonths = i("service_interval_months"),
                )
                val sourced = (entry["sourced"]?.jsonObject ?: JsonObject(emptyMap())).mapValues { (key, v) ->
                    val o = v.jsonObject
                    Provenance(key, o.getValue("source").jsonPrimitive.content, o["url"]?.jsonPrimitive?.content, now)
                }
                val other = entry["other_specs"]?.jsonArray?.map { it.jsonObject }?.map { o ->
                    OtherSpec("", o.getValue("label").jsonPrimitive.content, o.getValue("value").jsonPrimitive.content,
                        o["source"]?.jsonPrimitive?.content, o["url"]?.jsonPrimitive?.content, if (o["source"] != null) now else null)
                }.orEmpty()
                val id = entry.getValue("id").jsonPrimitive.content
                val created = watches.create(watch, id = id, sourced = sourced, otherSpecs = other.mapIndexed { n, o -> o.copy(id = "$id-spec-$n") })

                // A photo is a path, or an object with its kind, credit, ID and whether it is the cover. Giving the
                // IDs an earlier backup used makes a Merge replace those photos instead of adding beside them.
                for (photo in entry["photos"]?.jsonArray.orEmpty()) {
                    val p = photo as? JsonObject
                    fun str(k: String) = p?.get(k)?.jsonPrimitive?.content
                    val file = p?.getValue("file")?.jsonPrimitive?.content ?: photo.jsonPrimitive.content
                    val kind = str("kind")?.let { k -> keyedOf<PhotoKind>(k) ?: error("unknown photo kind $k") } ?: PhotoKind.USER
                    val bytes = FileSystem.SYSTEM.read(file.toPath()) { readByteArray() }
                    val added = photos.add(created.id, bytes, kind, str("credit"), str("credit_url"), str("id") ?: Ids.new())
                    // A reused ID would put this original at the earlier backup's file name with other contents,
                    // which app versions before 0.8.0 can't restore. Give it a tagged name, as a re-made original gets.
                    if (str("id") != null) {
                        val from = added.originalFile
                        val dot = from.lastIndexOf('.')
                        val to = "${from.substring(0, dot)}-${Ids.new().take(6)}${from.substring(dot)}"
                        files.fileSystem.atomicMove(files.resolve(from), files.resolve(to))
                        val photoDao = db.photoDao()
                        photoDao.upsert(checkNotNull(photoDao.get(added.id)).copy(originalFile = to))
                    }
                    if (p?.get("cover")?.jsonPrimitive?.booleanOrNull == true) photos.setCover(created.id, added.id)
                }
                // Documents can be given IDs too, for the same reason. The repository picks its own, so the row is
                // moved to the given ID afterwards; its file stays where the repository put it.
                val documentDao = db.documentDao()
                for (doc in entry["documents"]?.jsonArray.orEmpty().map { it.jsonObject }) {
                    val kind = keyedOf<DocumentKind>(doc.getValue("kind").jsonPrimitive.content) ?: error("unknown document kind")
                    val bytes = FileSystem.SYSTEM.read(doc.getValue("file").jsonPrimitive.content.toPath()) { readByteArray() }
                    val added = documents.add(created.id, bytes, kind, doc.getValue("title").jsonPrimitive.content)
                    val wanted = doc["id"]?.jsonPrimitive?.content
                    if (wanted != null && wanted != added.id) {
                        val row = checkNotNull(documentDao.get(added.id))
                        documentDao.upsert(row.copy(id = wanted))
                        documentDao.delete(added.id)
                    }
                }
                println("PersonalBackupMaker: ${entry["key"]} ${created.title}")
            }

            val backup = BackupRepository(db, files, watches, photos, AppInfo("0.8.0", root["platform"]?.jsonPrimitive?.content ?: "collection import"), clock)
            val file = out.toPath()
            file.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
            val written = backup.export(FileSystem.SYSTEM.sink(file))
            check(written.missingFiles == 0) { "${written.missingFiles} files were missing" }
            println("PersonalBackupMaker: wrote $out, ${written.bytes} bytes: ${written.counts}")
        } finally {
            db.close()
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }
}
