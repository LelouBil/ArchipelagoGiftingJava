import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jreleaser.model.Active
import java.util.Optional

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    `maven-publish`
    alias(libs.plugins.jreleaser)
}

group = "net.leloubil"
version = "1.3.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.archipelagoJavaClient)
    implementation(libs.kotlinx.coroutines)
    implementation(libs.gson)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.junit)
    testImplementation(libs.junit.testcontainers)
    testImplementation(libs.slf4j.simple)
}

kotlin {
    compilerOptions{
        freeCompilerArgs.add("-Xcontext-parameters")
        jvmTarget = JvmTarget.JVM_1_8 // should be the default but just in case
    }
}
java {
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.test {
    useJUnitPlatform()
    java {
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
}

val sourcesJar by tasks.named("kotlinSourcesJar")
val dokkaGenerateModuleJavadoc by tasks.getting(org.jetbrains.dokka.gradle.tasks.DokkaGenerateModuleTask::class)

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    dependsOn(dokkaGenerateModuleJavadoc)
    archiveClassifier.set("javadoc")
    from(dokkaGenerateModuleJavadoc.outputDirectory)
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = "archipelago-gifting-jvm"

            from(components["kotlin"])
            artifact(sourcesJar)
            artifact(javadocJar)
            repositories {
                maven {
                    url = uri(layout.buildDirectory.dir("staging-deploy"))
                }
            }

            pom {
                name = "archipelago-gifting-jvm"
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
                    url = "https://github.com/LelouBil/ArchipelagoGiftingJvm"
                }
            }
        }
    }
}

@Suppress("UnstableApiUsage")
fun activeDeploy(provider: Provider<String>): Provider<Active> {
    // Releases turned on by environment variable
    return provider.map { v ->
        Active.values().firstOrNull { v.uppercase() == it.name }.let { Optional.ofNullable(it) }
    }.filter(Optional<Active>::isPresent).map(Optional<Active>::get)
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
                        // Releases turned on by environment variable
                        active = activeDeploy(
                            providers.environmentVariable(
                                "JRELEASER_DEPLOY_MAVEN_MAVENCENTRAL_RELEASE_DEPLOY_ACTIVE"
                            )
                        ).orElse(Active.NEVER)
                        applyMavenCentralRules = true
                        url = "https://central.sonatype.com/api/v1/publisher"
                        stagingRepository("build/staging-deploy")
                    }
                }
                nexus2 {
                    register("snapshot-deploy") {
                        active = activeDeploy(
                            providers.environmentVariable(
                                "JRELEASER_DEPLOY_MAVEN_MAVENCENTRAL_SNAPSHOT_DEPLOY_ACTIVE"
                            )
                        ).orElse(Active.NEVER)
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


