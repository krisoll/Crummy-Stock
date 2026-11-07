package com.example.stockapplication.data

/**
 * A minimal JSON reader — the mirror of [JsonWriter].
 *
 * Exists for the same reason the writer does: the project has no serialisation dependency, and
 * `org.json` is stubbed out in JVM unit tests. A small recursive-descent parser keeps the error
 * messages in one visible place and makes the importer testable on the plain JVM.
 *
 * It is deliberately strict: it accepts exactly RFC 8259 and nothing more (no comments, no
 * trailing commas, no single quotes, no unquoted keys). A backup is produced by this app, so
 * anything outside the spec means the file was hand-edited or truncated, and silently guessing
 * what was meant is how a restore corrupts a database.
 *
 * Values come back as the types they need to be: [JsonParser.JsonNull], [Boolean], [String],
 * [JsonParser.JsonNumber] for every number, [List] and [Map]. Numbers keep their original lexeme
 * rather than being widened to [Double], so a 13-digit epoch timestamp survives to the digit.
 *
 * Not thread-safe; build one per document.
 */
object JsonParser {

    /**
     * Parses [text] into one JSON value.
     *
     * Content after the first complete value is an error, not ignored: a file with two documents
     * in it is a file whose second half the user probably does not want to lose silently.
     *
     * @throws JsonParseException with the line and column of the problem.
     */
    fun parse(text: String): Any? {
        val reader = Reader(text)
        reader.skipWhitespace()
        val value = reader.readValue()
        reader.skipWhitespace()
        if (!reader.atEnd()) {
            throw reader.fail("Unexpected content after the end of the document")
        }
        return value
    }

    /**
     * A number exactly as it appeared in the source.
     *
     * The accessors parse that lexeme instead of going through [Double], so `1700000000000` does
     * not come back as `1699999999999`.
     */
    class JsonNumber(val raw: String) {
        fun toLongOrNull(): Long? = raw.toLongOrNull()
            ?: raw.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()

        fun toIntOrNull(): Int? = toLongOrNull()?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else null }

        fun toBooleanOrNull(): Boolean? = when (raw) {
            "1" -> true
            "0" -> false
            else -> null
        }

        override fun toString(): String = raw
    }

    /** Marker for a JSON `null`, so a missing key stays distinguishable from an explicit null. */
    object JsonNull {
        override fun toString(): String = "null"
    }

    private class Reader(private val text: String) {
        private var pos = 0

        fun atEnd(): Boolean = pos >= text.length

        fun fail(reason: String): JsonParseException {
            var line = 1
            var column = 1
            for (i in 0 until pos.coerceAtMost(text.length)) {
                if (text[i] == '\n') {
                    line++
                    column = 1
                } else {
                    column++
                }
            }
            return JsonParseException("$reason (line $line, column $column)")
        }

        fun skipWhitespace() {
            while (pos < text.length) {
                when (text[pos]) {
                    ' ', '\t', '\n', '\r' -> pos++
                    else -> return
                }
            }
        }

        fun readValue(): Any? {
            if (atEnd()) throw fail("Unexpected end of document")
            return when (val char = text[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", JsonNull)
                else -> if (char == '-' || char.isAsciiDigit()) {
                    readNumber()
                } else {
                    throw fail("Unexpected character '$char'")
                }
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return out
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') throw fail("Expected a quoted key")
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                out[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return out
                    }
                    else -> throw fail("Expected ',' or '}' in object")
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return out
            }
            while (true) {
                skipWhitespace()
                out.add(readValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return out
                    }
                    else -> throw fail("Expected ',' or ']' in array")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (atEnd()) throw fail("Unterminated string")
                when (val char = text[pos]) {
                    '"' -> {
                        pos++
                        return out.toString()
                    }
                    '\\' -> {
                        pos++
                        if (atEnd()) throw fail("Unterminated escape sequence")
                        when (val escape = text[pos]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 >= text.length) throw fail("Truncated \\u escape")
                                val hex = text.substring(pos + 1, pos + 5)
                                val code = hex.toIntOrNull(16)
                                    ?: throw fail("Invalid \\u escape '$hex'")
                                out.append(code.toChar())
                                pos += 4
                            }
                            else -> throw fail("Unknown escape '\\$escape'")
                        }
                        pos++
                    }
                    // Raw control characters are illegal in a JSON string and the writer escapes
                    // them, so seeing one here means the file was damaged in transit.
                    else -> if (char < ' ') {
                        throw fail("Unescaped control character in string")
                    } else {
                        out.append(char)
                        pos++
                    }
                }
            }
        }

        private fun readNumber(): JsonNumber {
            val start = pos
            if (peek() == '-') pos++
            while (!atEnd() && text[pos].isAsciiDigit()) pos++
            if (!atEnd() && text[pos] == '.') {
                pos++
                if (atEnd() || !text[pos].isAsciiDigit()) throw fail("Malformed number")
                while (!atEnd() && text[pos].isAsciiDigit()) pos++
            }
            if (!atEnd() && (text[pos] == 'e' || text[pos] == 'E')) {
                pos++
                if (!atEnd() && (text[pos] == '+' || text[pos] == '-')) pos++
                if (atEnd() || !text[pos].isAsciiDigit()) throw fail("Malformed exponent")
                while (!atEnd() && text[pos].isAsciiDigit()) pos++
            }
            val raw = text.substring(start, pos)
            if (raw.isEmpty() || raw == "-") throw fail("Malformed number")
            return JsonNumber(raw)
        }

        private fun <T> readLiteral(literal: String, value: T): T {
            if (!text.startsWith(literal, pos)) throw fail("Expected '$literal'")
            pos += literal.length
            return value
        }

        private fun peek(): Char {
            if (atEnd()) throw fail("Unexpected end of document")
            return text[pos]
        }

        private fun expect(char: Char) {
            if (peek() != char) throw fail("Expected '$char'")
            pos++
        }

        private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'
    }
}

