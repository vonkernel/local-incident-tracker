dependencies {
    // Internal dependencies
    implementation(project(":shared"))
    implementation(project(":ai-core"))
    implementation(project(":persistence"))

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Kafka for event consumption
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // Database
    runtimeOnly("org.postgresql:postgresql:42.7.9")

    // HTTP client for external APIs (LLM, Kakao Geocoding)
    implementation("org.springframework.boot:spring-boot-starter-webclient")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

// 테스트 설정: integration 태그는 기본 test task에서 제외
tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

// Integration Test 전용 task
tasks.register<Test>("integrationTest") {
    description = "Runs integration tests with real OpenAI API and local DB"
    group = "verification"

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
