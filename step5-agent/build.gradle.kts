plugins {
    java
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.kosta"
version = "0.0.1-SNAPSHOT"
description = "KOSTA Spring AI Agent Lab"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["springAiVersion"] = "1.1.5"

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("com.h2database:h2")

    // Spring AI - Chat (OpenAI)
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // Spring AI - Vector Store (pgvector)
    

    // Spring AI - Chat Memory (JDBC)
    implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc")

    // Spring AI - RAG advisors (RetrievalAugmentationAdvisor, QuestionAnswerAdvisor 등)
    implementation("org.springframework.ai:spring-ai-advisors-vector-store")
    implementation("org.springframework.ai:spring-ai-rag")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
