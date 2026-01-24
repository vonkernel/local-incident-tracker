val springAiVersion = "2.0.0-M1"

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
    }
}

dependencies {
    // Spring AI - OpenAI starter (버전은 BOM에서 관리)
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // YAML 파싱
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
}

// ai-core는 라이브러리 모듈이므로 bootJar 비활성화
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}
