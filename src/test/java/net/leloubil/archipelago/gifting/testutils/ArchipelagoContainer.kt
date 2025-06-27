package net.leloubil.archipelago.gifting.testutils

import com.google.gson.Gson
import dev.koifysh.archipelago.Client
import dev.koifysh.archipelago.events.ArchipelagoEventListener
import dev.koifysh.archipelago.events.ConnectionResultEvent
import dev.koifysh.archipelago.network.ConnectionResult
import io.kotest.common.runBlocking
import io.kotest.mpp.env
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.junit.AssumptionViolatedException
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

const val ARCHIPELAGO_INTERNAL_PORT = 38281

data class ArchipelagoContainer
@JvmOverloads
constructor(
    val players: List<ArchipelagoPlayer>,
    val version: String = env("TESTS_ARCHIPELAGO_VERSION") ?: "0.6.1"
) : GenericContainer<ArchipelagoContainer>(
    archipelagoImage(
        players,
        version,
        ARCHIPELAGO_INTERNAL_PORT
    )
), AutoCloseable {

    init {
        withStartupAttempts(1)
        withExposedPorts(ARCHIPELAGO_INTERNAL_PORT)
        waitingFor(Wait.forLogMessage("(.*)server listening on(.*)", 2))
        withLogConsumer(Slf4jLogConsumer(LoggerFactory.getLogger("archipelago")))
    }

    val url
        get() = "ws://${host}:${getMappedPort(ARCHIPELAGO_INTERNAL_PORT)}"


    suspend fun playerClient(num: Int): Client = withTimeout(5.seconds) {
        suspendCancellableCoroutine { cont ->
            val player = players.getOrNull(num - 1)
            if (player == null) {
                val exception = AssumptionViolatedException(
                    "Trying to get player $num, but they are only ${players.size} players in this multiworld"
                )
                cont.resumeWithException(exception)
                throw exception
            }
            val c = object : Client() {

                @Suppress("unused")
                @ArchipelagoEventListener
                fun onConnected(event: ConnectionResultEvent) {
                    if (event.result != ConnectionResult.Success) {
                        cont.resumeWithException(
                            AssumptionViolatedException(
                                "Client for player $player failed to connect to the archipelago server: ${event.result}"
                            )
                        )
                        return
                    }
                    println("Client $num connected")
                    cont.resume(this)
                }

                override fun onError(ex: Exception) {
                    println("Error in the AP client for player $num, not failing the test because the client can retry")
                    ex.printStackTrace()
                }

                override fun onClose(Reason: String?, attemptingReconnect: Int) {
                    // ignored
                }
            }

            c.eventManager.registerListener(c)
            c.game = player.game
            c.setName(player.name)
            c.connect(url)
            cont.invokeOnCancellation {
                c.eventManager.unRegisterListener(c)
                c.disconnect()
            }
        }
    }

    fun blockingPlayerClient(num: Int): Client = runBlocking {
        delay(2000) // delay between connections
        playerClient(num)
    }
}

data class ArchipelagoPlayer(val name: String, val game: String, val gameOptions: Map<String, Any> = mapOf()) {
    val yamlFileName = "$name.yaml"
    val yamlContents = """
        name: $name
        game: $game
        $game: ${Gson().toJson(gameOptions)}
    """.trimIndent()
}

fun cliquePlayers(count: Int) =
    (1..count).map {
        ArchipelagoPlayer(
            "Player$it",
            "Clique"
        )
    }

private fun archipelagoImage(
    players: List<ArchipelagoPlayer>,
    version: String,
    port: Int
): ImageFromDockerfile = ImageFromDockerfile().apply {
    players.forEach { player ->
        withFileFromString(
            player.yamlFileName,
            player.yamlContents
        )
    }
}.withDockerfileFromBuilder {
    it.apply {
        val url = "https://github.com/ArchipelagoMW/Archipelago/archive/refs/tags/$version.zip"
        from("python:3.13")
        run("apt update && apt install libarchive-tools wget -y")
        run("mkdir /output")
        workDir("/app")
        run("wget -O /tmp/src.zip '$url' ")
        run("bsdtar xf /tmp/src.zip --strip-components 1 -C /app")
        run("python ModuleUpdate.py --yes")
        players.forEach { p ->
            copy(p.yamlFileName, "/players/${p.yamlFileName}")
        }
        run("python Generate.py --player_files_path /players --outputpath /output")
        entryPoint("python MultiServer.py --port $port /output/AP_*") // a single file should be present
    }
}
