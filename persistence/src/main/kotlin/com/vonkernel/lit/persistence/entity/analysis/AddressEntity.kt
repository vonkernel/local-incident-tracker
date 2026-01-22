package com.vonkernel.lit.persistence.entity.analysis

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
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
class AddressEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null,

    @Column(name = "region_type", length = 1, nullable = false)
    var regionType: String,

    @Column(name = "code", length = 100, nullable = false)
    var code: String,

    @Column(name = "address_name", length = 500, nullable = false)
    var addressName: String,

    @Column(name = "depth1_name", length = 255)
    var depth1Name: String? = null,

    @Column(name = "depth2_name", length = 255)
    var depth2Name: String? = null,

    @Column(name = "depth3_name", length = 255)
    var depth3Name: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: ZonedDateTime = ZonedDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: ZonedDateTime = ZonedDateTime.now(),

    @OneToOne(mappedBy = "address", fetch = FetchType.LAZY, optional = true)
    var coordinate: AddressCoordinateEntity? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AddressEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}