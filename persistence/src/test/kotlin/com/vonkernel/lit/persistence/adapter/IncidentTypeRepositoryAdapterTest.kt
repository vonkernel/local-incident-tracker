package com.vonkernel.lit.persistence.adapter

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
    @DisplayName("findAll: schema-h2.sql에서 INSERT된 모든 사건 유형을 도메인 모델로 반환한다")
    fun findAll_returnsAllIncidentTypes() {
        val result = adapter.findAll()

        assertThat(result).hasSize(37)
        assertThat(result.map { it.code }).contains("FOREST_FIRE", "TYPHOON", "EARTHQUAKE", "FLOOD")
        assertThat(result.map { it.name }).contains("산불", "태풍", "지진", "홍수")
    }

    @Test
    @DisplayName("findAll: 각 사건 유형의 code-name 매핑이 정확하다")
    fun findAll_correctCodeNameMapping() {
        val result = adapter.findAll()

        val nameByCode = result.associate { it.code to it.name }
        assertThat(nameByCode["FOREST_FIRE"]).isEqualTo("산불")
        assertThat(nameByCode["TYPHOON"]).isEqualTo("태풍")
        assertThat(nameByCode["EARTHQUAKE"]).isEqualTo("지진")
        assertThat(nameByCode["FLOOD"]).isEqualTo("홍수")
        assertThat(nameByCode["HEAVY_SNOW"]).isEqualTo("대설")
    }
}