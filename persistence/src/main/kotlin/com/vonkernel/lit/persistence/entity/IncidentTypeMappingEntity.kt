package com.vonkernel.lit.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime

@Entity
@Table(
    name = "incident_type_mapping",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["article_id", "incident_type_id"], name = "uk_incident_type_mapping")
    ]
)
data class IncidentTypeMappingEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    val article: ArticleEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_type_id", nullable = false)
    val incidentType: IncidentTypeEntity,

    @Column(name = "created_at", nullable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now()
)
