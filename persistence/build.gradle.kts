import org.gradle.kotlin.dsl.named
import org.springframework.boot.gradle.tasks.bundling.BootJar

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:11.20.2")
        classpath("org.postgresql:postgresql:42.7.9")
    }
}

plugins {
    id("java")
    kotlin("plugin.jpa") version "2.2.21"
    id("org.flywaydb.flyway") version "11.20.2"
}

val dbUrl = System.getenv("DB_URL") ?: (project.findProperty("db.url") as? String ?: "jdbc:postgresql://localhost:5432/lit_maindb")
val dbUser = System.getenv("DB_USER") ?: (project.findProperty("db.user") as? String ?: "postgres")
val dbPassword = System.getenv("DB_PASSWORD") ?: (project.findProperty("db.password") as? String ?: "postgres")
val migrationPath = "filesystem:${project.projectDir}/src/main/resources/db/migration"

dependencies {
    // Shared module (domain models)
    implementation(project(":shared"))

    // PostgreSQL driver
    implementation("org.postgresql:postgresql:42.7.9")

    // Spring Data JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Jackson for JSON serialization with Java 8+ date/time support
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // H2 for integration tests
    testImplementation("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
}

flyway {
    url = dbUrl
    user = dbUser
    password = dbPassword
    locations = arrayOf(migrationPath)
}

tasks.register("flywayMigrateLocal") {
    group = "database"
    description = "기존 flywayMigrate 설정을 사용하여 로컬 마이그레이션 실행"

    dependsOn("flywayMigrate")

    doFirst {
        println("Migration Path: $migrationPath")
        println("Target URL: $dbUrl")
    }
}

// persistence는 라이브러리 모듈이므로 bootJar 비활성화
tasks.named<BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}