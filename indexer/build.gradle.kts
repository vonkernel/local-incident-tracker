dependencies {
    // Internal dependency
    implementation(project(":shared"))

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Kafka for event consumption
    implementation("org.springframework.kafka:spring-kafka:3.3.11")

    // OpenSearch client
    implementation("org.opensearch.client:opensearch-java:3.4.0")
}
