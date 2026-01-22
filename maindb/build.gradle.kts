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
    id("org.flywaydb.flyway") version "11.20.2"
}

val dbUrl = System.getenv("DB_URL") ?: (project.findProperty("db.url") as? String ?: "jdbc:postgresql://localhost:5432/lit_maindb")
val dbUser = System.getenv("DB_USER") ?: (project.findProperty("db.user") as? String ?: "postgres")
val dbPassword = System.getenv("DB_PASSWORD") ?: (project.findProperty("db.password") as? String ?: "postgres")
val migrationPath = "filesystem:${project.projectDir}/src/main/resources/db/migration"

dependencies {
    implementation("org.postgresql:postgresql:42.7.9")
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