plugins {
    kotlin("jvm") version "1.3.61"
}

group = "ray.eldath"
version = "1.0-SNAPSHOT"

repositories {
    jcenter()
    mavenCentral()
}

val http4kVersion = "3.226.0"
val exposedVersion = "0.20.3"
val micrometerVersion = "1.3.3"

dependencies {
    implementation("com.zaxxer:HikariCP:3.4.2")
    implementation("mysql:mysql-connector-java:8.0.19")
    implementation("de.mkammerer:argon2-jvm:2.6")
    implementation("com.github.ben-manes.caffeine:caffeine:2.8.1")
    implementation("commons-validator:commons-validator:1.6")

    implementation("io.micrometer:micrometer-core:$micrometerVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.http4k:http4k-server-apache:$http4kVersion")
    implementation("org.http4k:http4k-contract:$http4kVersion")
    implementation("org.http4k:http4k-format-jackson:$http4kVersion")
    implementation("org.http4k:http4k-metrics-micrometer:$http4kVersion")

    implementation(kotlin("stdlib-jdk8"))
    testImplementation(kotlin("script-runtime"))
}

listOf(tasks.compileKotlin, tasks.compileTestKotlin).forEach { it.get().kotlinOptions.jvmTarget = "11" }