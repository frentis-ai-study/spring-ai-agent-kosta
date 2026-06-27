plugins {
    java
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.kosta.demo"
version = "0.0.1-SNAPSHOT"
description = "Spring AI PDF SimpleRAG Chatbot Demo"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

extra["springAiVersion"] = "1.1.7"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Spring AI - Chat + Embedding (OpenAI)
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // Spring AI - PDF 문서 리더 (PagePdfDocumentReader, PDFBox 기반)
    implementation("org.springframework.ai:spring-ai-pdf-document-reader")

    // Spring AI - RAG 어드바이저 (QuestionAnswerAdvisor)
    implementation("org.springframework.ai:spring-ai-advisors-vector-store")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
