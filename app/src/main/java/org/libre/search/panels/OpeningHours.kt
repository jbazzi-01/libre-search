package org.libre.search.panels

import java.time.DayOfWeek
import java.time.LocalDateTime

/** Handles the common OpenStreetMap opening_hours patterns (e.g. "Mo-Fr 08:00-19:00; Sa 09:00-12:30"). */
object OpeningHours {
    private val dayCodes = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

    /** Returns (openNow, today's hours text) or null if the format isn't understood. */
    fun status(raw: String, now: LocalDateTime = LocalDateTime.now()): Pair<Boolean, String>? {
        val s = raw.trim()
        if (s == "24/7") return true to "Open 24 hours"
        val todayIdx = now.dayOfWeek.value - 1 // Monday = 0
        val todayRanges = mutableListOf<Pair<Int, Int>>()
        var understood = false
        var closedToday = false
        for (rule in s.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
            if (rule.startsWith("PH")) continue
            val m = Regex("^([A-Za-z,\\- ]+?)\\s+(.+)$").find(rule)
            val daysPart: String
            val timesPart: String
            if (m != null && m.groupValues[1].split(',', '-').all { it.trim() in dayCodes }) {
                daysPart = m.groupValues[1]
                timesPart = m.groupValues[2]
            } else if (Regex("^\\d").containsMatchIn(rule)) {
                daysPart = "Mo-Su"
                timesPart = rule
            } else {
                continue
            }
            val days = expandDays(daysPart) ?: continue
            understood = true
            if (todayIdx !in days) continue
            if (timesPart.trim().equals("off", true) || timesPart.trim().equals("closed", true)) {
                closedToday = true; todayRanges.clear(); continue
            }
            closedToday = false
            todayRanges.clear()
            for (t in timesPart.split(',')) {
                val tm = Regex("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})").find(t) ?: continue
                val a = tm.groupValues[1].toInt() * 60 + tm.groupValues[2].toInt()
                var b = tm.groupValues[3].toInt() * 60 + tm.groupValues[4].toInt()
                if (b <= a) b += 24 * 60
                todayRanges += a to b
            }
        }
        if (!understood) return null
        if (closedToday || todayRanges.isEmpty()) return false to "Closed today"
        val minutes = now.hour * 60 + now.minute
        val open = todayRanges.any { minutes in it.first until it.second }
        val text = todayRanges.joinToString(", ") { "${fmt(it.first)}–${fmt(it.second)}" }
        val next = if (open) {
            val r = todayRanges.first { minutes in it.first until it.second }
            "Closes ${fmt(r.second)}"
        } else {
            todayRanges.firstOrNull { it.first > minutes }?.let { "Opens ${fmt(it.first)}" } ?: "Closed for today"
        }
        return open to "$next · $text"
    }

    private fun fmt(m: Int) = "%02d:%02d".format((m / 60) % 24, m % 60)

    private fun expandDays(part: String): Set<Int>? {
        val out = HashSet<Int>()
        for (seg in part.split(',').map { it.trim() }) {
            if ('-' in seg) {
                val (a, b) = seg.split('-').map { it.trim() }
                val i = dayCodes.indexOf(a); val j = dayCodes.indexOf(b)
                if (i < 0 || j < 0) return null
                var k = i
                while (true) { out += k; if (k == j) break; k = (k + 1) % 7 }
            } else {
                val i = dayCodes.indexOf(seg)
                if (i < 0) return null
                out += i
            }
        }
        return out
    }

    @Suppress("unused")
    private fun dayOf(d: DayOfWeek) = dayCodes[d.value - 1]
}
