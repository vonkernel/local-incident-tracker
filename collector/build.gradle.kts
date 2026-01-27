dependencies {
    // Internal dependencies
    implementation(project(":shared"))
    implementation(project(":persistence"))

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")

    // HTTP client for external APIs
    implementation("org.springframework.boot:spring-boot-starter-webclient")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
