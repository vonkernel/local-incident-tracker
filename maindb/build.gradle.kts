plugins {
    id("java")
}

description = "Database migrations using Flyway"

dependencies {
    // Flyway for database migrations
    implementation("org.flywaydb:flyway-core:11.20.2")
    implementation("org.flywaydb:flyway-database-postgresql:11.20.2")

    // PostgreSQL driver for migrations
    implementation("org.postgresql:postgresql:42.7.9")
}
