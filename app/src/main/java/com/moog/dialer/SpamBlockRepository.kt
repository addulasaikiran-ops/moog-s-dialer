package com.moog.dialer

import android.content.Context

/**
 * User-managed caller blocklist. Entries are stored only in app-private preferences.
 * This does not consult a remote reputation service or identify unknown callers.
 */
object SpamBlockRepository {
    private const val PREFS = "moogs_dialer_spam_preferences"
    private const val KEY_BLOCKED_NUMBERS = "blocked_numbers"

    fun blockedNumbers(context: Context): List<String> =
        preferences(context).getStringSet(KEY_BLOCKED_NUMBERS, emptySet())
            .orEmpty()
            .toList()
            .sorted()

    fun isBlocked(context: Context, number: String): Boolean {
        val candidate = normalize(number)
        if (candidate.isBlank()) return false
        return preferences(context).getStringSet(KEY_BLOCKED_NUMBERS, emptySet())
            .orEmpty()
            .any { stored -> numbersMatch(stored, candidate) }
    }

    fun block(context: Context, number: String): Boolean {
        val normalized = normalize(number)
        if (normalized.isBlank()) return false
        val updated = preferences(context).getStringSet(KEY_BLOCKED_NUMBERS, emptySet())
            .orEmpty()
            .toMutableSet()
        updated.add(normalized)
        preferences(context).edit().putStringSet(KEY_BLOCKED_NUMBERS, updated).apply()
        return true
    }

    fun unblock(context: Context, number: String) {
        val normalized = normalize(number)
        val updated = preferences(context).getStringSet(KEY_BLOCKED_NUMBERS, emptySet())
            .orEmpty()
            .toMutableSet()
        updated.remove(normalized)
        preferences(context).edit().putStringSet(KEY_BLOCKED_NUMBERS, updated).apply()
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun normalize(number: String): String = number.filter(Char::isDigit)

    private fun numbersMatch(first: String, second: String): Boolean {
        if (first == second) return true
        // Accommodate country-code formatting only when one side is a 10-digit
        // national number. This is a convenience match, not a unique identity rule.
        return (first.length == 10 && second.length > 10 && second.endsWith(first)) ||
            (second.length == 10 && first.length > 10 && first.endsWith(second))
    }
}
