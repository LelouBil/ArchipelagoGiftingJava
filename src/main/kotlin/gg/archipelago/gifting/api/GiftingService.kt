package gg.archipelago.gifting.api

import gg.archipelago.gifting.remote.GiftTraitName

sealed interface CanGiftResult {

    sealed interface CanGiftSuccess : CanGiftResult {
        data object AcceptsAnyGifts : CanGiftSuccess
        data class AcceptedTraits(val matchingTraits: Set<GiftTraitName>) : CanGiftSuccess
    }

    sealed interface CanGiftError : CanGiftResult {
        data class PlayerSlotNotFound(val playerSlot: Int) : CanGiftError

        //
        data object GiftBoxClosed : CanGiftError

        // The data version of the sender is too low to send gifts to the recipient
        data class DataVersionTooLow(val recipientMinimumVersion: Int) : CanGiftError

        // The recipient doesn't accept all gifts, and the gift traits don't contain any traits that the recipient accepts
        data class NoMatchingTraits(val recipientAcceptedTraits: Collection<GiftTraitName>) : CanGiftError

        // could be a network error
        data object DataStorageWriteError : CanGiftError
    }
}

sealed interface SendGiftResult {
    data object SendGiftSuccess : SendGiftResult
    sealed interface SendGiftFailure : SendGiftResult {
        data class CannotGift(val reason: CanGiftResult.CanGiftError) : SendGiftResult
        data object  DataStorageWriteError : SendGiftFailure
    }
}

interface GiftingService {
    val myGiftBoxKey: String

    suspend fun openGiftBox()
    suspend fun openGiftBox(acceptsAnyGifts: Boolean, desiredTraits: List<String>)

    suspend fun closeGiftBox()

    suspend fun canGiftToPlayer(
        recipientPlayerSlot: Int,
        recipientPlayerTeam: Int? = null,
        giftTraits: Collection<GiftTraitName> = emptyList()
    ): CanGiftResult

    suspend fun sendGift(
        item: GiftItem,
        amount: Int,
        recipientPlayerSlot: Int,
        recipientPlayerTeam: Int? = null,
    ): SendGiftResult

    suspend fun refundGift(
        receivedGift: ReceivedGift
    ): SendGiftResult

}
