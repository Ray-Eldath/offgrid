plugins {
    kotlin("jvm") version "1.3.61"
    id("application")
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("nu.studer.jooq") version "4.1"
}

group = "ray.eldath"
version = "1.0-SNAPSHOT"

val http4kVersion = "3.226.0"
val micrometerVersion = "1.3.3"
val logbackVersion = "1.2.3"
val striktVersion = "0.23.4"
val jupiterVersion = "5.6.0"
val databaseConnector = "mysql:mysql-connector-java:8.0.19"

allprojects {
    apply(plugin = "nu.studer.jooq")
    apply(plugin = "kotlin")

    repositories {
        jcenter()
        mavenCentral()
    }

    dependencies {
        jooqRuntime(databaseConnector)
        implementation(databaseConnector)
        implementation("org.jooq:jooq")
        implementation("org.jooq:jooq-codegen")

        implementation(kotlin("stdlib-jdk8"))
    }
}

dependencies {
    jooqRuntime(project(":preBuild"))
    implementation("com.aliyun:aliyun-java-sdk-core:4.4.9")
    implementation("com.aliyun:aliyun-java-sdk-dm:3.3.1")
    implementation("com.zaxxer:HikariCP:3.4.2")
    implementation("de.mkammerer:argon2-jvm:2.6")
    implementation("com.github.ben-manes.caffeine:caffeine:2.8.1")
    implementation("commons-validator:commons-validator:1.6")
    implementation("org.codehaus.groovy:groovy:3.0.0-rc-3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.3")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("ch.qos.logback:logback-core:$logbackVersion")
    implementation("io.micrometer:micrometer-core:$micrometerVersion")
    implementation("io.micrometer:micrometer-registry-graphite:$micrometerVersion")
    implementation("org.http4k:http4k-core:$http4kVersion")
    implementation("org.http4k:http4k-server-apache:$http4kVersion")
    implementation("org.http4k:http4k-client-okhttp:$http4kVersion")
    implementation("org.http4k:http4k-contract:$http4kVersion")
    implementation("org.http4k:http4k-format-jackson:$http4kVersion")
    implementation("org.http4k:http4k-metrics-micrometer:$http4kVersion")

    //

    testImplementation("io.strikt:strikt-core:$striktVersion")
    testImplementation("io.strikt:strikt-jackson:$striktVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")

    testImplementation(kotlin("script-runtime"))
}

listOf(tasks.compileKotlin, tasks.compileTestKotlin).forEach { it.get().kotlinOptions.jvmTarget = "11" }

tasks.test {
    useJUnitPlatform()

    exclude("**/singleshot/**")
}

application { mainClassName = "ray.eldath.offgrid.core.Core" }

apply(from = "jooq.gradle")