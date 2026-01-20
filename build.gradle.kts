plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21" apply false
    id("org.springframework.boot") version "4.0.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

repositories {
    mavenCentral()
}

group = "com.vonkernel.lit"
version = "0.0.1-SNAPSHOT"

subprojects {
    // Gradle 9에서는 java 플러그인 적용이 명확해야 toolchain DSL 사용 가능
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        // Kotlin (stdlib-jdk8은 불필요하므로 생략)
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")

        // Spring Boot 4.0.1
        implementation("org.springframework.boot:spring-boot-starter")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

        // Testing
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("io.mockk:mockk:1.13.17")
    }

    // Gradle 9 및 Kotlin 2.2.21 권장 설정
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            // Kotlin 2.2에서 변경된 Context Parameters 플래그 적용
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xcontext-parameters")
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}