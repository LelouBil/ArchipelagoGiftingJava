package net.leloubil.archipelago.gifting.utils

import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.core.spec.style.AnnotationSpec.Test
import io.kotest.matchers.Matcher
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveElementAt
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldStartWith
import io.kotest.matchers.shouldHave
import net.leloubil.archipelago.gifting.api.GiftTrait

class CloseTraitParserTests: AnnotationSpec() {
    @Test
    fun testOneExactMatch() {
        val closeTraitParser = BKTreeCloseTraitParser<Any?>()
        closeTraitParser.registerAvailableGift(1, listOf(GiftTrait("a", 1f, 1f)))
        closeTraitParser.registerAvailableGift(2, listOf(GiftTrait("b", 1f, 1f)))
        closeTraitParser.registerAvailableGift(
            3, listOf(
                GiftTrait("a", 1f, 1f),
                GiftTrait("b", 1f, 1f)
            )
        )
        val matches =
            closeTraitParser.findClosestAvailableGift(
                listOf(GiftTrait("a", 1f, 1f))
            )
        matches.shouldHaveSingleElement(1)
    }

    @Test
    fun testTwoExactMatches() {
        val closeTraitParser = BKTreeCloseTraitParser<Any?>()
        closeTraitParser.registerAvailableGift(1, listOf(GiftTrait("a", 1f, 1f)))
        closeTraitParser.registerAvailableGift(2, listOf(GiftTrait("a", 1f, 1f)))
        closeTraitParser.registerAvailableGift(3, listOf(GiftTrait("b", 1f, 1f)))
        closeTraitParser.registerAvailableGift(
            4, listOf(
                GiftTrait("a", 1f, 1f),
                GiftTrait("b", 1f, 1f)
            )
        )
        val matches =
            closeTraitParser.findClosestAvailableGift(
                listOf(GiftTrait("a", 1f, 1f))
            )
        matches.shouldContainExactlyInAnyOrder(1,2)
    }

    @Test
    fun TestOneFuzzyMatch() {
        val closeTraitParser = BKTreeCloseTraitParser<Any?>()
        closeTraitParser.registerAvailableGift(1, listOf(GiftTrait("a", 1f, 1f)))
        closeTraitParser.registerAvailableGift(2, listOf(GiftTrait("b", 1f, 1f)))
        closeTraitParser.registerAvailableGift(
            3, listOf(
                GiftTrait("a", 1f, 1f),
                GiftTrait("b", 1f, 1f)
            )
        )
        val matches =
            closeTraitParser.findClosestAvailableGift(
                listOf(GiftTrait("a", 2f, 1f))
            )
        matches.shouldHaveSingleElement(1)
    }

    @Test
    fun TestTwoFuzzyMatches() {
        val closeTraitParser = BKTreeCloseTraitParser<Any?>()
        closeTraitParser.registerAvailableGift(
            1, listOf(
                GiftTrait("a", 1f, 1f),
                GiftTrait("b", 1f, 1f)
            )
        )
        closeTraitParser.registerAvailableGift(
            2, listOf(
                GiftTrait("a", 1f, 1f),
                GiftTrait("c", 1f, 1f)
            )
        )
        closeTraitParser.registerAvailableGift(
            3, listOf(
                GiftTrait("d", 1f, 1f)
            )
        )
        val matches =
            closeTraitParser.findClosestAvailableGift(
                listOf(GiftTrait("a", 1f, 1f))
            )
        matches.shouldContainExactlyInAnyOrder(1,2)
    }

    @Test
    fun testNoMatch() {
        val closeTraitParser = BKTreeCloseTraitParser<Any?>()
        closeTraitParser.registerAvailableGift(
            1, listOf(
                GiftTrait("a", 1f, 1f)
            )
        )
        val matches =
            closeTraitParser.findClosestAvailableGift(
                listOf(GiftTrait("b", 1f, 1f))
            )
        matches.shouldBeEmpty()
    }

    @Test
    fun TestGoodClosest() {
        val closeTraitParser = BKTreeCloseTraitParser<Any?>()
        closeTraitParser.registerAvailableGift(
            1, listOf(
                GiftTrait("a", 1f, 1f)
            )
        )
        closeTraitParser.registerAvailableGift(
            2, listOf(
                GiftTrait("a", 1f, 1f),
                GiftTrait("b", 20f, 1f)
            )
        )
        val matches =
            closeTraitParser.findClosestAvailableGift(
                listOf(
                    GiftTrait("a", 1f, 1f),
                    GiftTrait("b", 1f, 1f)
                )
            )
        matches.shouldHaveSingleElement(2)
    }
}
