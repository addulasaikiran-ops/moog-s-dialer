package com.moog.dialer.data

object T9Search {
    private val letterToDigit = mapOf(
        'A' to '2', 'B' to '2', 'C' to '2',
        'D' to '3', 'E' to '3', 'F' to '3',
        'G' to '4', 'H' to '4', 'I' to '4',
        'J' to '5', 'K' to '5', 'L' to '5',
        'M' to '6', 'N' to '6', 'O' to '6',
        'P' to '7', 'Q' to '7', 'R' to '7', 'S' to '7',
        'T' to '8', 'U' to '8', 'V' to '8',
        'W' to '9', 'X' to '9', 'Y' to '9', 'Z' to '9'
    )

    fun nameToDigits(value: String): String = buildString {
        value.uppercase().forEach { char ->
            when {
                char in '0'..'9' -> append(char)
                letterToDigit.containsKey(char) -> append(letterToDigit.getValue(char))
            }
        }
    }

    fun matches(contact: PhoneContact, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return true
        val digits = trimmed.filter(Char::isDigit)
        val nameDigits = nameToDigits(contact.displayName)
        val queryDigits = if (digits.isNotEmpty()) digits else nameToDigits(trimmed)
        val nameMatch = queryDigits.isNotEmpty() && nameDigits.contains(queryDigits)
        val numberMatch = digits.isNotEmpty() && contact.phoneNumber.filter(Char::isDigit).contains(digits)
        return contact.displayName.contains(trimmed, ignoreCase = true) || nameMatch || numberMatch
    }
}
