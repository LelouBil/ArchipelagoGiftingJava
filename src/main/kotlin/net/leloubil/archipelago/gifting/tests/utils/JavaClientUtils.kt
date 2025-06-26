package net.leloubil.archipelago.gifting.tests.utils

import com.google.gson.Gson
import dev.koifysh.archipelago.Client
import dev.koifysh.archipelago.events.ArchipelagoEventListener
import dev.koifysh.archipelago.events.RetrievedEvent
import dev.koifysh.archipelago.events.SetReplyEvent
import dev.koifysh.archipelago.network.client.SetPacket
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.properties.Delegates
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

@OptIn(ExperimentalStdlibApi::class)
internal inline fun <reified T> convertFromGson(data: Any): T {
    val json = Gson().toJson(data)
    return Gson().fromJson<T>(json, typeOf<T>().javaType)
}

internal suspend inline fun <reified T> Client.getDataStorage(key: String): T? =
    suspendCancellableCoroutine { continuation ->
        var req by Delegates.notNull<Int>()
        val listener = object {

            @Suppress("unused")
            @ArchipelagoEventListener
            fun onDataStorageReceive(evt: RetrievedEvent) {
                if (evt.requestID != req) return
                eventManager.unRegisterListener(this)
                continuation.resume(evt.data[key]?.let { convertFromGson(it) })
            }
        }

        this.eventManager.registerListener(listener)
        req = dataStorageGet(listOf(key))
        if (req == 0) {
            //todo throw IOException("Failed to send data storage get request for key: $key")
            // current java client can't differentiate between a failed request and the first request
        }
        continuation.invokeOnCancellation {
            this.eventManager.unRegisterListener(listener)
        }
    }


internal suspend inline fun Client.setDataStorage(packet: SetPacket) =
    suspendCancellableCoroutine { continuation ->
        var req: Int? = null
        val listener = object {

            @Suppress("unused")
            @ArchipelagoEventListener
            fun onSetReply(evt: SetReplyEvent) {
                if (req == null || evt.requestID != req) return
                eventManager.unRegisterListener(this)
                continuation.resume(req)
            }
        }

        this.eventManager.registerListener(listener)
        req = dataStorageSet(packet.apply {
            this.want_reply = true
        })
        if (req == 0) {
            //todo throw IOException("Failed to send data storage get request for key: $key")
            // current java client can't differentiate between a failed request and the first request
        }
        continuation.invokeOnCancellation {
            this.eventManager.unRegisterListener(listener)
        }
    }

internal data class DataStorageEventUpdate<T>(val old: T?, val new: T) {
    inline fun <R> map(transform: (T) -> R): DataStorageEventUpdate<R> {
        return DataStorageEventUpdate(old?.let(transform), transform(new))
    }
}

@OptIn(ExperimentalStdlibApi::class)
internal inline fun <reified T> Client.dataStorageAsFlow(key: String): Flow<DataStorageEventUpdate<T>> = callbackFlow {
    val listener = object {
        @Suppress("unused")
        @ArchipelagoEventListener
        fun onDataStorageReply(evt: SetReplyEvent) { // subscription handler
            if (evt.key != key) return
            val old = evt.original_value?.let { convertFromGson<T>(it) }
            val new = convertFromGson<T>(evt.value)
            trySendBlocking(DataStorageEventUpdate(old, new))
        }
    }
    eventManager.registerListener(listener)
    dataStorageSetNotify(listOf(key))
    val resp = getDataStorage<T>(key)
    if (resp != null)
        trySend(DataStorageEventUpdate<T>(null, resp))
//    if (resp == null) {
    //todo throw IllegalStateException("Failed to send data storage get request for key: $key")
    // current java client can't differentiate between a failed request and the first request
//    }
    awaitClose { eventManager.unRegisterListener(listener) }
}
