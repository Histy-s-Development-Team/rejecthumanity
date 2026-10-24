package net.oraclehisty.rejecthumanity.assetgen

/**
 * A small pretty-printing JSON writer for the generated asset files, so the generator needs no dependencies.
 * Maps keep insertion order. Lists of plain values (numbers, strings) stay on one line, which keeps model files
 * readable: `"origin": [-4, 12, -2]`.
 */
object Json {
    fun write(value: Any?): String = StringBuilder().also { append(it, value, 0) }.append('\n').toString()

    private fun append(out: StringBuilder, value: Any?, depth: Int) {
        when (value) {
            null -> out.append("null")
            is String -> quote(out, value)
            is Boolean -> out.append(value)
            is Number -> out.append(number(value))
            is Map<*, *> -> block(out, value.entries, depth, '{', '}') { entry ->
                quote(out, entry.key.toString())
                out.append(": ")
                append(out, entry.value, depth + 1)
            }
            is Collection<*> -> if (value.all { it is Number || it is String || it is Boolean }) {
                out.append('[')
                value.forEachIndexed { i, item ->
                    if (i > 0) out.append(", ")
                    append(out, item, depth + 1)
                }
                out.append(']')
            } else {
                block(out, value, depth, '[', ']') { append(out, it, depth + 1) }
            }
            else -> error("Can't write ${value::class.simpleName} as JSON")
        }
    }

    private fun <T> block(out: StringBuilder, items: Collection<T>, depth: Int, open: Char, close: Char, item: (T) -> Unit) {
        if (items.isEmpty()) {
            out.append(open).append(close)
            return
        }
        out.append(open).append('\n')
        items.forEachIndexed { i, entry ->
            indent(out, depth + 1)
            item(entry)
            if (i < items.size - 1) out.append(',')
            out.append('\n')
        }
        indent(out, depth)
        out.append(close)
    }

    private fun indent(out: StringBuilder, depth: Int) {
        repeat(depth) { out.append("  ") }
    }

    private fun quote(out: StringBuilder, text: String) {
        out.append('"')
        for (c in text) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                else -> out.append(c)
            }
        }
        out.append('"')
    }

    /**
     * Rounded to 4 decimals (model units are pixels, so finer is just floating-point noise such as
     * -0.8999999999999999); whole numbers without a trailing ".0".
     */
    private fun number(value: Number): String {
        val d = Math.round(value.toDouble() * 10_000.0) / 10_000.0
        if (d == 0.0) return "0"
        if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) return d.toLong().toString()
        return d.toString()
    }
}
