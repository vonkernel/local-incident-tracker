package com.vonkernel.lit.entity

enum class Urgency(val priority: Int) {
    CRITICAL(10),    // 긴급
    IMPORTANT(7),   // 중요
    INFO(3);        // 정보
}