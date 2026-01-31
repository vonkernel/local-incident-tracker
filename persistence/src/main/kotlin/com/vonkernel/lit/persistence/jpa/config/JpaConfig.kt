package com.vonkernel.lit.persistence.jpa.config

import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@Configuration
@EnableJpaRepositories(
    basePackages = ["com.vonkernel.lit.persistence.jpa"]
)
@EntityScan(basePackages = ["com.vonkernel.lit.persistence.jpa.entity"])
@EnableJpaAuditing
class JpaConfig