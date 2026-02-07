import org.gradle.kotlin.dsl.named
import org.springframework.boot.gradle.tasks.bundling.BootJar

configurations.implementation {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter")
}

dependencies {
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

// shared는 라이브러리 모듈이므로 bootJar 비활성화
tasks.named<BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
}