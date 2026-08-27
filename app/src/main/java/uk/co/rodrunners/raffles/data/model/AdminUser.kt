package uk.co.rodrunners.raffles.data.model

/** Someone with a role on the back office. */
data class AdminUser(
    val uid: String = "",
    val email: String? = null,
    val displayName: String? = null,
    val role: String = "support",
    val active: Boolean = true,
) {
    val roleLabel: String get() = ROLES.firstOrNull { it.first == role }?.second ?: role

    companion object {
        val ROLES = listOf(
            "super_admin" to "Super Admin",
            "admin" to "Admin",
            "content_manager" to "Content",
            "support" to "Support",
        )
    }
}
