package com.vonkernel.lit.core.repository

import com.vonkernel.lit.core.entity.Urgency

interface UrgencyRepository {
    fun findAll(): List<Urgency>
}