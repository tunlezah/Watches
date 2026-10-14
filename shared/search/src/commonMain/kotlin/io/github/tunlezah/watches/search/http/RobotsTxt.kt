package io.github.tunlezah.watches.search.http

/**
 * A robots.txt file, matched the way RFC 9309 describes: the group naming our product token wins,
 * otherwise the `*` group applies. Within a group the longest matching rule wins, and on a tie
 * Allow beats Disallow. `*` and a trailing `$` work as in Google's and Bing's implementations.
 */
class RobotsTxt private constructor(private val groups: List<Group>) {

    private class Rule(val allow: Boolean, val pattern: String) {
        private val regex: Regex = buildString {
            append('^')
            val anchored = pattern.endsWith('$')
            val body = if (anchored) pattern.dropLast(1) else pattern
            for (part in body.split('*').withIndex()) {
                if (part.index > 0) append(".*")
                append(Regex.escape(part.value))
            }
            if (anchored) append('$')
        }.let(::Regex)

        fun matches(path: String) = regex.containsMatchIn(path)
    }

    private class Group(val agents: List<String>, val rules: List<Rule>)

    fun isAllowed(pathAndQuery: String, productToken: String): Boolean {
        val token = productToken.lowercase()
        val group = groups.firstOrNull { g -> g.agents.any { it != "*" && it == token } }
            ?: groups.firstOrNull { "*" in it.agents }
            ?: return true
        val path = pathAndQuery.ifEmpty { "/" }
        val best = group.rules
            .filter { it.pattern.isNotEmpty() && it.matches(path) }
            .maxWithOrNull(compareBy<Rule>({ it.pattern.length }, { it.allow }))
        return best?.allow ?: true
    }

    companion object {
        val AllowAll = RobotsTxt(emptyList())

        fun parse(text: String): RobotsTxt {
            val groups = mutableListOf<Group>()
            var agents = mutableListOf<String>()
            var rules = mutableListOf<Rule>()
            var lastWasAgent = false
            for (raw in text.lineSequence()) {
                val line = raw.substringBefore('#').trim()
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val field = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                when (field) {
                    "user-agent" -> {
                        if (!lastWasAgent && agents.isNotEmpty()) {
                            groups += Group(agents, rules)
                            agents = mutableListOf(); rules = mutableListOf()
                        }
                        agents += value.lowercase()
                        lastWasAgent = true
                    }
                    "allow", "disallow" -> {
                        if (agents.isNotEmpty()) rules += Rule(field == "allow", value)
                        lastWasAgent = false
                    }
                    else -> lastWasAgent = false
                }
            }
            if (agents.isNotEmpty()) groups += Group(agents, rules)
            return RobotsTxt(groups)
        }
    }
}
