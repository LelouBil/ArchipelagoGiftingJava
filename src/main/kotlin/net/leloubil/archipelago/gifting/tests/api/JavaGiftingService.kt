package net.leloubil.archipelago.gifting.tests.api

import dev.koifysh.archipelago.Client
import net.leloubil.archipelago.gifting.tests.remote.GiftTraitName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture
import kotlin.jvm.Throws

fun interface ReceivedGiftListener {
    /**
     * Called when a gift is received.
     * @param gift The received gift.
     */
    fun onReceivedGift(gift: ReceivedGift)
}

class GiftSendingException(error: SendGiftResult.SendGiftFailure) : Exception()


/**
 * A wrapper class for using the Gifting API from Java code.
 * It is heavily recommended to use the Kotlin API instead
 */
class JavaGiftingService(client: Client) : AutoCloseable {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val giftingService = DefaultGiftingService(client)
    private val receivedGiftListeners = mutableListOf<ReceivedGiftListener>()

    private var giftListenJob: Job? = null

    init {

    }

    /**
     * Opens the gift box for the player.
     * @param acceptsAnyGifts If true, this will signal to other players that this gift box accepts all gifts, regardless of traits.
     * @param desiredTraits If [acceptsAnyGifts] is false, this will be the list of traits that this gift box accepts.
     * Other games will not usually send gifts with traits that are not in this list.
     * If [acceptsAnyGifts] is true, this list stands for the traits that the game prefers.
     * @return true if the gift box was successfully opened, false otherwise.
     */
    fun openGiftBox(acceptsAnyGifts: Boolean, desiredTraits: List<String>): CompletableFuture<Boolean> =
        coroutineScope.async {
            giftingService.openGiftBox(acceptsAnyGifts, desiredTraits)
        }.asCompletableFuture()

    /**
     * Closes the gift box for the player.
     * @return true if the gift box was successfully closed, false otherwise.
     */
    fun closeGiftBox(): CompletableFuture<Boolean> =
        coroutineScope.async {
            giftingService.closeGiftBox()
        }.asCompletableFuture()

    /**
     * Registers a listener for received gifts.
     * @param listener The listener to register.
     */
    fun registerReceivedGiftListener(listener: ReceivedGiftListener) {
        receivedGiftListeners.add(listener)
    }

    /**
     * Starts listening for received gifts.
     * This is required for listeners to receive notifications about received gifts.
     *
     * **If there are no listeners registered when calling this method, gifts will be lost!**
     *
     * Be sure to register at least one listener before calling this method.
     */
    fun startListeningForGifts(){
        if(giftListenJob != null && giftListenJob!!.isActive) {
            // Already listening for gifts, no need to start again.
            return
        }
        giftListenJob = coroutineScope.launch {
            giftingService.receivedGifts.collect { gift ->
                // Notify all registered listeners about the received gift.
                for (listener in receivedGiftListeners) {
                    listener.onReceivedGift(gift)
                }
            }
        }
    }


    /**
     * Unregisters a listener for received gifts.
     * @param listener The listener to unregister.
     */
    fun unregisterReceivedGiftListener(listener: ReceivedGiftListener) {
        receivedGiftListeners.remove(listener)
    }

    /**
     * Checks if the player can receive a gift with the given traits.
     * @param recipientPlayerSlot The slot of the player to send the gift to.
     * @param recipientPlayerTeam The team of the player to send the gift to.
     * @param giftTraits The traits to check
     * @return true if the player can receive the gift, false otherwise.
     */
    fun canGiftToPlayer(
        recipientPlayerSlot: Int,
        recipientPlayerTeam: Int,
        giftTraits: Collection<GiftTraitName> = emptyList()
    ): CompletableFuture<Boolean> =
        coroutineScope.async {
            val res = giftingService.canGiftToPlayer(recipientPlayerSlot, recipientPlayerTeam, giftTraits)
            return@async res is CanGiftResult.CanGiftSuccess
        }.asCompletableFuture()

    /**
     * Sends a gift to the player with the given slot and team.
     * @param item The item to send.
     * @param amount The amount of the item to send.
     * @param recipientPlayerSlot The slot of the player to send the gift to.
     * @param recipientPlayerTeam The team of the player to send the gift to
     * @throws GiftSendingException if the gift could not be sent.
     */
    @Throws(GiftSendingException::class)
    fun sendGift(
        item: GiftItem,
        amount: Int,
        recipientPlayerSlot: Int,
        recipientPlayerTeam: Int,
    ): CompletableFuture<Unit> = coroutineScope.async {
        val result = giftingService.sendGift(item, amount, recipientPlayerSlot, recipientPlayerTeam)
        if (result is SendGiftResult.SendGiftFailure) {
            throw GiftSendingException(result)
        }
    }.asCompletableFuture()

    /**
     * Refunds a gift that was received.
     * @param receivedGift The gift to refund.
     * @throws GiftSendingException if the gift could not be refunded.
     */
    @Throws(GiftSendingException::class)
    fun refundGift(receivedGift: ReceivedGift): CompletableFuture<Unit> = coroutineScope.async {
        val result = giftingService.refundGift(receivedGift)
        if (result is SendGiftResult.SendGiftFailure) {
            throw GiftSendingException(result)
        }
    }.asCompletableFuture()

    /**
     * Closes the gift box and cancels all ongoing operations.
     */
    override fun close() {
        coroutineScope.launch {
            giftingService.closeGiftBox()
            coroutineScope.cancel()
        }
    }

}
