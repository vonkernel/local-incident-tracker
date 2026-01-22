package com.vonkernel.lit.persistence.entity.core

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "incident_type")
data class IncidentTypeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "code", length = 50, nullable = false, unique = true)
    val code: String,

    @Column(name = "name", length = 100, nullable = false)
    val name: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: ZonedDateTime = ZonedDateTime.now()
)