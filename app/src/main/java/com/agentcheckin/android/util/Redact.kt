package com.agentcheckin.android.util

object Redact {
    private val jwt = Regex("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{5,}")
    private val personalToken = Regex("pt-[A-Za-z0-9_-]{8,}")
    private val jobToken = Regex("(jt|jrt|dt|drt)-[A-Za-z0-9_-]{8,}")
    private val authHeader = Regex("(?i)(authorization|cookie)\\s*[:=]\\s*\\S+")

    fun text(value: String, maxLength: Int = 300): String {
        var output = value
        output = jwt.replace(output, "***")
        output = personalToken.replace(output, "***")
        output = jobToken.replace(output, "***")
        output = authHeader.replace(output) { match ->
            "${match.groupValues[1]}: ***"
        }
        if (output.length > maxLength) {
            output = output.take(maxLength) + "..."
        }
        return output
    }
}
