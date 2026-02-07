package com.vonkernel.lit.persistence.adapter

import com.vonkernel.lit.persistence.jpa.JpaUrgencyTypeRepository
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
@Import(UrgencyRepositoryAdapter::class)
@DisplayName("UrgencyRepositoryAdapter 테스트")
class UrgencyRepositoryAdapterTest {

    @Autowired
    private lateinit var adapter: UrgencyRepositoryAdapter

    @Autowired
    private lateinit var jpaRepository: JpaUrgencyTypeRepository

    @Test
    @DisplayName("findAll: schema-h2.sql에서 INSERT된 모든 긴급도를 도메인 모델로 반환한다")
    fun findAll_returnsAllUrgencies() {
        val result = adapter.findAll()

        assertThat(result).hasSize(5)
        assertThat(result.map { it.name }).containsExactlyInAnyOrder("정보", "주의", "경계", "심각", "긴급")
        assertThat(result.map { it.level }).containsExactlyInAnyOrder(1, 3, 5, 7, 9)
    }

    @Test
    @DisplayName("findAll: 각 긴급도의 name-level 매핑이 정확하다")
    fun findAll_correctNameLevelMapping() {
        val result = adapter.findAll()

        val levelByName = result.associate { it.name to it.level }
        assertThat(levelByName["정보"]).isEqualTo(1)
        assertThat(levelByName["주의"]).isEqualTo(3)
        assertThat(levelByName["경계"]).isEqualTo(5)
        assertThat(levelByName["심각"]).isEqualTo(7)
        assertThat(levelByName["긴급"]).isEqualTo(9)
    }
}