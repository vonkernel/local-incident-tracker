package com.vonkernel.lit.persistence.jpa

import com.vonkernel.lit.persistence.entity.outbox.AnalysisResultOutboxEntity
import org.springframework.data.jpa.repository.JpaRepository

interface JpaAnalysisResultOutboxRepository : JpaRepository<AnalysisResultOutboxEntity, Long>