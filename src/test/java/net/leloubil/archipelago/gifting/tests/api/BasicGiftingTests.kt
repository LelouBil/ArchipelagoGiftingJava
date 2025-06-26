package net.leloubil.archipelago.gifting.tests.api

import app.cash.turbine.test
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import net.leloubil.archipelago.gifting.testutils.archipelagoContainer
import net.leloubil.archipelago.gifting.testutils.defer

class BasicGiftingTests : BehaviorSpec({
    given("A running multiworld") {
        val mw = archipelagoContainer(playersCount = 2)
        val p1 by defer { mw.playerClient(1) }
        val p2 by defer { mw.playerClient(2) }
        and("An initialized source gifting service") {
            val senderService by defer { GiftingService(p1) }
            and("A recipient that knows about gifts") {
                val receiverService by defer { GiftingService(p2) }
                and("has an open giftBox") {
                    defer { receiverService.openGiftBox(true, listOf()) }
                    `when`("I send the gift") {
                        val sentItem = GiftItem("Random Item", listOf())
                        val sentAmount = 1
                        val sendRes by defer {
                            senderService.sendGift(
                                sentItem, sentAmount,
                                p2.slot, p2.team
                            )
                        }

                        then("The gift should be sent successfully and be in the recipient's gift box") {
                            withClue("The gift should be sent successfully") {
                                sendRes shouldBe SendGiftResult.SendGiftSuccess
                            }
                            withClue("The gift should be in the recipient's gift box") {
                                receiverService.receivedGifts.test {
                                    val received = awaitItem()
                                    received.item shouldBe sentItem
                                    received.isRefund.shouldBeFalse()
                                    received.amount shouldBe sentAmount
                                    received.senderPlayerSlot shouldBe p1.slot
                                    received.senderPlayerTeam shouldBe p1.team
                                }
                            }
                        }
                        and("The recipient refunds it"){
                            val returnRes by defer {
                                withClue("The gift should be sent successfully") {
                                    sendRes shouldBe SendGiftResult.SendGiftSuccess
                                }
                                lateinit var received: ReceivedGift
                                withClue("The gift should be in the recipient's gift box") {
                                    receiverService.receivedGifts.test {
                                        received = awaitItem()
                                        received.item shouldBe sentItem
                                        received.isRefund.shouldBeFalse()
                                        received.amount shouldBe sentAmount
                                        received.senderPlayerSlot shouldBe p1.slot
                                        received.senderPlayerTeam shouldBe p1.team
                                    }
                                }
                                receiverService.refundGift(received)
                            }
                            then("The gift should be refunded and returned to the original sender's gift box") {
                                withClue("The gift should be returned successfully") {
                                    returnRes shouldBe SendGiftResult.SendGiftSuccess
                                }
                                withClue("The gift should be in the original sender's gift box") {
                                    senderService.receivedGifts.test {
                                        val received = awaitItem()
                                        received.item shouldBe sentItem
                                        received.isRefund.shouldBeTrue()
                                        received.amount shouldBe sentAmount
                                        received.senderPlayerSlot shouldBe p2.slot
                                        received.senderPlayerTeam shouldBe p2.team
                                    }
                                }
                            }
                        }
                    }
                }
                and("has a closed giftBox") {
                    defer { receiverService.closeGiftBox() }
                    `when`("I send the gift") {
                        val sentItem = GiftItem("Random Item", listOf())
                        val sentAmount = 1
                        val sendRes by defer {
                            senderService.sendGift(
                                sentItem, sentAmount,
                                p2.slot, p2.team
                            )
                        }
                        then("The gift should not be sent") {
                            sendRes shouldBe SendGiftResult.SendGiftFailure.CannotGift(
                                CanGiftResult.CanGiftError.GiftBoxClosed
                            )
                        }
                    }
                }
            }
            and("A recipent that doesn't know about gifts"){
                `when`("I send the gift") {
                    val gift = GiftItem("Random Item", listOf())
                    val sendRes by defer {senderService.sendGift(gift,1,p2.slot,p2.team)}
                    then("The gift should not be sent"){
                        sendRes shouldBe SendGiftResult.SendGiftFailure.CannotGift(
                            CanGiftResult.CanGiftError.NoGiftBox
                        )
                    }
                }
            }
        }

    }
})




