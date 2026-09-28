package org.libre.search.search

import org.libre.search.core.Text
import java.util.Locale
import kotlin.math.PI
import kotlin.math.E
import kotlin.math.abs

/** Works out what kind of Google-style panels a query deserves. */
object QueryIntent {

    private val weatherWords = Regex(
        "(?U)\\b(weather|meteo|météo|forecast|previsions?|prévisions?|temperature|température|quel temps|what's the weather|will it rain|va-t-il pleuvoir)\\b",
        RegexOption.IGNORE_CASE
    )
    private val weatherFiller = Regex(
        "(?U)\\b(in|at|for|a|à|au|aux|de|du|des|la|le|les|today|tonight|tomorrow|this|week|weekend|aujourd'hui|aujourdhui|demain|ce|cette|semaine|soir|now|maintenant|current|actuelle|what's|the|is|quel|temps|fait|il|va|t|pleuvoir|rain|will|it)\\b",
        RegexOption.IGNORE_CASE
    )

    fun weatherPlace(q: String): String? {
        if (!weatherWords.containsMatchIn(q)) return null
        val rest = q.replace(weatherWords, " ").replace(weatherFiller, " ")
            .replace(Regex("[?!.,;:\\-']"), " ").replace(Regex("\\s+"), " ").trim()
        return rest // empty string means "here"
    }

    private val nearWords = Regex(
        "(?U)\\b(near me|nearby|near here|around me|close to me|près de moi|pres de moi|autour de moi|à proximité|a proximite|proche|dans le coin|open now|ouvert maintenant)\\b",
        RegexOption.IGNORE_CASE
    )

    /** Keyword -> Overpass tag filter. */
    private val categories: List<Pair<Regex, String>> = listOf(
        "pizza|pizzeria|pizzas" to """["cuisine"~"pizza",i]""",
        "sushi|japanese restaurant|restaurant japonais|ramen" to """["cuisine"~"sushi|japanese|ramen",i]""",
        "burger|burgers|hamburger" to """["cuisine"~"burger",i]""",
        "kebab|kebabs|döner|doner|tacos" to """["cuisine"~"kebab|turkish|tacos",i]""",
        "chinese|chinois|restaurant chinois" to """["cuisine"~"chinese",i]""",
        "indian|indien|restaurant indien" to """["cuisine"~"indian",i]""",
        "vegan|végétarien|vegetarian|vegetarien" to """["diet:vegan"~"yes|only"]""",
        "restaurant|restaurants|resto|restos|where to eat|où manger" to """["amenity"="restaurant"]""",
        "fast food|fastfood|snack" to """["amenity"="fast_food"]""",
        "café|cafe|cafés|cafes|coffee|coffee shop|salon de thé" to """["amenity"="cafe"]""",
        "bar|bars|pub|pubs" to """["amenity"~"^(bar|pub)$"]""",
        "boulangerie|boulangeries|bakery|bakeries|patisserie|pâtisserie" to """["shop"~"^(bakery|pastry)$"]""",
        "pharmacie|pharmacies|pharmacy|pharmacies|chemist" to """["amenity"="pharmacy"]""",
        "supermarché|supermarche|supermarket|supermarkets|grocery|épicerie|epicerie|supérette" to """["shop"~"^(supermarket|convenience|greengrocer)$"]""",
        "hôtel|hotel|hotels|hôtels|hostel|auberge" to """["tourism"~"^(hotel|hostel|guest_house)$"]""",
        "coiffeur|coiffeurs|hairdresser|barber|barbier" to """["shop"="hairdresser"]""",
        "cinéma|cinema|cinemas|cinémas|movie theater" to """["amenity"="cinema"]""",
        "salle de sport|gym|gyms|fitness" to """["leisure"="fitness_centre"]""",
        "banque|bank|banks|atm|distributeur" to """["amenity"~"^(bank|atm)$"]""",
        "poste|post office|la poste" to """["amenity"="post_office"]""",
        "médecin|medecin|doctor|doctors|généraliste" to """["amenity"="doctors"]""",
        "dentiste|dentist" to """["amenity"="dentist"]""",
        "hôpital|hopital|hospital|urgences|emergency room" to """["amenity"="hospital"]""",
        "garage|mécanicien|mecanicien|car repair" to """["shop"="car_repair"]""",
        "station essence|station service|gas station|petrol station|fuel" to """["amenity"="fuel"]""",
        "parc|parcs|park|parks|jardin" to """["leisure"="park"]""",
        "musée|musee|museum|museums" to """["tourism"="museum"]""",
        "bibliothèque|bibliotheque|library|médiathèque|mediatheque" to """["amenity"="library"]""",
        "librairie|librairies|bookshop|bookstore" to """["shop"="books"]""",
        "fleuriste|florist" to """["shop"="florist"]""",
        "boucherie|butcher|boucher" to """["shop"="butcher"]""",
        "vétérinaire|veterinaire|vet|veterinary" to """["amenity"="veterinary"]""",
        "laverie|laundromat|laundry|pressing" to """["shop"~"^(laundry|dry_cleaning)$"]""",
        "tabac|bureau de tabac" to """["shop"="tobacco"]""",
        "bricolage|hardware store|quincaillerie" to """["shop"~"^(hardware|doityourself)$"]""",
        "vélo|velo|bike shop|bicycle" to """["shop"="bicycle"]""",
        "piscine|swimming pool" to """["leisure"="sports_centre"]["sport"="swimming"]""",
        "mairie|town hall|city hall" to """["amenity"="townhall"]""",
        "police|commissariat|gendarmerie" to """["amenity"="police"]""",
        "école|ecole|school" to """["amenity"="school"]""",
        "marché|marche|market" to """["amenity"="marketplace"]""",
        "glacier|ice cream|glace" to """["amenity"="ice_cream"]""",
        "opticien|optician" to """["shop"="optician"]""",
        "friperie|second hand|thrift|recyclerie|ressourcerie" to """["shop"~"^(second_hand|charity)$"]""",
    ).map { (k, v) -> Regex("(?U)\\b($k)\\b", RegexOption.IGNORE_CASE) to v }

