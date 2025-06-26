package net.leloubil.archipelago.gifting.testutils

import dev.koifysh.archipelago.Client
import dev.koifysh.archipelago.events.ArchipelagoEventListener
import dev.koifysh.archipelago.events.ConnectionResultEvent
import dev.koifysh.archipelago.network.ConnectionResult
import io.kotest.core.TestConfiguration
import io.kotest.core.spec.Spec
import io.kotest.extensions.testcontainers.perTest
import io.kotest.mpp.env
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.AssumptionViolatedException
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.lifecycle.Startable
import java.lang.Exception
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

data class ArchipelagoContainer(
    private val container: GenericContainer<*>,
    private val names: List<String>,
    private val internalPort: Int
) :
    Startable by container {
    val url
        get() = "ws://${container.host}:${container.getMappedPort(internalPort)}"

    override fun getDependencies(): Set<Startable?>? {
        return container.getDependencies()
    }

    override fun close() {
        container.close()
    }


    suspend fun playerClient(num: Int): Client = withTimeout(5.seconds) {
        suspendCancellableCoroutine { cont ->
            val name = names.getOrNull(num - 1)
            if (name == null) {
                throw AssumptionViolatedException(
                    "Trying to get player $num, but they are only ${names.size} players in this multiworld"
                )
            }
            val c = object : Client() {

                @Suppress("unused")
                @ArchipelagoEventListener
                fun onConnected(event: ConnectionResultEvent) {
                    if (event.result != ConnectionResult.Success) {
                        throw AssumptionViolatedException(
                            "Client for player $name failed to connect to the archipelago server: ${event.result}"
                        )
                    }
                    cont.resume(this)
                }

                override fun onError(ex: Exception?) {
                    throw AssumptionViolatedException("Archipelago client for player $name had an error", ex)

                }

                override fun onClose(Reason: String?, attemptingReconnect: Int) {
                    // ignored
                }
            }

            c.eventManager.registerListener(c)
            c.game = "Clique"
            c.setName(name)
            c.connect(url)
            cont.invokeOnCancellation {
                c.eventManager.unRegisterListener(c)
                c.disconnect()
            }
        }
    }
}

fun Spec.archipelagoContainer(wantedVersion: String? = null, playersCount: Int = 1): ArchipelagoContainer {
    val version = wantedVersion ?: env("TESTS_ARCHIPELAGO_VERSION") ?: "0.6.1"
    val port = 38281
    val url = "https://github.com/ArchipelagoMW/Archipelago/archive/refs/tags/$version.zip"
//     to debug container build failures
//     System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG");

    val playerNames = mutableListOf<String>()
    val image = ImageFromDockerfile().apply {
        1.rangeTo(playersCount).forEach { playerID ->
            val playerName = "Player$playerID"
            playerNames.add(playerName)
            withFileFromString(
                "$playerName.yaml",
                // language=yaml
                """
                name: $playerName
                game: Clique
                Clique: {}
            """.trimIndent()
            )

        }
    }.withDockerfileFromBuilder {
        it.apply {
            from("python:3.13")
            run("apt update && apt install libarchive-tools wget -y")
            run("mkdir /output")
            workDir("/app")
            run("wget -O /tmp/src.zip '$url' ")
            run("bsdtar xf /tmp/src.zip --strip-components 1 -C /app")
            run("python ModuleUpdate.py --yes")
            playerNames.forEach { name ->
                copy("$name.yaml", "/players/$name.yaml")
            }
            run("python Generate.py --player_files_path /players --outputpath /output")
            entryPoint("python MultiServer.py --port $port /output/AP_*")
        }
    }

    val container = GenericContainer(image)
        .withStartupAttempts(1)
        .withExposedPorts(port)
        .waitingFor(Wait.forListeningPort())
        .withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("archipelago")))
    val cont = ArchipelagoContainer(container, playerNames, port)
    register(cont.perTest())
    return cont
}
