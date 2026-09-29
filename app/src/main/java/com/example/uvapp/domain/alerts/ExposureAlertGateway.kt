package com.example.uvapp.domain.alerts

/** Side-effect boundary for exposure alerts so ViewModel tests never need Android services. */
fun interface ExposureAlertGateway {
    fun notifyIndoorAutoPause()
}
