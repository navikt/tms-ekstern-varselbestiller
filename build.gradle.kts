import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin on the JVM.
    kotlin("jvm").version(Kotlin.version)
    kotlin("plugin.allopen").version(Kotlin.version)

    id(Flyway.pluginId) version (Flyway.version)
    id(Shadow.pluginId) version (Shadow.version)

    // Apply the application plugin to add support for building a CLI application.
    application
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven")
    maven ( "https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven" )
    mavenLocal()
    maven("https://jitpack.io")
}

sourceSets {
    create("intTest") {
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    }
}

dependencies {
    implementation("com.github.navikt:brukernotifikasjon-schemas-internal:1.2022.01.20-12.20-9c37cb170dc9")
    implementation(DittNAV.Common.influxdb)
    implementation(DittNAV.Common.utils)
    implementation(Doknotifikasjon.schemas)
    implementation(Flyway.core)
    implementation(Hikari.cp)
    implementation(Influxdb.java)
    implementation(Kafka.Apache.clients)
    implementation(Kafka.Confluent.avroSerializer)
    implementation(Ktor.htmlBuilder)
    implementation(Ktor.serverNetty)
    implementation(Logback.classic)
    implementation(Logstash.logbackEncoder)
    implementation(Postgresql.postgresql)
    implementation(Prometheus.common)
    implementation(Prometheus.hotspot)
    implementation(Prometheus.logback)
    implementation("com.github.navikt:rapids-and-rivers:20210617121814-3e67e4d")

    testImplementation(Junit.api)
    testImplementation(Junit.engine)
    testImplementation(Junit.params)
    testImplementation(Kafka.Apache.kafka_2_12)
    testImplementation(Kafka.Apache.streams)
    testImplementation(Kafka.Confluent.schemaRegistry)
    testImplementation(Kotlinx.atomicfu)
    testImplementation(Ktor.clientMock)
    testImplementation(Mockk.mockk)
    testImplementation(NAV.kafkaEmbedded)
    testImplementation(TestContainers.postgresql)
    testImplementation(Kotest.runnerJunit5)
    testImplementation(Kotest.assertionsCore)
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            events("passed", "skipped", "failed")
        }
    }

    register("runServer", JavaExec::class) {
        environment("KAFKA_BROKERS", "localhost:29092")
        environment("KAFKA_SCHEMA_REGISTRY", "http://localhost:8081")
        environment("GROUP_ID", "dittnav_varselbestiller")
        environment("DB_HOST", "localhost")
        environment("DB_PORT", "5432")
        environment("DB_DATABASE", "dittnav-varselbestiller")
        environment("DB_USERNAME", "testuser")
        environment("DB_PASSWORD", "testpassword")
        environment("NAIS_CLUSTER_NAME", "dev-gcp")
        environment("NAIS_NAMESPACE", "dev")

        main = application.mainClass.get()
        classpath = sourceSets["main"].runtimeClasspath
    }
}

// TODO: Fjern følgende work around i ny versjon av Shadow-pluginet:
// Skal være løst i denne: https://github.com/johnrengelman/shadow/pull/612
project.setProperty("mainClassName", application.mainClass.get())
apply(plugin = Shadow.pluginId)
