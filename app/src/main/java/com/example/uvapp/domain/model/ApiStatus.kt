package com.example.uvapp.domain.model

/** Availability information rendered by the developer diagnostics card. */
data class ApiStatus(
    val name: String,
    val ok: Boolean,
    val detail: String,
)
