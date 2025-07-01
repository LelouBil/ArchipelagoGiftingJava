package net.leloubil.archipelago.gifting.api

import dev.koifysh.archipelago.Client
import dev.koifysh.archipelago.network.client.SetPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import net.leloubil.archipelago.gifting.remote.GiftBoxDescriptor
import net.leloubil.archipelago.gifting.remote.GiftEntry
import net.leloubil.archipelago.gifting.remote.GiftId
import net.leloubil.archipelago.gifting.remote.GiftTraitEntry
import net.leloubil.archipelago.gifting.remote.GiftTraitName
import net.leloubil.archipelago.gifting.remote.LibraryDataVersion
import net.leloubil.archipelago.gifting.remote.MotherBox
import net.leloubil.archipelago.gifting.remote.PlayerGiftBox
import net.leloubil.archipelago.gifting.remote.getMotherBoxKey
import net.leloubil.archipelago.gifting.remote.getPlayerGiftBoxKey
import net.leloubil.archipelago.gifting.utils.dataStorageAsFlow
import net.leloubil.archipelago.gifting.utils.getDataStorage
import net.leloubil.archipelago.gifting.utils.setDataStorage


// The default value for a non-existent gift box descriptor.
private val defaultGiftBoxDescriptor = GiftBoxDescriptor(
    isOpen = false,
    acceptsAnyGift = false,
    desiredTraits = emptyList(),
    minimumGiftDataVersion = LibraryDataVersion,
    maximumGiftDataVersion = LibraryDataVersion
)

