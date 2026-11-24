package io.github.tunlezah.watches.data

import io.github.tunlezah.watches.core.TimeSource
import io.github.tunlezah.watches.core.model.DocumentKind
import io.github.tunlezah.watches.core.model.MovementType
import io.github.tunlezah.watches.core.model.OtherSpec
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

                for (photo in entry["photos"]?.jsonArray.orEmpty()) {
                    photos.add(created.id, FileSystem.SYSTEM.read(photo.jsonPrimitive.content.toPath()) { readByteArray() })
                }
                for (doc in entry["documents"]?.jsonArray.orEmpty().map { it.jsonObject }) {
                    val kind = keyedOf<DocumentKind>(doc.getValue("kind").jsonPrimitive.content) ?: error("unknown document kind")
                    val bytes = FileSystem.SYSTEM.read(doc.getValue("file").jsonPrimitive.content.toPath()) { readByteArray() }
                    documents.add(created.id, bytes, kind, doc.getValue("title").jsonPrimitive.content)
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
