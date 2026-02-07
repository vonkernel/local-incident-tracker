package com.vonkernel.lit.core.port.repository

import com.vonkernel.lit.core.entity.Location

interface AddressCacheRepository {
    fun findByAddressName(addressName: String): Location?
}