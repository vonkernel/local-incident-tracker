package com.vonkernel.lit.persistence.adapter

import com.vonkernel.lit.persistence.jpa.entity.article.UrgencyTypeEntity
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
    @DisplayName("findAll: 저장된 모든 긴급도를 도메인 모델로 반환한다")
    fun findAll_returnsAllUrgencies() {
        jpaRepository.saveAll(listOf(
            UrgencyTypeEntity(name = "긴급", level = 3),
            UrgencyTypeEntity(name = "중요", level = 2),
            UrgencyTypeEntity(name = "정보", level = 1)
        ))

        val result = adapter.findAll()

        assertThat(result).hasSize(3)
        assertThat(result.map { it.name }).containsExactlyInAnyOrder("긴급", "중요", "정보")
        assertThat(result.map { it.level }).containsExactlyInAnyOrder(1, 2, 3)
    }

    @Test
    @DisplayName("findAll: 데이터가 없으면 빈 리스트를 반환한다")
    fun findAll_returnsEmptyListWhenNoData() {
        val result = adapter.findAll()

        assertThat(result).isEmpty()
    }
}