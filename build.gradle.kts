plugins {
    kotlin("jvm") version "1.3.61"
    id("application")
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("nu.studer.jooq") version "4.1"
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
val logbackVersion = "1.2.3"
val jupiterVersion = "5.6.0"

dependencies {
    jooqRuntime("mysql:mysql-connector-java:8.0.19")
    implementation("com.zaxxer:HikariCP:3.4.2")
    implementation("de.mkammerer:argon2-jvm:2.6")
    implementation("com.github.ben-manes.caffeine:caffeine:2.8.1")
    implementation("commons-validator:commons-validator:1.6")
    implementation("org.codehaus.groovy:groovy:3.0.0-rc-3")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("ch.qos.logback:logback-core:$logbackVersion")
    implementation("io.micrometer:micrometer-core:$micrometerVersion")
    implementation("org.jooq:jooq")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.http4k:http4k-server-apache:$http4kVersion")
    implementation("org.http4k:http4k-contract:$http4kVersion")
    implementation("org.http4k:http4k-format-jackson:$http4kVersion")
    implementation("org.http4k:http4k-metrics-micrometer:$http4kVersion")

    implementation(kotlin("stdlib-jdk8"))

    //

    testImplementation("io.strikt:strikt-core:0.23.4")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")

    testImplementation(kotlin("script-runtime"))
}

listOf(tasks.compileKotlin, tasks.compileTestKotlin).forEach { it.get().kotlinOptions.jvmTarget = "11" }

tasks.test { useJUnitPlatform() }

application { mainClassName = "ray.eldath.offgrid.core.Core" }

apply(from = "jooq.gradle")