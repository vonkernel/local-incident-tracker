import org.springframework.boot.gradle.tasks.bundling.BootJar

dependencies {
    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0-M1"))

    // Spring AI - OpenAI starter (버전은 BOM에서 관리)
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // YAML 파싱
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
}

// ai-core는 라이브러리 모듈이므로 bootJar 비활성화
tasks.named<BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}

// 테스트 설정: integration 태그는 기본 test task에서 제외
tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

// Integration Test 전용 task
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests with real OpenAI API"
    group = "verification"

    // test task의 classpath와 testClassesDirs 상속
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    useJUnitPlatform {
        includeTags("integration")
    }

    // .env.local 파일에서 환경 변수 로드
    doFirst {
        val envFile = file(".env.local")
        if (envFile.exists()) {
            envFile.readLines().forEach { line ->
                if (line.isNotBlank() && !line.startsWith("#")) {
                    val (key, value) = line.split("=", limit = 2)
                    environment(key.trim(), value.trim())
                }
            }
            println("Loaded environment variables from .env.local")
        } else {
            println("Warning: .env.local file not found. Create it from .env.local.example")
        }
    }

    shouldRunAfter(tasks.test)
}
