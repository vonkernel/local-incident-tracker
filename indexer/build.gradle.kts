dependencies {
    // Internal dependencies
    implementation(project(":shared"))
    implementation(project(":ai-core"))

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Kafka for event consumption
    implementation("org.springframework.boot:spring-boot-starter-kafka")

    // OpenSearch client
    implementation("org.opensearch.client:opensearch-java:3.4.0")

    // Jackson - JSR310 for Instant deserialization in CDC payload
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
}
