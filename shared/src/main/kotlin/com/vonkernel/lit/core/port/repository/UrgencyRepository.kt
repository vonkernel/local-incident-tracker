package com.vonkernel.lit.core.port.repository

import com.vonkernel.lit.core.entity.Urgency

interface UrgencyRepository {
    fun findAll(): List<Urgency>
}