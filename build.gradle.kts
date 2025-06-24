import org.jreleaser.model.Active
import java.net.URI

plugins {
    alias(libs.plugins.kotlin)
    `maven-publish`
    alias(libs.plugins.jreleaser)
}

group = "net.leloubil"
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
            artifactId = "ArchipelagoGiftingJvm"

            from(components["kotlin"])
            artifact(sourcesJar)

            pom {
                name = "ArchipelagoGiftingJvm"
                description = "Archipelago Gifting API for JVM"
                url = "https://github.com/LelouBil/ArchipelagoGiftingJvm"
                licenses {
                    license {
                        name = "MIT"
                    }
                }
                developers {
                    developer {
                        id = "LelouBil"
                    }
                }
                scm {
                    url = "http://github.com/LelouBil/ArchipelagoGiftingJvm"
                }
            }
        }
    }
}

jreleaser {
    project {
        authors = listOf("LelouBil")
        license = "MIT"
    }

    release {
        github {
            repoOwner = "LelouBil"
            overwrite = true
        }
    }

    signing {
        active = Active.ALWAYS
        armored = true
    }
    deploy {
        maven {
            mavenCentral {
                create("sonatype") {
                    active = Active.RELEASE
                    url = "https://central.sonatype.com/api/v1/publisher"
                    stagingRepository("target/staging-deploy")
                }
            }
            nexus2 {
                create("snapshot-deploy") {
                    active = Active.SNAPSHOT
                    snapshotUrl = "{central_snapshot_url}"
                    applyMavenCentralRules = true
                    snapshotSupported = true
                    closeRepository = true
                    releaseRepository = true
                    stagingRepository("target/staging-deploy")
                }
            }
        }
    }

}


tasks.test {
    useJUnitPlatform()
}
