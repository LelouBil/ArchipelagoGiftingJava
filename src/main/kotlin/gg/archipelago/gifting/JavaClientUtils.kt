package gg.archipelago.gifting

import dev.koifysh.archipelago.Client
import dev.koifysh.archipelago.events.RetrievedEvent
import dev.koifysh.archipelago.events.SetReplyEvent
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.properties.Delegates

suspend fun Client.datastorageGet(key: String): Any? = suspendCancellableCoroutine {
    var req by Delegates.notNull<Int>()
    val listener = object {
        fun onDataStorageReceive(evt: RetrievedEvent) {
            if (evt.requestID != req) return
            this@datastorageGet.eventManager.unRegisterListener(this)
            it.resume(evt.data[key])
        }
    }
    this.eventManager.registerListener(listener)
    req = dataStorageGet(listOf(key))
    it.invokeOnCancellation {
        this.eventManager.unRegisterListener(listener)
    }
}

data class DataStorageEventUpdate<T>(val old: T?, val new: T) {
    inline fun <R> map(transform: (T) -> R): DataStorageEventUpdate<R> {
        return DataStorageEventUpdate(old?.let(transform), transform(new))
    }
}

fun Client.dataStorageAsFlow(key: String): Flow<DataStorageEventUpdate<Any>> = callbackFlow {
    var req by Delegates.notNull<Int>();
    val listener = object {

        fun onDataStorageReceive(evt: RetrievedEvent) { // initial value
            if (evt.requestID != req) return;
            evt.data[key]?.let { mb ->
                trySendBlocking(DataStorageEventUpdate(null, mb))
            }
        }

        fun onDataStorageReply(evt: SetReplyEvent) { // subscription handler
            trySendBlocking(DataStorageEventUpdate(evt.original_value, evt.value))
        }
    }
    eventManager.registerListener(listener)
    req = dataStorageGet(listOf(key))
    awaitClose { eventManager.unRegisterListener(listener) }
}
