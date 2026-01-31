package com.vonkernel.lit.core.port.repository

import com.vonkernel.lit.core.entity.IncidentType

interface IncidentTypeRepository {
    fun findAll(): List<IncidentType>
}