package com.eirahoutmoss.nextools.uwb

/** Tek UWB ölçümü. Açılar, cihazın anten düzeni desteklemiyorsa null gelir. */
data class UwbReading(
    val distanceM: Float?,
    val azimuthDeg: Float?,
    val elevationDeg: Float?,
    val disconnected: Boolean = false,
    val failure: Boolean = false,
)

enum class UwbRole { CONTROLLER, CONTROLEE }
