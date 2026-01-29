package com.vonkernel.lit.persistence.adapter

import com.vonkernel.lit.persistence.entity.core.IncidentTypeEntity
import com.vonkernel.lit.persistence.jpa.JpaIncidentTypeRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(IncidentTypeRepositoryAdapter::class)
@DisplayName("IncidentTypeRepositoryAdapter 테스트")
class IncidentTypeRepositoryAdapterTest {

    @Autowired
    private lateinit var adapter: IncidentTypeRepositoryAdapter

    @Autowired
    private lateinit var jpaRepository: JpaIncidentTypeRepository

    @Test
    @DisplayName("findAll: 저장된 모든 사건 유형을 도메인 모델로 반환한다")
    fun findAll_returnsAllIncidentTypes() {
        jpaRepository.saveAll(listOf(
            IncidentTypeEntity(code = "forest_fire", name = "산불"),
            IncidentTypeEntity(code = "typhoon", name = "태풍"),
            IncidentTypeEntity(code = "earthquake", name = "지진")
        ))

        val result = adapter.findAll()

        assertThat(result).hasSize(3)
        assertThat(result.map { it.code }).containsExactlyInAnyOrder("forest_fire", "typhoon", "earthquake")
        assertThat(result.map { it.name }).containsExactlyInAnyOrder("산불", "태풍", "지진")
    }

    @Test
    @DisplayName("findAll: 데이터가 없으면 빈 리스트를 반환한다")
    fun findAll_returnsEmptyListWhenNoData() {
        val result = adapter.findAll()

        assertThat(result).isEmpty()
    }
}