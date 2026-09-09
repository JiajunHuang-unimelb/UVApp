package com.example.uvapp.domain.exposure

/** Approximate minimum erythemal dose threshold for each Fitzpatrick skin type. */
enum class SkinType(
    val exposureLimitSed: Double,
) {
    TYPE_I(2.0),
    TYPE_II(2.5),
    TYPE_III(3.0),
    TYPE_IV(4.5),
    TYPE_V(6.0),
    TYPE_VI(10.0),
}
