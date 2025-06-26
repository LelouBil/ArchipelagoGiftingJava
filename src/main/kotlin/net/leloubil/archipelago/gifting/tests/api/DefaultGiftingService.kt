package net.leloubil.archipelago.gifting.tests.api

import dev.koifysh.archipelago.Client
import dev.koifysh.archipelago.network.client.SetPacket
import net.leloubil.archipelago.gifting.tests.utils.dataStorageAsFlow
import net.leloubil.archipelago.gifting.tests.utils.getDataStorage
import net.leloubil.archipelago.gifting.tests.remote.GiftBoxDescriptor
import net.leloubil.archipelago.gifting.tests.remote.GiftEntry
import net.leloubil.archipelago.gifting.tests.remote.GiftId
import net.leloubil.archipelago.gifting.tests.remote.GiftTraitEntry
import net.leloubil.archipelago.gifting.tests.remote.GiftTraitName
import net.leloubil.archipelago.gifting.tests.remote.LibraryDataVersion
import net.leloubil.archipelago.gifting.tests.remote.MotherBox
import net.leloubil.archipelago.gifting.tests.remote.PlayerGiftBox
import net.leloubil.archipelago.gifting.tests.remote.getMotherBoxKey
import net.leloubil.archipelago.gifting.tests.remote.getPlayerGiftBoxKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import net.leloubil.archipelago.gifting.tests.utils.setDataStorage
import kotlin.collections.map


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
            .onEach {
                // Remove the gifts from the gift box in the data storage.
                val dataStorageSet = session.setDataStorage(
                    SetPacket(myGiftBoxKey, mapOf<GiftId, GiftEntry>()).apply {
                        it.forEach { g ->
                            addDataStorageOperation(
                                SetPacket.Operation.POP, g.id
                            )
                        }
                    })
                if (dataStorageSet == 0) {
                    //todo throw IllegalStateException("Failed to write to data storage when processing received gifts.")
                    // The java library currently does not differentiate between a failed request and the first request.
                }
            }
            // Flatten the flow but keep the order of gifts.
            .flatMapConcat { giftList ->
                giftList.map { gift ->
                    ReceivedGift(
                        id = GiftId(gift.id),
                        item = GiftItem(
                            name = gift.name,
                            traits = gift.traits.map {
                                GiftTrait(it.name, it.quality, it.duration)
                            },
                            value = gift.valuePerUnit,
                        ),
                        amount = gift.amount,
                        senderPlayerSlot = gift.senderPlayerSlot,
                        senderPlayerTeam = gift.senderPlayerTeam,
                        isRefund = gift.isRefund
                    )
                }.asFlow()
            }

    // Write the new gift box descriptor to the data storage.
    private suspend fun updateGiftBoxDescriptor(descriptor: GiftBoxDescriptor): Boolean {
        val setPacket = SetPacket(motherBoxKey, emptyMap<Int, GiftBoxDescriptor>())
        setPacket.addDataStorageOperation(
            SetPacket.Operation.UPDATE,
            mapOf(session.slot to descriptor)
        )
        val reqId = session.setDataStorage(setPacket)
        //todo return reqId != 0
        // The java library currently does not differentiate between a failed request and the first request.
        return true
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
    private suspend fun addGiftToBox(recipientTeam: Int, recipientPlayerSlot: Int, giftEntry: GiftEntry): SendGiftResult {
        val packet = SetPacket(
            getPlayerGiftBoxKey(recipientTeam, recipientPlayerSlot),
            emptyMap<GiftId, GiftEntry>()
        ).apply {
            addDataStorageOperation(
                SetPacket.Operation.UPDATE,
                mapOf(giftEntry.id to giftEntry)
            )
        }
        val res = session.setDataStorage(packet)
        return if (res == 0) {
            //todo SendGiftResult.SendGiftFailure.DataStorageWriteError
            // The java library currently does not differentiate between a failed request and the first request.
            SendGiftResult.SendGiftSuccess
        } else {
            SendGiftResult.SendGiftSuccess
        }
    }
}
