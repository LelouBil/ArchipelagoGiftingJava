package gg.archipelago.gifting.api

import gg.archipelago.gifting.remote.GiftId
import gg.archipelago.gifting.remote.GiftTraitName

data class GiftTrait(val name: GiftTraitName,val quality: Float = 1f, val duration: Float = 1f)
data class GiftItem(val name: String, val value: Int? = null, val traits: List<GiftTrait>)


data class ReceivedGift(
    val id: GiftId,
    val name: String,
    val traits: List<GiftTrait>,
    val amount: Int,
    val valuePerUnit: Int? = null,
    val senderPlayerSlot: Int,
    val senderPlayerTeam: Int,
    val isRefund: Boolean,
)
