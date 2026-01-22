package com.vonkernel.lit.persistence.entity.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZonedDateTime

@Entity
@Table(
    name = "address",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["region_type", "code"], name = "uk_address_region_code")
    ]
)
data class AddressEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "region_type", length = 1, nullable = false)
    val regionType: String,

    @Column(name = "code", length = 100, nullable = false)
    val code: String,

    @Column(name = "address_name", length = 500, nullable = false)
    val addressName: String,

    @Column(name = "depth1_name", length = 255)
    val depth1Name: String? = null,

    @Column(name = "depth2_name", length = 255)
    val depth2Name: String? = null,

    @Column(name = "depth3_name", length = 255)
    val depth3Name: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: ZonedDateTime = ZonedDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: ZonedDateTime = ZonedDateTime.now(),

    @OneToOne(mappedBy = "address")
    val coordinate: AddressCoordinateEntity? = null
)