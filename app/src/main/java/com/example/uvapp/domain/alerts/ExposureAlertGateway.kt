package com.example.uvapp.domain.alerts

/** Side-effect boundary for haptic and audible alerts. */
interface ExposureAlertGateway {
    fun notifyIndoorAutoPause()

    fun notifyExposureLimitReached()

    fun previewReapplyReminder()

    fun previewBandWarning()
}