class DefaultGiftingService(
    private val session: Client,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : GiftingService {

    private val myGiftBoxKey: String = getPlayerGiftBoxKey(session.team, session.slot)

    private val motherBoxKey: String = getMotherBoxKey(session.team)

    // Flow that holds the latest state of the player's gift box descriptor.
    private val myGiftBoxDescriptorState: StateFlow<GiftBoxDescriptor> =
        session.dataStorageAsFlow<MotherBox>(motherBoxKey)
            .map { it.new[session.slot] ?: defaultGiftBoxDescriptor }
            .stateIn(coroutineScope, SharingStarted.Eagerly, defaultGiftBoxDescriptor)

    // Flow that emits received gifts after removing them from the gift box.
    @OptIn(ExperimentalCoroutinesApi::class)
    override val receivedGifts: Flow<ReceivedGift> =
        session.dataStorageAsFlow<PlayerGiftBox>(myGiftBoxKey)
            // Only emit gifts that were actually added in this update.
            .map { (old, new) ->
                if (old != null) new.filterKeys { !old.containsKey(it) } else new
            }
            .map { it.values }
            // Stop here if there are no new gifts.
            .filter { it.isNotEmpty() }
            .flatMapConcat { list -> list.map { it.toReceived() }.asFlow() }

    private fun GiftEntry.toReceived(): ReceivedGift = ReceivedGift(
        id = GiftId(this.id),
        item = GiftItem(
            name = this.name,
            traits = this.traits.map {
                GiftTrait(it.name, it.quality ?: 1.0f, it.duration ?: 1.0f)
            },
            value = this.valuePerUnit,
        ),
        amount = this.amount,
        senderPlayerSlot = this.senderPlayerSlot,
        senderPlayerTeam = this.senderPlayerTeam,
        isRefund = this.isRefund
    )

    override suspend fun getGiftBoxContents(): List<ReceivedGift> {
        return session.getDataStorage<PlayerGiftBox>(myGiftBoxKey)?.values?.map { it.toReceived() }.orEmpty()
    }

    override suspend fun removeGiftsFromBox(vararg receivedGifts: ReceivedGift): List<GiftId>? {
        val res = session.setDataStorage<PlayerGiftBox>(
            SetPacket(myGiftBoxKey, mapOf<GiftId, GiftEntry>()).apply {
                receivedGifts.forEach { gift ->
                    addDataStorageOperation(
                        SetPacket.Operation.POP, gift.id.id
                    )
                }
            })
        if (res == null) {
            return null
            //todo throw IllegalStateException("Failed to write to data storage when processing received gifts.")
            // The java library currently does not differentiate between a failed request and the first request.
        }
        if (res.old == null) {
            return emptyList()
        }
        return res.old.filterKeys { !res.new.containsKey(it) }.values.map { GiftId(it.id) }
    }


    // Write the new gift box descriptor to the data storage.
    private suspend fun updateGiftBoxDescriptor(descriptor: GiftBoxDescriptor): Boolean {
        val setPacket = SetPacket(motherBoxKey, emptyMap<Int, GiftBoxDescriptor>())
        setPacket.addDataStorageOperation(
            SetPacket.Operation.UPDATE,
            mapOf(session.slot to descriptor)
        )
        val res = session.setDataStorage<MotherBox>(setPacket)
        //todo return reqId != 0
        // The java library currently does not differentiate between a failed request and the first request.
        return res != null
    }

    override suspend fun openGiftBox(acceptsAnyGifts: Boolean, desiredTraits: List<String>): Boolean {
        val newGiftBoxDescriptor = myGiftBoxDescriptorState.value.copy(
            isOpen = true, acceptsAnyGift = acceptsAnyGifts, desiredTraits = desiredTraits
        )
        return updateGiftBoxDescriptor(newGiftBoxDescriptor)
    }

    override suspend fun closeGiftBox(): Boolean {
        val newGiftBoxDescriptor = myGiftBoxDescriptorState.value.copy(
            isOpen = false,
        )
        return updateGiftBoxDescriptor(newGiftBoxDescriptor)
    }

    override suspend fun canGiftToPlayer(
        recipientPlayerSlot: Int,
        recipientPlayerTeam: Int,
        giftTraits: Collection<GiftTraitName>
    ): CanGiftResult {

        val mbox =
            session.getDataStorage<MotherBox>(getMotherBoxKey(recipientPlayerTeam))
                ?: return CanGiftResult.CanGiftError.NoGiftBox

        val descriptor = mbox[recipientPlayerSlot]
            ?: return CanGiftResult.CanGiftError.NoGiftBox

        if (!descriptor.isOpen) return CanGiftResult.CanGiftError.GiftBoxClosed

        if (descriptor.minimumGiftDataVersion > LibraryDataVersion) {
            return CanGiftResult.CanGiftError
                .DataVersionTooLow(descriptor.minimumGiftDataVersion)
        }

        if (descriptor.acceptsAnyGift) {
            return CanGiftResult.CanGiftSuccess.AcceptsAnyGifts
        }

        val other = descriptor.desiredTraits.map(::GiftTraitName)

        val commonTraits = giftTraits.intersect(other)

        return if (commonTraits.any()) {
            CanGiftResult.CanGiftSuccess.AcceptedTraits(commonTraits)
        } else {
            CanGiftResult.CanGiftError.NoMatchingTraits(other)
        }
    }

    override suspend fun sendGift(
        item: GiftItem,
        amount: Int,
        recipientPlayerSlot: Int,
        recipientPlayerTeam: Int
    ): SendGiftResult {
        val canGift = canGiftToPlayer(recipientPlayerSlot, recipientPlayerTeam, item.traits.map { it.name })
        if (canGift is CanGiftResult.CanGiftError) return SendGiftResult.SendGiftFailure.CannotGift(canGift)

        val giftEntry = GiftEntry(
            id = GiftId.new().id,
            name = item.name,
            amount = amount,
            valuePerUnit = item.value,
            traits = item.traits.map { GiftTraitEntry(it.name.name, it.quality, it.duration) },
            senderPlayerSlot = session.slot,
            senderPlayerTeam = session.team,
            recipientPlayerSlot = recipientPlayerSlot,
            recipientPlayerTeam = recipientPlayerTeam,
            isRefund = false
        )
        return addGiftToBox(giftEntry.recipientPlayerTeam, giftEntry.recipientPlayerSlot, giftEntry)
    }

    override suspend fun refundGift(receivedGift: ReceivedGift): SendGiftResult {
        val giftEntry = GiftEntry(
            id = receivedGift.id.id,
            name = receivedGift.item.name,
            amount = receivedGift.amount,
            valuePerUnit = receivedGift.item.value,
            traits = receivedGift.item.traits
                .map { GiftTraitEntry(it.name.name, it.quality, it.duration) },
            senderPlayerSlot = session.slot,
            senderPlayerTeam = session.team,
            recipientPlayerSlot = receivedGift.senderPlayerSlot,
            recipientPlayerTeam = receivedGift.senderPlayerTeam,
            isRefund = true
        )
        return addGiftToBox(
            giftEntry.recipientPlayerTeam,
            giftEntry.recipientPlayerSlot,
            giftEntry
        )
    }

    // Adds a gift entry to the recipient's gift box.
    private suspend fun addGiftToBox(
        recipientTeam: Int,
        recipientPlayerSlot: Int,
        giftEntry: GiftEntry
    ): SendGiftResult {
        val packet = SetPacket(
            getPlayerGiftBoxKey(recipientTeam, recipientPlayerSlot),
            emptyMap<GiftId, GiftEntry>()
        ).apply {
            addDataStorageOperation(
                SetPacket.Operation.UPDATE,
                mapOf(giftEntry.id to giftEntry)
            )
        }
        val res = session.setDataStorage<PlayerGiftBox>(packet)
        return if (res == null) {
            //todo SendGiftResult.SendGiftFailure.DataStorageWriteError
            // The java library currently does not differentiate between a failed request and the first request.
            SendGiftResult.SendGiftSuccess
        } else {
            SendGiftResult.SendGiftSuccess
        }
    }
}
