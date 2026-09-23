package com.sleepagent.prototype.data

/** RFC 4180 escaping, including multiline reasons and JSON metadata. */
object InterventionCsv {
    fun row(values: List<String?>): String = values.joinToString(",") { value ->
        val text = value.orEmpty()
        if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"${text.replace("\"", "\"\"")}\""
        else text
    } + "\r\n"
}
