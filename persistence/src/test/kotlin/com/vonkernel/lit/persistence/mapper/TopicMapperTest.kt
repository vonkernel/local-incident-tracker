package com.vonkernel.lit.persistence.mapper

import com.vonkernel.lit.persistence.TestFixtures
import com.vonkernel.lit.persistence.jpa.mapper.TopicMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("TopicMapper 테스트")
class TopicMapperTest {

    // ===== toDomainModel() 테스트 =====
    @Test
    @DisplayName("toDomainModel: topic 필드 매핑")
    fun `toDomainModel - topic 필드가 올바르게 매핑된다`() {
        val entity = TestFixtures.createTopicAnalysisEntity(topic = "서울 강남구 침수 피해 발생")
        val domain = TopicMapper.toDomainModel(entity)

        assertThat(domain.topic).isEqualTo("서울 강남구 침수 피해 발생")
    }

    // ===== toPersistenceModel() 테스트 =====
    @Test
    @DisplayName("toPersistenceModel: topic 필드 매핑")
    fun `toPersistenceModel - topic 필드가 올바르게 매핑된다`() {
        val domain = TestFixtures.createTopic(topic = "경기도 산불 확산 경보")
        val entity = TopicMapper.toPersistenceModel(domain)

        assertThat(entity.topic).isEqualTo("경기도 산불 확산 경보")
    }

    // ===== 양방향 변환 (Round-trip) 테스트 =====
    @Test
    @DisplayName("Round-trip: Domain → Entity → Domain 불변성")
    fun `roundTrip - Domain에서 Entity 변환 후 다시 Domain으로 변환하면 원본과 동일`() {
        val originalDomain = TestFixtures.createTopic(topic = "전국 폭우 특보 발령")

        val entity = TopicMapper.toPersistenceModel(originalDomain)
        val reconvertedDomain = TopicMapper.toDomainModel(entity)

        assertThat(reconvertedDomain).isEqualTo(originalDomain)
    }
}
