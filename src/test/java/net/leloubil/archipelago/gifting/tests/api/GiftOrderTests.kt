package net.leloubil.archipelago.gifting.tests.api

import app.cash.turbine.test
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import net.leloubil.archipelago.gifting.testutils.archipelagoContainer
import net.leloubil.archipelago.gifting.testutils.defer

class GiftOrderTests : BehaviorSpec({
    given("A running multiworld") {
        val mw = archipelagoContainer(playersCount = 3)
        val p1 by defer { mw.playerClient(1) }
        val p2 by defer { mw.playerClient(2) }
        val p3 by defer { mw.playerClient(3) }

        fun createGiftItem(name: String, traits: List<String>): GiftItem {
            return GiftItem(name, traits.map { GiftTrait(it) })
        }

        and("Multiple initialized gifting services") {
            val service1 by defer { GiftingService(p1) }
            val service2 by defer { GiftingService(p2) }
            val service3 by defer { GiftingService(p3) }

            and("A recipient with an open gift box") {
                defer { service3.openGiftBox(true, listOf()) }

                `when`("Multiple gifts are sent in a specific order") {
                    val firstItem = createGiftItem("First Gift", listOf("Weapon"))
                    val secondItem = createGiftItem("Second Gift", listOf("Armor"))
                    val thirdItem = createGiftItem("Third Gift", listOf("Consumable"))

                    defer {
                        service1.sendGift(firstItem, 1, p3.slot, p3.team)

                        service2.sendGift(secondItem, 1, p3.slot, p3.team)

                        service1.sendGift(thirdItem, 1, p3.slot, p3.team)
                    }

                    then("The gifts should be received in the same order they were sent") {
                        service3.receivedGifts.test {
                            // First gift
                            val firstReceived = awaitItem()
                            firstReceived.item shouldBe firstItem
                            firstReceived.senderPlayerSlot shouldBe p1.slot

                            // Second gift
                            val secondReceived = awaitItem()
                            secondReceived.item shouldBe secondItem
                            secondReceived.senderPlayerSlot shouldBe p2.slot

                            // Third gift
                            val thirdReceived = awaitItem()
                            thirdReceived.item shouldBe thirdItem
                            thirdReceived.senderPlayerSlot shouldBe p1.slot
                        }
                    }
                }
            }
        }
    }
})
