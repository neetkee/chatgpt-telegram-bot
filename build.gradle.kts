import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    kotlin("jvm") version "1.9.22"
    id("com.google.cloud.tools.jib") version "3.4.0"
    id("com.github.ben-manes.versions") version "0.50.0"
    application
}

group = "com.neetkee"
version = "0.1"

repositories {
    mavenCentral()
}

val exposedVersion: String by project
dependencies {
    implementation("org.telegram:telegrambots:6.8.0")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("org.xerial:sqlite-jdbc:3.45.0.0")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation(platform("com.aallam.openai:openai-client-bom:3.6.3"))
    implementation("com.aallam.openai:openai-client")
    implementation("io.ktor:ktor-client-cio")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}

tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}