    data class Local(val tag: String?, val nameQuery: String?, val explicit: Boolean)

    /** Returns a local-search plan, or null when the query clearly isn't about places. */
    private val howTo = Regex(
        "(?U)\\b(how|recipe|recette|comment|history|histoire|what|why|pourquoi|definition|définition|meaning|wiki|song|chanson|lyrics)\\b",
        RegexOption.IGNORE_CASE
    )

    fun local(q: String): Local? {
        val explicit = nearWords.containsMatchIn(q)
        if (!explicit && howTo.containsMatchIn(q)) return null
        val cat = categories.firstOrNull { it.first.containsMatchIn(q) }
        if (cat != null) return Local(cat.second, null, explicit)
        if (explicit) {
            val name = q.replace(nearWords, " ").trim()
            if (name.isNotBlank()) return Local(null, name, true)
        }
        return null
    }

    private val screenWords = Regex(
        "(?U)\\b(film|movie|cast|casting|acteurs?|actors?|actrices?|director|réalisateur|realisateur|series|série|serie|saison|season|trailer|bande annonce|streaming|showtimes|séances)\\b",
        RegexOption.IGNORE_CASE
    )

    fun mentionsScreen(q: String) = screenWords.containsMatchIn(q)
    fun stripScreenWords(q: String) = q.replace(screenWords, " ").replace(Regex("\\s+"), " ").trim()

    private val questionStart = Regex(
        "^(how|why|what is the best|comment|pourquoi|est-ce que|can i|should i|where to buy|best|meilleur|top \\d+|vs|versus)\\b",
        RegexOption.IGNORE_CASE
    )

    /** Queries that ask a how-to question rarely deserve an entity panel. */
    fun entityCandidate(q: String): Boolean {
        val words = Text.normalize(q).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 7) return false
        if (questionStart.containsMatchIn(q.trim())) return false
        return true
    }

    private val shopWords = Regex(
        "(?U)\\b(buy|acheter|price|prix|cheap|pas cher|deal|promo|review|avis|test|comparatif|best|meilleur)\\b",
        RegexOption.IGNORE_CASE
    )

    fun shoppingLike(q: String) = shopWords.containsMatchIn(q)
}

