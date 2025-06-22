package gg.archipelago.gifting

import com.google.gson.Gson
import dev.koifysh.archipelago.Client
import dev.koifysh.archipelago.events.ArchipelagoEventListener
import dev.koifysh.archipelago.events.RetrievedEvent
import dev.koifysh.archipelago.events.SetReplyEvent
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
inline fun <reified T> convertFromGson(data: Any): T {
    val json = Gson().toJson(data)
    return Gson().fromJson<T>(json, typeOf<T>().javaType)
}

suspend inline fun <reified T> Client.datastorageGet(key: String): T? = suspendCancellableCoroutine {
    var req by Delegates.notNull<Int>()
    val listener = object {
        @ArchipelagoEventListener
        fun onDataStorageReceive(evt: RetrievedEvent) {
            if (evt.requestID != req) return
            this@datastorageGet.eventManager.unRegisterListener(this)
            it.resume(evt.data[key]?.let { convertFromGson(it) })
        }
    }
    this.eventManager.registerListener(listener)
    req = dataStorageGet(listOf(key))
    if (req == 0) {
        //todo throw IllegalStateException("Failed to send data storage get request for key: $key")
    }
    it.invokeOnCancellation {
        this.eventManager.unRegisterListener(listener)
    }
}

data class DataStorageEventUpdate<T>(val old: T?, val new: T) {
    inline fun <R> map(transform: (T) -> R): DataStorageEventUpdate<R> {
        return DataStorageEventUpdate(old?.let(transform), transform(new))
    }
}

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T> Client.dataStorageAsFlow(key: String): Flow<DataStorageEventUpdate<T>> = callbackFlow {
    var req by Delegates.notNull<Int>();
    val listener = object {

        @ArchipelagoEventListener
        fun onDataStorageReceive(evt: RetrievedEvent) { // initial value
            if (evt.requestID != req) return;
            evt.data[key]?.let { mb ->
                trySendBlocking(DataStorageEventUpdate<T>(null, convertFromGson(mb)))
            }
        }

        @ArchipelagoEventListener
        fun onDataStorageReply(evt: SetReplyEvent) { // subscription handler
            if (evt.key != key) return
            val old_value = evt.original_value?.let { convertFromGson<T>(it) }
            val new_value = convertFromGson<T>(evt.value)
            trySendBlocking(
                DataStorageEventUpdate(
                    old_value,
                    new_value
                )
            )
        }
    }
    eventManager.registerListener(listener)
    dataStorageSetNotify(listOf(key))
    req = dataStorageGet(listOf(key))
    if (req == 0) {
        //todo throw IllegalStateException("Failed to send data storage get request for key: $key")
    }
    awaitClose { eventManager.unRegisterListener(listener) }
}
