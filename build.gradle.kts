import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21"
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
