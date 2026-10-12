package com.example.uvapp.platform.environment

/** Coordinates runtime permission changes without depending on Android sensor classes. */
internal class StepCounterRegistrationCoordinator(
    private val register: () -> Boolean,
    private val unregister: () -> Unit,
    private val clearReading: () -> Unit,
) {
    /** Returns whether the step listener is registered after applying the permission state. */
    fun refresh(
        hasPermission: Boolean,
        isRegistered: Boolean,
    ): Boolean =
        when {
            hasPermission && !isRegistered -> register()
            !hasPermission -> {
                if (isRegistered) unregister()
                clearReading()
                false
            }
            else -> true
        }
}
