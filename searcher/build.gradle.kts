dependencies {
    // Internal dependency
    implementation(project(":shared"))

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")

    // OpenSearch client
    implementation("org.opensearch.client:opensearch-java:3.4.0")
}