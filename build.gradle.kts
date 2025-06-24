import org.jreleaser.model.Active
import java.net.URI

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.dokka)
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
val dokkaHtml by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class)

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml.outputDirectory)
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "ArchipelagoGiftingJvm"

            from(components["kotlin"])
            artifact(sourcesJar)
            artifact(javadocJar)
            repositories {
                maven {
                    url = uri(layout.buildDirectory.dir("staging-deploy"))
                }
            }

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
        deploy {
            maven {
                mavenCentral {
                    register("release-deploy") {
                        // Turning off releases; supposed to be turned on via environment variable
                        active = Active.NEVER
                        applyMavenCentralRules = true
                        url = "https://central.sonatype.com/api/v1/publisher"
                        stagingRepository("build/staging-deploy")
                    }
                }
                nexus2 {
                    register("snapshot-deploy") {
                        active = Active.SNAPSHOT
                        applyMavenCentralRules = true
                        snapshotSupported = true
                        closeRepository = true
                        releaseRepository = true
                        url = "https://central.sonatype.com/api/v1/publisher"
                        snapshotUrl = "https://central.sonatype.com/repository/maven-snapshots/"
                        stagingRepository("build/staging-deploy")
                    }
                }
            }
        }
    }
}


tasks.test {
    useJUnitPlatform()
}
