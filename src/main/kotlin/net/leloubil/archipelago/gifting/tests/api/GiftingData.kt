package net.leloubil.archipelago.gifting.tests.api

import net.leloubil.archipelago.gifting.tests.remote.GiftId
import net.leloubil.archipelago.gifting.tests.remote.GiftTraitName

/**
 * Represents a trait that a giftable item can have.
 *
 * A trait can have a [name], a [quality] (default is 1.0), and a [duration] (default is 1.0).
 *
 * Traits are used by the recipient's game to determine what item in their game this gift corresponds to.
 *
 * See also [Gift Trait Documentation](https://github.com/agilbert1412/Archipelago.Gifting.Net/blob/main/Documentation/Gifting%20API.md#gifttrait-specification)
 */
data class GiftTrait(
    /**
     * The name of the trait.
     * By convention, it should be in PascalCase.
     */
    val name: GiftTraitName,
    /**
     * The quality of the trait.
     * How powerful the trait is, 1.0 means "average power"
     */
    val quality: Float = 1f,
    /**
     * The duration of the trait.
     * How long the trait lasts, 1.0 means "average duration"
     */
    val duration: Float = 1f
) {
    companion object {
        @Suppress("NOTHING_TO_INLINE")
        @JvmStatic
        @JvmName("create")
        inline operator fun invoke(
            name: String,
            quality: Float = 1f,
            duration: Float = 1f
        ): GiftTrait {
            return GiftTrait(GiftTraitName(name), quality, duration)
        }
    }
}


/**
 * Represents an item that can be gifted.
 *
 * A giftable item has a [name], an optional [value], and a list of [traits].
 *
 * The [value] is used if the item can have a monetary or energy value,
 * it is used by some games that support converting gifts into in-game currency or energy.
 *
 * See also [Gift Item Documentation](https://github.com/agilbert1412/Archipelago.Gifting.Net/blob/main/Documentation/Gifting%20API.md#gift-specification)
 **/
data class GiftItem(
    /**
     * The name of the giftable item.
     * Can be used for display purposes or to identify items for same-game gifting.
     */
    val name: String,
    /**
     * The traits of the giftable item.
     * Traits are used by the recipient's game to determine what item in their game this gift corresponds to.
     * @see GiftTrait
     */
    val traits: List<GiftTrait>,
    /**
     * The value of the giftable item.
     * Some games use this to convert gifts into in-game currency or energy.
     */
    val value: Int? = null
)


/**
 * Represents a gift that has been received by a player.
 * A received gift should be interpreted according to its [GiftItem.traits],
 * and can optionally be refunded to the sender.
 **/
data class ReceivedGift(
    /**
     * The unique identifier of the gift.
     * This is used to identify the gift in the gift box and for refunding.
     */
    val id: GiftId,
    /**
     * The item that was gifted.
     */
    val item: GiftItem,
    /**
     * The amount of the item that was gifted.
     */
    val amount: Int,
    /**
     * The slot of the player that sent the gift.
     */
    val senderPlayerSlot: Int,
    /**
     * The team of the player that sent the gift.
     */
    val senderPlayerTeam: Int,
    /**
     * Whether this gift is actually a refund from a gift that was previously sent.
     */
    val isRefund: Boolean,
) {
    @JvmName("getId")
    fun getId(): String {
        return id.id
    }
}
