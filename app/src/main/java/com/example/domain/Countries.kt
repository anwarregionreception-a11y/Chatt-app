package com.example.domain

import java.util.Locale

data class Country(val code: String, val name: String) {
    val flag: String get() = Countries.flag(code)
}

object Countries {
    val all: List<Country> by lazy {
        Locale.getISOCountries()
            .map { Country(it, nameOf(it)) }
            .sortedBy { it.name }
    }

    fun nameOf(code: String): String =
        if (code.length != 2) "" else Locale("", code).getDisplayCountry(Locale.ENGLISH)

    /** The flag emoji is made of the two "regional indicator" letters of the country code. */
    fun flag(code: String): String {
        if (code.length != 2 || code.any { it.uppercaseChar() !in 'A'..'Z' }) return ""
        return code.uppercase().map { letter ->
            String(Character.toChars(0x1F1E6 + (letter - 'A')))
        }.joinToString("")
    }
}