/** A malformed JSON document. [message] always carries the position of the problem. */
class JsonParseException(message: String) : Exception(message)

// ---- Typed accessors -------------------------------------------------------
//
// The importer reads fields through these rather than casting by hand, so a wrong type produces
// the same clear message everywhere instead of a ClassCastException pointing at a random line.

/** Reads [key] as an object, or throws. */
fun Map<String, Any?>.requireObject(key: String): Map<String, Any?> =
    obj(key) ?: throw JsonParseException("Missing required object \"$key\"")

/** Reads [key] as an object, or null when absent. */
fun Map<String, Any?>.obj(key: String): Map<String, Any?>? = when (val value = this[key]) {
    null, JsonParser.JsonNull -> null
    is Map<*, *> -> {
        @Suppress("UNCHECKED_CAST")
        value as Map<String, Any?>
    }
    else -> throw JsonParseException("Expected \"$key\" to be an object")
}

/** Reads [key] as an array of objects. A missing key is an empty array. */
fun Map<String, Any?>.objArray(key: String): List<Map<String, Any?>> =
    when (val value = this[key]) {
        null, JsonParser.JsonNull -> emptyList()
        is List<*> -> value.mapIndexed { index, item ->
            if (item is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                item as Map<String, Any?>
            } else {
                throw JsonParseException("Element $index of \"$key\" is not an object")
            }
        }
        else -> throw JsonParseException("Expected \"$key\" to be an array")
    }

/** Reads [key] as a string, or null when the key is absent or explicitly null. */
fun Map<String, Any?>.string(key: String): String? = when (val value = this[key]) {
    null, JsonParser.JsonNull -> null
    is String -> value
    is JsonParser.JsonNumber -> value.raw
    else -> throw JsonParseException("Expected \"$key\" to be a string")
}

/** Reads [key] as a string, throwing when it is missing. */
fun Map<String, Any?>.requiredString(key: String): String =
    string(key) ?: throw JsonParseException("Missing required field \"$key\"")

/** Reads [key] as a Long, or null when absent. */
fun Map<String, Any?>.long(key: String): Long? = when (val value = this[key]) {
    null, JsonParser.JsonNull -> null
    is JsonParser.JsonNumber -> value.toLongOrNull()
        ?: throw JsonParseException("Field \"$key\" is not a whole number")
    is String -> value.trim().toLongOrNull()
        ?: throw JsonParseException("Field \"$key\" is not a whole number")
    else -> throw JsonParseException("Expected \"$key\" to be a number")
}

fun Map<String, Any?>.requiredLong(key: String): Long =
    long(key) ?: throw JsonParseException("Missing required field \"$key\"")

/** Reads [key] as an Int, or null when absent. */
fun Map<String, Any?>.int(key: String): Int? = when (val value = this[key]) {
    null, JsonParser.JsonNull -> null
    is JsonParser.JsonNumber -> value.toIntOrNull()
        ?: throw JsonParseException("Field \"$key\" is not a 32-bit integer")
    is String -> value.trim().toIntOrNull()
        ?: throw JsonParseException("Field \"$key\" is not a 32-bit integer")
    else -> throw JsonParseException("Expected \"$key\" to be a number")
}

fun Map<String, Any?>.requiredInt(key: String): Int =
    int(key) ?: throw JsonParseException("Missing required field \"$key\"")

/**
 * Reads [key] as a boolean.
 *
 * `1`/`0` and `"true"`/`"false"` are accepted alongside real booleans: that is what a hand-edited
 * file or a spreadsheet round-trip tends to produce, and honouring it cannot lose data.
 */
fun Map<String, Any?>.boolean(key: String, default: Boolean = false): Boolean =
    when (val value = this[key]) {
        null, JsonParser.JsonNull -> default
        is Boolean -> value
        is JsonParser.JsonNumber -> value.toBooleanOrNull()
            ?: throw JsonParseException("Field \"$key\" is not a boolean")
        is String -> when (value.trim().lowercase()) {
            "true", "1", "yes" -> true
            "false", "0", "no", "" -> false
            else -> throw JsonParseException("Field \"$key\" is not a boolean")
        }
        else -> throw JsonParseException("Expected \"$key\" to be a boolean")
    }
