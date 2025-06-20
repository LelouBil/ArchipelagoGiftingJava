import java.net.URI

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinx.serialization)
}

group = "gg.archipelago"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "Central Portal Snapshots"
        url = URI("https://central.sonatype.com/repository/maven-snapshots/")

        // Only search this repository for the specific dependency
        content {
            includeModule("io.github.archipelagomw", "Java-Client")
        }
    }
}

dependencies {
    implementation(libs.archipelagoJavaClient)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnitPlatform()
}
