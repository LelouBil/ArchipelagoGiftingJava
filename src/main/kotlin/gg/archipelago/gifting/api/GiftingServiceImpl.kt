package gg.archipelago.gifting.api

import dev.koifysh.archipelago.Client
import dev.koifysh.archipelago.network.client.SetPacket
import gg.archipelago.gifting.dataStorageAsFlow
import gg.archipelago.gifting.datastorageGet
import gg.archipelago.gifting.remote.GiftBoxDescriptor
import gg.archipelago.gifting.remote.GiftEntry
import gg.archipelago.gifting.remote.GiftId
import gg.archipelago.gifting.remote.GiftTraitEntry
import gg.archipelago.gifting.remote.GiftTraitName
import gg.archipelago.gifting.remote.LibraryDataVersion
import gg.archipelago.gifting.remote.MotherBox
import gg.archipelago.gifting.remote.getMotherBoxKey
import gg.archipelago.gifting.remote.getPlayerGiftBoxKey
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
import kotlin.collections.map


private val defaultGiftBoxDescriptor = GiftBoxDescriptor(
    isOpen = false,
    acceptsAnyGift = false,
    desiredTraits = emptyList(),
    minimumGiftDataVersion = LibraryDataVersion,
    maximumGiftDataVersion = LibraryDataVersion
)

class GiftingServiceImpl
@JvmOverloads
constructor(
    private val session: Client,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : GiftingService {

    private val myGiftBoxKey: String = getPlayerGiftBoxKey(session.team, session.slot)

    private val motherBoxKey: String = getMotherBoxKey(session.team)


    private val myTeamMotherBoxState: StateFlow<MotherBox> =
        session.dataStorageAsFlow<MotherBox>(motherBoxKey).map { it.new }
            .stateIn(coroutineScope, SharingStarted.Eagerly, mapOf())

    private val myGiftBoxDescriptorState: StateFlow<GiftBoxDescriptor> =
        myTeamMotherBoxState.map { it[session.slot] ?: defaultGiftBoxDescriptor }
            .stateIn(coroutineScope, SharingStarted.Eagerly, defaultGiftBoxDescriptor)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val receivedGifts: Flow<ReceivedGift> =
        session.dataStorageAsFlow<Map<String, GiftEntry>>(myGiftBoxKey)
            .map { d -> d.map { it.mapKeys { e -> GiftId(e.key) } } }
            .map { (old, new) ->
                if (old != null) {
                    new.filter { !old.contains(it.key) }.values
                } else {
                    new.values
                }
            }
            .filter { it.isNotEmpty() }
            .onEach {
                val dataStorageSet = session.dataStorageSet(
                    SetPacket(
                        myGiftBoxKey,
                        mapOf<GiftId, GiftEntry>(),
                    ).apply {
                        it.forEach { g ->
                            addDataStorageOperation(
                                SetPacket.Operation.POP, g.id
                            )
                        }
                    })
                if (dataStorageSet == 0) {
                    //todo throw IllegalStateException("Failed to write to data storage when processing received gifts.")
                }
            }.flatMapConcat { ls ->
                ls.asFlow().map {
                    ReceivedGift(
                        id = GiftId(it.id),
                        name = it.name,
                        traits = it.traits.map { trait -> GiftTrait(GiftTraitName(trait.name), trait.quality, trait.duration) },
                        amount = it.amount,
                        valuePerUnit = it.valuePerUnit,
                        senderPlayerSlot = it.senderPlayerSlot,
                        senderPlayerTeam = it.senderPlayerTeam,
                        isRefund = it.isRefund
                    )
                }
            }

    private fun updateGiftBoxDescriptor(
        descriptor: GiftBoxDescriptor,
    ): Boolean {
        val setPacket = SetPacket(motherBoxKey, emptyMap<Int, GiftBoxDescriptor>())
        setPacket.addDataStorageOperation(
            SetPacket.Operation.UPDATE,
            mapOf(session.slot to descriptor)
        )
        val reqId = session.dataStorageSet(setPacket)
        return reqId != 0;
    }

    override suspend fun openGiftBox(
        acceptsAnyGifts: Boolean, desiredTraits: List<String>
    ): Boolean {
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
        recipientPlayerSlot: Int, recipientPlayerTeam: Int?, giftTraits: Collection<GiftTraitName>
    ): CanGiftResult {
        val mbox = session.datastorageGet<MotherBox>(
            getMotherBoxKey(recipientPlayerTeam ?: session.team)
        ) ?: return CanGiftResult.CanGiftError.DataStorageWriteError
        val descriptor = mbox[recipientPlayerSlot] ?: return CanGiftResult.CanGiftError.PlayerSlotNotFound(
            recipientPlayerSlot
        )
        if (!descriptor.isOpen) return CanGiftResult.CanGiftError.GiftBoxClosed
        if (descriptor.minimumGiftDataVersion > LibraryDataVersion) {
            return CanGiftResult.CanGiftError.DataVersionTooLow(descriptor.minimumGiftDataVersion)
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
        item: GiftItem, amount: Int, recipientPlayerSlot: Int, recipientPlayerTeam: Int?
    ): SendGiftResult {
        val recipientTeam = recipientPlayerTeam ?: session.team
        val canGift = canGiftToPlayer(
            recipientPlayerSlot, recipientTeam, item.traits.map { it.name })
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
            recipientPlayerTeam = recipientTeam,
            isRefund = false
        )
        return addGiftToBox(giftEntry.recipientPlayerTeam, giftEntry.recipientPlayerSlot, giftEntry)
    }

    private fun addGiftToBox(
        recipientTeam: Int, recipientPlayerSlot: Int, giftEntry: GiftEntry
    ): SendGiftResult {
        val packet = SetPacket(
            getPlayerGiftBoxKey(recipientTeam, recipientPlayerSlot), emptyMap<GiftId, GiftEntry>()
        ).apply {
            addDataStorageOperation(
                SetPacket.Operation.UPDATE,
                mapOf(giftEntry.id to giftEntry)
            )
        }
        val res = session.dataStorageSet(packet)
        return if (res == 0) {
            //todo SendGiftResult.SendGiftFailure.DataStorageWriteError
            SendGiftResult.SendGiftSuccess
        } else {
            SendGiftResult.SendGiftSuccess
        }
    }

    override suspend fun refundGift(receivedGift: ReceivedGift): SendGiftResult {
        val giftEntry = GiftEntry(
            id = receivedGift.id.id,
            name = receivedGift.name,
            amount = receivedGift.amount,
            valuePerUnit = receivedGift.valuePerUnit,
            traits = receivedGift.traits.map { GiftTraitEntry(it.name.name, it.quality, it.duration) },
            senderPlayerSlot = session.slot,
            senderPlayerTeam = session.team,
            recipientPlayerSlot = receivedGift.senderPlayerSlot,
            recipientPlayerTeam = receivedGift.senderPlayerTeam,
            isRefund = true
        )
        return addGiftToBox(giftEntry.recipientPlayerTeam, giftEntry.recipientPlayerSlot, giftEntry)
    }
}
