package com.vonkernel.lit.collector.adapter.outbound.model


/**
 * API response header indicating success/failure.
 */
data class SafetyDataApiResponseHeader(
    val resultCode: String,    // "00" = success
    val resultMsg: String,      // "NORMAL SERVICE"
    val errorMsg: String?
)