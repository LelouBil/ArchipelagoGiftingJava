package gg.archipelago.gifting.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

const val LibraryDataVersion = 3

@JvmInline
@Serializable
value class GiftId(val id: String) {
    companion object {
        fun new(): GiftId {
            return GiftId(UUID.randomUUID().toString())
        }
    }
}

@JvmInline
@Serializable
value class GiftTraitName(val name: String)

// https://github.com/agilbert1412/Archipelago.Gifting.Net/blob/main/Documentation/Gifting%20API.md#giftbox
fun getMotherBoxKey(team: Int) : String {
    return "GiftBoxes;$team"
}

fun getPlayerGiftBoxKey(playerTeam: Int, playerSlot: Int): String {
    return "GiftBox;$playerTeam;$playerSlot"
}

// https://github.com/agilbert1412/Archipelago.Gifting.Net/blob/main/Documentation/Gifting%20API.md#gift-specification
@Serializable
data class GiftEntry(
    @SerialName("id")
    val id: GiftId,
    @SerialName("item_name")
    val name: String,
    @SerialName("amount")
    val amount: Int,
    @SerialName("item_value")
    val valuePerUnit: Int?,
    @SerialName("traits")
    val traits: List<GiftTraitEntry>,
    @SerialName("sender_slot")
    val senderPlayerSlot: Int,
    @SerialName("sender_team")
    val senderPlayerTeam: Int,
    @SerialName("receiver_slot")
    val recipientPlayerSlot: Int,
    @SerialName("receiver_team")
    val recipientPlayerTeam: Int,
    @SerialName("is_refund")
    val isRefund: Boolean
)

// https://github.com/agilbert1412/Archipelago.Gifting.Net/blob/main/Documentation/Gifting%20API.md#gifttrait-specification
@Serializable
data class GiftTraitEntry(
    @SerialName("trait")
    val name: GiftTraitName,
    @SerialName("quality")
    val quality: Float = 1f,
    @SerialName("duration")
    val duration: Float = 1f
)

// https://github.com/agilbert1412/Archipelago.Gifting.Net/blob/main/Documentation/Gifting%20API.md#giftbox-metadata-specification
@Serializable
data class GiftBoxDescriptor(
    @SerialName("is_open")
    val isOpen: Boolean,
    @SerialName("accepts_any_gift")
    val acceptsAnyGift: Boolean,
    @SerialName("desired_traits")
    val desiredTraits: List<GiftTraitName>,
    @SerialName("minimum_gift_data_version")
    val minimumGiftDataVersion: Int,
    @SerialName("maxiumum_gift_data_version")
    val maximumGiftDataVersion: Int,
)
typealias MotherBox = Map<Int, GiftBoxDescriptor>
typealias PlayerGiftBox = Map<GiftId, GiftEntry>



