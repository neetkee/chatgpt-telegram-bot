plugins {
    kotlin("jvm") version "1.8.0"
    id("com.google.cloud.tools.jib") version "3.3.1"
    application
}

group = "com.neetkee"
version = "0.1"

repositories {
    mavenCentral()
}

val exposedVersion: String by project
dependencies {
    implementation("org.telegram:telegrambots:6.5.0")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("ch.qos.logback:logback-classic:1.4.5")
    implementation("org.xerial:sqlite-jdbc:3.41.0.0")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation(platform("com.aallam.openai:openai-client-bom:3.0.0"))
    implementation("com.aallam.openai:openai-client")
    implementation("io.ktor:ktor-client-okhttp")

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