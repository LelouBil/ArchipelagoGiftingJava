package net.leloubil.archipelago.gifting.tests.remote

import com.google.gson.annotations.SerializedName
import java.util.UUID

const val LibraryDataVersion = 3

@JvmInline
value class GiftId(val id: String) {
    companion object {
        fun new(): GiftId {
            return GiftId(UUID.randomUUID().toString())
        }
    }
}

@JvmInline
value class GiftTraitName(val name: String)

// https://github.com/agilbert1412/Archipelago.Gifting.Net/blob/main/Documentation/Gifting%20API.md#giftbox
internal fun getMotherBoxKey(team: Int) : String {
    return "GiftBoxes;$team"
}

internal fun getPlayerGiftBoxKey(playerTeam: Int, playerSlot: Int): String {
    return "GiftBox;$playerTeam;$playerSlot"
}

// https://github.com/agilbert1412/Archipelago.Gifting.Net/blob/main/Documentation/Gifting%20API.md#gift-specification
internal data class GiftEntry(
    @SerializedName("id")
    val id: String,
    @SerializedName("item_name")
    val name: String,
    @SerializedName("amount")
    val amount: Int,
    @SerializedName("item_value")
    val valuePerUnit: Int?,
    @SerializedName("traits")
    val traits: List<GiftTraitEntry>,
    @SerializedName("sender_slot")
    val senderPlayerSlot: Int,
    @SerializedName("sender_team")
    val senderPlayerTeam: Int,
    @SerializedName("receiver_slot")
    val recipientPlayerSlot: Int,
    @SerializedName("receiver_team")
    val recipientPlayerTeam: Int,
    @SerializedName("is_refund")
    val isRefund: Boolean
)

// https://github.com/agilbert1412/Archipelago.Gifting.Net/blob/main/Documentation/Gifting%20API.md#gifttrait-specification
internal data class GiftTraitEntry(
    @SerializedName("trait")
    val name: String,
    @SerializedName("quality")
    val quality: Float,
    @SerializedName("duration")
    val duration: Float,
)

// https://github.com/agilbert1412/Archipelago.Gifting.Net/blob/main/Documentation/Gifting%20API.md#giftbox-metadata-specification
internal data class GiftBoxDescriptor(
    @SerializedName("is_open")
    val isOpen: Boolean,
    @SerializedName("accepts_any_gift")
    val acceptsAnyGift: Boolean,
    @SerializedName("desired_traits")
    val desiredTraits: List<String>,
    @SerializedName("minimum_gift_data_version")
    val minimumGiftDataVersion: Int,
    @SerializedName("maximum_gift_data_version")
    val maximumGiftDataVersion: Int,
)
internal typealias MotherBox = Map<Int, GiftBoxDescriptor>
internal typealias PlayerGiftBox = Map<String, GiftEntry>



