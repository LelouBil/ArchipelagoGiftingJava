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
import gg.archipelago.gifting.remote.PlayerGiftBox
import gg.archipelago.gifting.remote.getMotherBoxKey
import gg.archipelago.gifting.remote.getPlayerGiftBoxKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.collections.map


private val defaultGiftBoxDescriptor = GiftBoxDescriptor(
    isOpen = false,
    acceptsAnyGift = false,
    desiredTraits = emptyList(),
    minimumGiftDataVersion = LibraryDataVersion,
    maximumGiftDataVersion = LibraryDataVersion
)


class GiftingServiceImpl(
    private val session: Client,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : GiftingService {


    override val myGiftBoxKey: String = getPlayerGiftBoxKey(session.team, session.slot)

    private val motherBoxKey: String = getMotherBoxKey(session.team)

    private val serializer: Json = Json

    val myTeamMotherBoxState: StateFlow<MotherBox> =
        session.dataStorageAsFlow(motherBoxKey)
            .map { serializer.decodeFromString<MotherBox>(serializer.encodeToString(it.new)) }
            .stateIn(coroutineScope, SharingStarted.Eagerly, mapOf())

    val myGiftBoxDescriptorState: StateFlow<GiftBoxDescriptor> =
        myTeamMotherBoxState.map { it[session.slot] ?: defaultGiftBoxDescriptor }
            .stateIn(coroutineScope, SharingStarted.Eagerly, defaultGiftBoxDescriptor)

    @OptIn(ExperimentalCoroutinesApi::class)
    val receivedGifts: Flow<ReceivedGift> =
        session.dataStorageAsFlow(myGiftBoxKey).map { p ->
            p.map { serializer.decodeFromString<PlayerGiftBox>(serializer.encodeToString(it)) }
        }.map { (old, new) ->
            if (old != null) {
                new.filter { !old.contains(it.key) }.values
            } else {
                new.values
            }
        }.onEach {
            session.dataStorageSet(
                SetPacket(
                    myGiftBoxKey,
                    mapOf<GiftId, GiftEntry>(),
                ).apply {
                    it.forEach { g ->
                        addDataStorageOperation(
                            SetPacket.Operation.POP, g.id.id
                        )
                    }
                })
        }.flatMapConcat { ls ->
            ls.asFlow().map {
                ReceivedGift(
                    id = it.id,
                    name = it.name,
                    traits = it.traits.map { trait -> GiftTrait(trait.name, trait.quality, trait.duration) },
                    amount = it.amount,
                    valuePerUnit = it.valuePerUnit,
                    senderPlayerSlot = it.senderPlayerSlot,
                    senderPlayerTeam = it.senderPlayerTeam,
                    isRefund = it.isRefund
                )
            }
        }


    override suspend fun openGiftBox() {
        openGiftBox(true, emptyList())
    }

    private suspend fun updateGiftBoxDescriptor(
        descriptor: GiftBoxDescriptor,
    ) {
        val setPacket = SetPacket(motherBoxKey, serializer.encodeToJsonElement(emptyMap<Int, GiftBoxDescriptor>()))
        setPacket.addDataStorageOperation(
            SetPacket.Operation.UPDATE, serializer.encodeToJsonElement(
                mapOf(myGiftBoxKey to descriptor)
            )
        )
        session.dataStorageSet(setPacket)
    }

    override suspend fun openGiftBox(
        acceptsAnyGifts: Boolean, desiredTraits: List<String>
    ) {
        val newGiftBoxDescriptor = myGiftBoxDescriptorState.value.copy(
            isOpen = true, acceptsAnyGift = acceptsAnyGifts, desiredTraits = desiredTraits.map(::GiftTraitName)
        )
        updateGiftBoxDescriptor(newGiftBoxDescriptor)
    }

    override suspend fun closeGiftBox() {
        val newGiftBoxDescriptor = myGiftBoxDescriptorState.value.copy(
            isOpen = false,
        )
        updateGiftBoxDescriptor(newGiftBoxDescriptor)
    }

    override suspend fun canGiftToPlayer(
        recipientPlayerSlot: Int, recipientPlayerTeam: Int?, giftTraits: Collection<GiftTraitName>
    ): CanGiftResult {
        val mbox = serializer.decodeFromString<MotherBox>(
            serializer.encodeToString(
                session.datastorageGet(
                    getMotherBoxKey(recipientPlayerTeam ?: session.team)
                )
            )
        )
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

        val commonTraits = giftTraits.intersect(descriptor.desiredTraits)
        return if (commonTraits.any()) {
            CanGiftResult.CanGiftSuccess.AcceptedTraits(commonTraits)
        } else {
            CanGiftResult.CanGiftError.NoMatchingTraits(descriptor.desiredTraits)
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
            id = GiftId.new(),
            name = item.name,
            amount = amount,
            valuePerUnit = item.value,
            traits = item.traits.map { GiftTraitEntry(it.name, it.quality, it.duration) },
            senderPlayerSlot = session.slot,
            senderPlayerTeam = session.team,
            recipientPlayerSlot = recipientPlayerSlot,
            recipientPlayerTeam = recipientTeam,
            isRefund = false
        )
        return addGiftToBox(recipientTeam, recipientPlayerSlot, giftEntry)
    }

    private fun addGiftToBox(
        recipientTeam: Int, recipientPlayerSlot: Int, giftEntry: GiftEntry
    ): SendGiftResult {
        val packet = SetPacket(
            getPlayerGiftBoxKey(recipientTeam, recipientPlayerSlot), emptyMap<GiftId, GiftEntry>()
        ).apply {
            addDataStorageOperation(
                SetPacket.Operation.UPDATE, serializer.encodeToJsonElement(giftEntry)
            )
        }
        val res = session.dataStorageSet(packet)
        return if (res == 0) {
            SendGiftResult.SendGiftFailure.DataStorageWriteError
        } else {
            SendGiftResult.SendGiftSuccess
        }
    }

    override suspend fun refundGift(receivedGift: ReceivedGift): SendGiftResult {
        val giftEntry = GiftEntry(
            id = receivedGift.id,
            name = receivedGift.name,
            amount = receivedGift.amount,
            valuePerUnit = receivedGift.valuePerUnit,
            traits = receivedGift.traits.map { GiftTraitEntry(it.name, it.quality, it.duration) },
            senderPlayerSlot = session.slot,
            senderPlayerTeam = session.team,
            recipientPlayerSlot = receivedGift.senderPlayerSlot,
            recipientPlayerTeam = receivedGift.senderPlayerTeam,
            isRefund = true
        )
        return addGiftToBox(session.team, session.slot, giftEntry)
    }
}
