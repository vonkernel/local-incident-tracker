package com.vonkernel.lit.persistence.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@Configuration
@EnableJpaRepositories(
    basePackages = ["com.vonkernel.lit.persistence.jpa"]
)
@EnableJpaAuditing
class JpaConfig