/** Safe arithmetic evaluator plus a few unit conversions, like Google's calculator box. */
object Calculator {

    data class Answer(val expression: String, val result: String)

    fun evaluate(raw: String): Answer? {
        conversion(raw)?.let { return it }
        val q = raw.trim().removeSuffix("=").trim()
        if (q.length < 2) return null
        if (!Regex("^[0-9\\s+\\-*/^().,%x×÷πe√a-z]+$", RegexOption.IGNORE_CASE).matches(q)) return null
        if (!Regex("[0-9π]").containsMatchIn(q)) return null
        val hasOp = Regex("[+\\-*/^%×÷x√]|sqrt|sin|cos|tan|log|ln").containsMatchIn(q)
        if (!hasOp) return null
        val letters = Regex("[a-z]+", RegexOption.IGNORE_CASE).findAll(q).map { it.value.lowercase() }
            .filterNot { it in setOf("x", "e", "pi", "sqrt", "sin", "cos", "tan", "log", "ln", "abs") }
            .toList()
        if (letters.isNotEmpty()) return null
        return try {
            val v = Parser(
                q.replace('×', '*').replace('÷', '/').replace(Regex("(?<=\\d),(?=\\d)"), ".")
                    .replace(Regex("(?<=[\\d)\\s])x(?=[\\s\\d(])"), "*")
            ).parse()
            if (v.isNaN() || v.isInfinite()) null else Answer("$q =", format(v))
        } catch (_: Exception) {
            null
        }
    }

    fun format(v: Double): String {
        if (abs(v) >= 1e15 || (abs(v) < 1e-6 && v != 0.0)) return String.format(Locale.US, "%.6e", v)
        val rounded = Math.round(v * 1e10) / 1e10
        return if (rounded == Math.floor(rounded)) String.format(Locale.US, "%,.0f", rounded).replace(",", " ")
        else rounded.toBigDecimal().stripTrailingZeros().toPlainString()
    }

    private class Parser(val s: String) {
        var pos = -1
        var ch = 0

        fun next() { ch = if (++pos < s.length) s[pos].code else -1 }
        fun eat(c: Int): Boolean {
            while (ch == ' '.code) next()
            if (ch == c) { next(); return true }
            return false
        }

        fun parse(): Double {
            next()
            val x = expr()
            if (pos < s.length) throw IllegalArgumentException("Unexpected: " + ch.toChar())
            return x
        }

        fun expr(): Double {
            var x = term()
            while (true) {
                x = when {
                    eat('+'.code) -> x + term()
                    eat('-'.code) -> x - term()
                    else -> return x
                }
            }
        }

        fun term(): Double {
            var x = factor()
            while (true) {
                x = when {
                    eat('*'.code) -> x * factor()
                    eat('/'.code) -> x / factor()
                    eat('%'.code) -> {
                        // "50 % of" is not supported, "7 % 3" is modulo, "20%" alone is percent
                        while (ch == ' '.code) next()
                        if (ch == -1 || ch == ')'.code || ch == '+'.code || ch == '-'.code) x / 100.0 else x % factor()
                    }
                    else -> return x
                }
            }
        }

