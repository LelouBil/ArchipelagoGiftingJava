import java.net.URI

plugins {
    alias(libs.plugins.kotlin)
    `maven-publish`
}

group = "net.leloubil.archipelago"
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
    implementation(libs.kotlinx.coroutines)
    implementation(libs.gson)
    testImplementation(libs.junit)
}
val sourcesJar by tasks.named("kotlinSourcesJar")


publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "gifting-jvm"
            version = project.version.toString()

            from(components["kotlin"])
            artifact(sourcesJar)
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
