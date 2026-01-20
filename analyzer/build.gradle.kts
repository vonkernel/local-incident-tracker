dependencies {
    // Internal dependency
    implementation(project(":shared"))

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Kafka for event consumption
    implementation("org.springframework.kafka:spring-kafka:3.3.11")

    // Database
    runtimeOnly("org.postgresql:postgresql:42.7.9")

    // HTTP client for external APIs (LLM, Kakao Geocoding)
    implementation("org.springframework.boot:spring-boot-starter-webclient")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")
}
