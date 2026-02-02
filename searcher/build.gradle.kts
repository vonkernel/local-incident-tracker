dependencies {
    // Internal dependency
    implementation(project(":shared"))
    implementation(project(":ai-core"))

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")

    // OpenSearch client
    implementation("org.opensearch.client:opensearch-java:3.4.0")

    // Jackson JSR310 for date/time serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
