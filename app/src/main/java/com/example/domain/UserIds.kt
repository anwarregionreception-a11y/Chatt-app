package com.example.domain

/** The public name a person picks to be found and to log in with. */
object UserIds {
    private const val SYNTHETIC_DOMAIN = "id.rabbta-16040.firebaseapp.com"
    private val pattern = Regex("^[a-z][a-z0-9_.]{2,19}$")

    fun normalise(text: String): String = text.trim().lowercase()

    /** Returns a message when the ID cannot be used, otherwise null. */
    fun problem(id: String): String? = when {
        id.length < 3 -> "Kam az kam 3 huroof"
        id.length > 20 -> "Zyada se zyada 20 huroof"
        !pattern.matches(id) -> "Pehla harf a-z ho. Sirf a-z, 0-9, _ aur . istemal karein"
        else -> null
    }

    /**
     * Accounts created without an email sign in to Firebase with this address.
     * The domain belongs to the Firebase project, so nobody else can receive mail for it.
     */
    fun syntheticEmail(id: String): String = "$id@$SYNTHETIC_DOMAIN"

    fun isSynthetic(email: String): Boolean = email.endsWith("@$SYNTHETIC_DOMAIN")
}
