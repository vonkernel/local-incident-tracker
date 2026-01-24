package com.vonkernel.lit.ai

import org.springframework.boot.autoconfigure.SpringBootApplication

/**
 * Integration Test용 Spring Boot Application
 *
 * ai-core는 라이브러리 모듈이므로 @SpringBootApplication이 없음
 * Integration Test에서 Spring context를 띄우기 위한 테스트 전용 Application
 */
@SpringBootApplication
class IntegrationTestApplication
