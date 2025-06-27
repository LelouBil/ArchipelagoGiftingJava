package net.leloubil.archipelago.gifting.tests.api

import app.cash.turbine.test
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.testcontainers.perTest
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import net.leloubil.archipelago.gifting.api.CanGiftResult
import net.leloubil.archipelago.gifting.api.GiftItem
import net.leloubil.archipelago.gifting.api.GiftTrait
import net.leloubil.archipelago.gifting.api.GiftingService
import net.leloubil.archipelago.gifting.api.SendGiftResult
import net.leloubil.archipelago.gifting.remote.GiftTraitName
import net.leloubil.archipelago.gifting.testutils.ArchipelagoContainer
import net.leloubil.archipelago.gifting.testutils.cliquePlayers
import net.leloubil.archipelago.gifting.testutils.defer

class TraitMatchingTests : BehaviorSpec({
    given("A running multiworld") {
        val mw = ArchipelagoContainer(cliquePlayers(2)).also { register(it.perTest()) }
        val p1 by defer { mw.playerClient(1) }
        val p2 by defer { mw.playerClient(2) }

        // Helper functions to create gift items and convert trait strings
        fun createGiftItem(name: String, traits: List<String>): GiftItem {
            return GiftItem(name, traits.map { GiftTrait(it) })
        }

        fun List<String>.toGiftTraitNames(): List<GiftTraitName> {
            return this.map { GiftTraitName(it) }
        }

        and("An initialized source gifting service") {
            val sender by defer { GiftingService(p1) }

            and("A recipient that accepts some specific traits") {
                val acceptedTraitStrings = listOf("Weapon", "Healing")
                val acceptedTraits = acceptedTraitStrings.toGiftTraitNames()
                val recipient by defer { GiftingService(p2) }
                defer { recipient.openGiftBox(false, acceptedTraitStrings) }

                `when`("I send a gift with a matching trait") {
                    val sentItem = createGiftItem("Healing Sword", listOf("Weapon", "Magic"))
                    val sentAmount = 1
                    val sendRes by defer {
                        sender.sendGift(
                            sentItem, sentAmount,
                            p2.slot, p2.team
                        )
                    }

                    then("The gift should be sent successfully") {
                        sendRes shouldBe SendGiftResult.SendGiftSuccess

                        recipient.receivedGifts.test {
                            val received = awaitItem()
                            received.item shouldBe sentItem
                            received.isRefund.shouldBeFalse()
                            received.amount shouldBe sentAmount
                            received.senderPlayerSlot shouldBe p1.slot
                            received.senderPlayerTeam shouldBe p1.team
                        }
                    }
                }

                `when`("I send a gift with no matching traits") {
                    val sentItem = createGiftItem("Magic Potion", listOf("Magic", "Consumable"))
                    val sentAmount = 1
                    val sendRes by defer {
                        sender.sendGift(
                            sentItem, sentAmount,
                            p2.slot, p2.team
                        )
                    }

                    then("The gift should not be sent") {
                        sendRes shouldBe SendGiftResult.SendGiftFailure.CannotGift(
                            CanGiftResult.CanGiftError.NoMatchingTraits(acceptedTraits)
                        )
                    }
                }
            }

            and("A recipient that accepts all traits but has preferences") {
                val preferredTraitStrings = listOf("Armor", "Shield")
                val service2 by defer { GiftingService(p2) }
                defer { service2.openGiftBox(true, preferredTraitStrings) }

                `when`("I send a gift with a matching preferred trait") {
                    val sentItem = createGiftItem("Steel Shield", listOf("Shield", "Metal"))
                    val sentAmount = 1
                    val sendRes by defer {
                        sender.sendGift(
                            sentItem, sentAmount,
                            p2.slot, p2.team
                        )
                    }

                    then("The gift should be sent successfully") {
                        sendRes shouldBe SendGiftResult.SendGiftSuccess

                        service2.receivedGifts.test {
                            val received = awaitItem()
                            received.item shouldBe sentItem
                            received.isRefund.shouldBeFalse()
                            received.amount shouldBe sentAmount
                            received.senderPlayerSlot shouldBe p1.slot
                            received.senderPlayerTeam shouldBe p1.team
                        }
                    }
                }

                `when`("I send a gift with no matching preferred traits") {
                    val sentItem = createGiftItem("Magic Potion", listOf("Magic", "Consumable"))
                    val sentAmount = 1
                    val sendRes by defer {
                        sender.sendGift(
                            sentItem, sentAmount,
                            p2.slot, p2.team
                        )
                    }

                    then("The gift should still be sent successfully (accepts all)") {
                        sendRes shouldBe SendGiftResult.SendGiftSuccess

                        service2.receivedGifts.test {
                            val received = awaitItem()
                            received.item shouldBe sentItem
                            received.isRefund.shouldBeFalse()
                            received.amount shouldBe sentAmount
                            received.senderPlayerSlot shouldBe p1.slot
                            received.senderPlayerTeam shouldBe p1.team
                        }
                    }
                }
            }
        }
    }
})