        fun factor(): Double {
            if (eat('+'.code)) return factor()
            if (eat('-'.code)) return -factor()
            if (eat('√'.code)) return Math.sqrt(factor())
            var x: Double
            val start = pos
            if (eat('('.code)) {
                x = expr()
                eat(')'.code)
            } else if ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) {
                while ((ch >= '0'.code && ch <= '9'.code) || ch == '.'.code) next()
                x = s.substring(start, pos).toDouble()
            } else if (ch == 'π'.code) {
                next(); x = PI
            } else if ((ch >= 'a'.code && ch <= 'z'.code) || (ch >= 'A'.code && ch <= 'Z'.code)) {
                while ((ch >= 'a'.code && ch <= 'z'.code) || (ch >= 'A'.code && ch <= 'Z'.code)) next()
                val name = s.substring(start, pos).lowercase()
                x = when (name) {
                    "pi" -> PI
                    "e" -> E
                    else -> {
                        val arg = factor()
                        when (name) {
                            "sqrt" -> Math.sqrt(arg)
                            "sin" -> Math.sin(Math.toRadians(arg))
                            "cos" -> Math.cos(Math.toRadians(arg))
                            "tan" -> Math.tan(Math.toRadians(arg))
                            "log" -> Math.log10(arg)
                            "ln" -> Math.log(arg)
                            "abs" -> abs(arg)
                            else -> throw IllegalArgumentException("Unknown function $name")
                        }
                    }
                }
            } else {
                throw IllegalArgumentException("Unexpected")
            }
            if (eat('^'.code)) x = Math.pow(x, factor())
            return x
        }
    }

    private data class Unit(val names: List<String>, val kind: String, val factor: Double)

    private val units = listOf(
        Unit(listOf("km", "kilometer", "kilometers", "kilomètre", "kilomètres", "kilometre", "kilometres"), "len", 1000.0),
        Unit(listOf("m", "meter", "meters", "mètre", "mètres", "metre", "metres"), "len", 1.0),
        Unit(listOf("cm", "centimeter", "centimeters", "centimètre", "centimètres"), "len", 0.01),
        Unit(listOf("mm", "millimeter", "millimeters", "millimètre", "millimètres"), "len", 0.001),
        Unit(listOf("mi", "mile", "miles"), "len", 1609.344),
        Unit(listOf("ft", "foot", "feet", "pied", "pieds"), "len", 0.3048),
        Unit(listOf("in", "inch", "inches", "pouce", "pouces"), "len", 0.0254),
        Unit(listOf("yd", "yard", "yards"), "len", 0.9144),
        Unit(listOf("kg", "kilogram", "kilograms", "kilo", "kilos", "kilogramme", "kilogrammes"), "mass", 1.0),
        Unit(listOf("g", "gram", "grams", "gramme", "grammes"), "mass", 0.001),
        Unit(listOf("lb", "lbs", "pound", "pounds", "livre", "livres"), "mass", 0.45359237),
        Unit(listOf("oz", "ounce", "ounces", "once", "onces"), "mass", 0.028349523),
        Unit(listOf("l", "liter", "liters", "litre", "litres"), "vol", 1.0),
        Unit(listOf("ml", "milliliter", "milliliters", "millilitre", "millilitres"), "vol", 0.001),
        Unit(listOf("cl", "centilitre", "centilitres"), "vol", 0.01),
        Unit(listOf("gal", "gallon", "gallons"), "vol", 3.785411784),
        Unit(listOf("cup", "cups", "tasse", "tasses"), "vol", 0.2365882),
        Unit(listOf("kmh", "km/h"), "speed", 1.0),
        Unit(listOf("mph"), "speed", 1.609344),
    )

    private val convRegex = Regex(
        "^\\s*(-?[0-9]+(?:[.,][0-9]+)?)\\s*([a-zA-Zéèû°/]+)\\s+(?:in|to|en|=|into)\\s+([a-zA-Zéèû°/]+)\\s*$"
    )

    private fun conversion(q: String): Answer? {
        val m = convRegex.find(q.lowercase()) ?: return null
        val value = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = m.groupValues[2]
        val to = m.groupValues[3]
        val temps = mapOf("c" to "C", "°c" to "C", "celsius" to "C", "f" to "F", "°f" to "F", "fahrenheit" to "F", "k" to "K", "kelvin" to "K")
        if (from in temps && to in temps) {
            val a = temps.getValue(from); val b = temps.getValue(to)
            val c = when (a) { "C" -> value; "F" -> (value - 32) * 5 / 9; else -> value - 273.15 }
            val r = when (b) { "C" -> c; "F" -> c * 9 / 5 + 32; else -> c + 273.15 }
            return Answer("${format(value)} °$a =", "${format(Math.round(r * 100) / 100.0)} °$b")
        }
        val u1 = units.firstOrNull { from in it.names } ?: return null
        val u2 = units.firstOrNull { to in it.names } ?: return null
        if (u1.kind != u2.kind) return null
        val r = value * u1.factor / u2.factor
        return Answer("${format(value)} ${u1.names.first()} =", "${format(Math.round(r * 10000) / 10000.0)} ${u2.names.first()}")
    }
}
