/*
 * Copyright 2025 The wgsl-fuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

val antlrVersion: String = "4.10"
val jacksonVersion: String = "2.19.0"
val ktorVersion: String = "3.1.3"
val logbackVersion: String = "1.5.18"

plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
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
    antlr("org.antlr:antlr4:$antlrVersion")
    testImplementation(kotlin("test"))

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")
}

tasks.generateGrammarSource {
    outputDirectory =
        layout.buildDirectory
            .dir("generated/sources/main/kotlin/antlr")
            .get()
            .asFile
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

tasks.register<JavaExec>("runServer") {
    mainClass.set("com.wgslfuzz.server.ServerKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("runGenerator") {
    mainClass.set("com.wgslfuzz.tools.GenerateEquivalentShaderJobsKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("runJobsViaServer") {
    mainClass.set("com.wgslfuzz.tools.RunJobsViaServerKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("reduceJobViaServer") {
    mainClass.set("com.wgslfuzz.tools.ReduceJobViaServerKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("standAloneShaderHtml") {
    mainClass.set("com.wgslfuzz.tools.StandAloneShaderHtmlKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("parseAndPrettyPrint") {
    mainClass.set("com.wgslfuzz.tools.ParseAndPrettyPrintKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("printShaderWithCommentary") {
    mainClass.set("com.wgslfuzz.tools.PrintShaderWithCommentaryKt")
    classpath = sourceSets["main"].runtimeClasspath
}
