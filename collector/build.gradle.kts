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
}
