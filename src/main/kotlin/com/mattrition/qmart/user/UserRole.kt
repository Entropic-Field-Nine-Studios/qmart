package com.mattrition.qmart.user

object UserRole {
    const val USER = "USER"
    const val MODERATOR = "MODERATOR"
    const val ADMIN = "ADMIN"
    const val SUPERADMIN = "SUPERADMIN"

    private val priorityMap = mapOf(USER to 1, MODERATOR to 2, ADMIN to 3, SUPERADMIN to 4)

    /**
     * Compares a role to another via permission hierarchy.
     * - Current role is more privileged, return positive number
     * - Current role is less privileged, return negative number
     * - Both roles have same permissions, return 0
     *
     * If a role could not be matched, its level of permission is 0.
     */
    fun compare(
        currentRole: String,
        otherRole: String,
    ): Int {
        val currentPriority = priorityMap.getOrDefault(currentRole, 0)
        val otherPriority = priorityMap.getOrDefault(otherRole, 0)

        return currentPriority.compareTo(otherPriority)
    }
}
