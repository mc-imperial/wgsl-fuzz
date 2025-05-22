import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktor_version: String = "3.1.3"
val logback_version: String = "1.5.18"

plugins {
    kotlin("jvm") version "2.1.21"
    java
    antlr
}

kotlin {
    jvmToolchain(21)
}

group = "com.wgslfuzz"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:4.10")
    testImplementation(kotlin("test"))

    implementation("io.ktor:ktor-network-tls-certificates-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-jackson-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-cors-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-html-builder-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-sessions-jvm:$ktor_version")

    implementation("ch.qos.logback:logback-classic:$logback_version")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.0")

}

tasks.generateGrammarSource {
    outputDirectory = layout.buildDirectory.dir("generated/sources/main/kotlin/antlr").get().asFile
    arguments = listOf("-visitor", "-package", "com.wgslfuzz")
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

sourceSets {
    main {
        java {
            srcDir(tasks.generateGrammarSource)
        }
    }
}

tasks.named("compileTestKotlin") {
    dependsOn("generateTestGrammarSource")
}
