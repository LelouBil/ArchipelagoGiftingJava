package net.leloubil.archipelago.gifting.tests.api

import dev.koifysh.archipelago.Client
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import net.leloubil.archipelago.gifting.tests.remote.GiftTraitName

/**
 * Represents the result of checking if a player can receive a gift.
 */
sealed interface CanGiftResult {

    /**
     * The player can receive the gift.
     */
    sealed interface CanGiftSuccess : CanGiftResult {
        data object AcceptsAnyGifts : CanGiftSuccess
        data class AcceptedTraits(val matchingTraits: Set<GiftTraitName>) : CanGiftSuccess
    }

    /**
     * The player cannot receive the gift.
     */
    sealed interface CanGiftError : CanGiftResult {

        /**
         * The recipient's gift box is closed.
         */
        data object GiftBoxClosed : CanGiftError

        /**
         * The recipient doesn't have a gift box
         */
        data object NoGiftBox : CanGiftError

        /**
         * The data version of the sender is too low to send gifts to the recipient
         */
        data class DataVersionTooLow(val recipientMinimumVersion: Int) : CanGiftError

        /**
         * The recipient does not accept the gift because it does not match any of their accepted traits.
         */
        data class NoMatchingTraits(val recipientAcceptedTraits: Collection<GiftTraitName>) : CanGiftError

        /**
         * There was an error while trying to acquire the recipient's gift box data.
         */
        data object DataStorageWriteError : CanGiftError
    }
}

/**
 * Represents the result of sending a gift.
 */
sealed interface SendGiftResult {
    /**
     * The gift was successfully sent.
     */
    data object SendGiftSuccess : SendGiftResult

    /**
     * The gift could not be sent.
     */
    sealed interface SendGiftFailure : SendGiftResult {
        /**
         * The gift could not be sent because the recipient does not accept the gift.
         * The reason is detailed in the [reason] property.
         */
        data class CannotGift(val reason: CanGiftResult.CanGiftError) : SendGiftResult

        /**
         * There was an error while trying to write the gift to the recipient's gift box.
         */
        data object DataStorageWriteError : SendGiftFailure
    }
}

interface GiftingService {

    /**
     * Opens the gift box for the player.
     * @param acceptsAnyGifts If true, this will signal to other players that this gift box accepts all gifts, regardless of traits.
     * @param desiredTraits If [acceptsAnyGifts] is false, this will be the list of traits that this gift box accepts.
     * Other games will not usually send gifts with traits that are not in this list.
     * If [acceptsAnyGifts] is true, this list stands for the traits that the game prefers.
     * @return true if the gift box was successfully opened, false otherwise.
     */
    suspend fun openGiftBox(acceptsAnyGifts: Boolean, desiredTraits: List<String>): Boolean

    /**
     * Closes the gift box for the player.
     * @return true if the gift box was successfully closed, false otherwise.
     */
    suspend fun closeGiftBox(): Boolean


    /**
     * The flow that emits received gifts.
     * The gifts will be removed from the gift box before being emitted in this flow.
     * This flow will emit gifts in the order they were received.
     */
    val receivedGifts: Flow<ReceivedGift>

    /**
     * Returns the current contents of the gift box, without any processing
     */
    suspend fun getGiftBoxContents(): List<ReceivedGift>

    /**
     * Removes the gift from the gift box
     */
    suspend fun removeGiftFromBox(receivedGift: ReceivedGift): Boolean

    /**
     * Checks if the player can receive a gift with the given traits.
     * @param recipientPlayerSlot The slot of the player to send the gift to.
     * @param recipientPlayerTeam The team of the player to send the gift to.
     * @param giftTraits The traits to check
     * @return A [CanGiftResult] indicating whether the player can receive the gift or not.
     */
    suspend fun canGiftToPlayer(
        recipientPlayerSlot: Int,
        recipientPlayerTeam: Int,
        giftTraits: Collection<GiftTraitName> = emptyList()
    ): CanGiftResult

    /**
     * Sends a gift to the player with the given slot and team.
     * @param item The item to send.
     * @param amount The amount of the item to send.
     * @param recipientPlayerSlot The slot of the player to send the gift to.
     * @param recipientPlayerTeam The team of the player to send the gift to
     * @return A [SendGiftResult] indicating whether the gift was sent successfully or not.
     */
    suspend fun sendGift(
        item: GiftItem,
        amount: Int,
        recipientPlayerSlot: Int,
        recipientPlayerTeam: Int,
    ): SendGiftResult

    /**
     * Refunds a gift that was received.
     * @param receivedGift The gift to refund.
     * @return A [SendGiftResult] indicating whether the refund was successful or not.
     */
    suspend fun refundGift(receivedGift: ReceivedGift): SendGiftResult

    companion object {
        /**
         * Creates a new instance of [GiftingService].
         * @param client The Archipelago client to use for communication.
         * @param coroutineScope The coroutine scope to use for listening to archipelago events
         * @return A new instance of [GiftingService].
         */
        operator fun invoke(
            client: Client,
            coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
        ) =
            DefaultGiftingService(client, coroutineScope)
    }
}
