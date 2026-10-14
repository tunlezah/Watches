package io.github.tunlezah.watches.search.sources

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Entities
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Small, forgiving helpers shared by the parsers. Inputs are untrusted pages: nothing here executes them. */
internal object Parsing {

    /** Visible text of an element without glossary pop-overs, buttons, icons or scripts. */
    fun visibleText(element: Element): String {
        val copy = element.clone()
        copy.select("[popover], button, script, style, template, svg, i, noscript").remove()
        return squash(copy.text())
    }

    fun squash(s: String): String = s.replace(Regex("\\s+"), " ").trim()

    /** Null for empty and placeholder values ("—", "-", "n/a"). */
    fun present(s: String?): String? = s?.let(::squash)?.takeUnless { it.isEmpty() || it == "—" || it == "-" || it == "–" || it.equals("n/a", true) }

    fun htmlToText(html: String): String = squash(Ksoup.parse(html).text())

    fun unescape(s: String): String = Entities.unescape(s)

    fun query(s: String): String = s.encodeURLParameter()

    // JSON navigation that returns null instead of throwing on an unexpected shape.
    operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
    operator fun JsonElement?.get(index: Int): JsonElement? = (this as? JsonArray)?.getOrNull(index)
    val JsonElement?.str: String? get() = (this as? JsonPrimitive)?.contentOrNull
    val JsonElement?.array: List<JsonElement> get() = (this as? JsonArray).orEmpty()
    val JsonElement?.obj: Map<String, JsonElement> get() = (this as? JsonObject).orEmpty()

    /**
     * Unescapes the body of a single-quoted JavaScript string literal, as found in
     * `x-data="{ images: JSON.parse('…') }"`. One left-to-right pass, so `\\u0026` stays `&`.
     */
    fun jsUnescape(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) { append(c); i++; continue }
            val e = s[i + 1]
            when (e) {
                'u' -> { s.substring(i + 2, minOf(i + 6, s.length)).toIntOrNull(16)?.let { append(it.toChar()); i += 6 } ?: run { append(e); i += 2 } }
                'x' -> { s.substring(i + 2, minOf(i + 4, s.length)).toIntOrNull(16)?.let { append(it.toChar()); i += 4 } ?: run { append(e); i += 2 } }
                'n' -> { append('\n'); i += 2 }
                't' -> { append('\t'); i += 2 }
                'r' -> { append('\r'); i += 2 }
                else -> { append(e); i += 2 }
            }
        }
    }
}
