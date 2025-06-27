package net.leloubil.archipelago.gifting.tests.utils;

import net.leloubil.archipelago.gifting.api.GiftTrait;
import net.leloubil.archipelago.gifting.utils.BKTreeCloseTraitParser;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


public class JavaBKTreeCloseTraitParserTests {


    @Test
    public void testOneExactMatch() {
        BKTreeCloseTraitParser<Integer> closeTraitParser = new BKTreeCloseTraitParser<>();

        // Register available gifts with traits
        closeTraitParser.registerAvailableGift(1, Collections.singletonList(GiftTrait.create("a", 1f, 1f)));
        closeTraitParser.registerAvailableGift(2, Collections.singletonList(GiftTrait.create("b", 1f, 1f)));
        closeTraitParser.registerAvailableGift(3, List.of(
                GiftTrait.create("a", 1f, 1f),
                GiftTrait.create("b", 1f, 1f)
        ));

        // Find closest available gift
        List<Integer> matches = closeTraitParser.findClosestAvailableGift(
                Collections.singletonList(GiftTrait.create("a", 1f, 1f))
        );

        // Assert that only gift 1 matches
        assertArrayEquals(new Integer[]{1}, matches.toArray());
    }


    @Test
    public void testTwoExactMatches() {
        BKTreeCloseTraitParser<Integer> closeTraitParser = new BKTreeCloseTraitParser<>();

        // Register available gifts with traits
        closeTraitParser.registerAvailableGift(1, Collections.singletonList(GiftTrait.create("a", 1f, 1f)));
        closeTraitParser.registerAvailableGift(2, Collections.singletonList(GiftTrait.create("a", 1f, 1f)));
        closeTraitParser.registerAvailableGift(3, Collections.singletonList(GiftTrait.create("b", 1f, 1f)));
        closeTraitParser.registerAvailableGift(4, List.of(
                GiftTrait.create("a", 1f, 1f),
                GiftTrait.create("b", 1f, 1f)
        ));

        // Find closest available gift
        List<Integer> matches = closeTraitParser.findClosestAvailableGift(
                Collections.singletonList(GiftTrait.create("a", 1f, 1f))
        );

        // Assert that gifts 1 and 2 match
        assertEquals(2, matches.size());
        assertTrue(matches.contains(1));
        assertTrue(matches.contains(2));
    }


    @Test
    public void testOneFuzzyMatch() {
        BKTreeCloseTraitParser<Integer> closeTraitParser = new BKTreeCloseTraitParser<>();

        // Register available gifts with traits
        closeTraitParser.registerAvailableGift(1, Collections.singletonList(GiftTrait.create("a", 1f, 1f)));
        closeTraitParser.registerAvailableGift(2, Collections.singletonList(GiftTrait.create("b", 1f, 1f)));
        closeTraitParser.registerAvailableGift(3, List.of(
                GiftTrait.create("a", 1f, 1f),
                GiftTrait.create("b", 1f, 1f)
        ));

        // Find closest available gift with a fuzzy match (different quality)
        List<Integer> matches = closeTraitParser.findClosestAvailableGift(
                Collections.singletonList(GiftTrait.create("a", 2f, 1f))
        );

        assertArrayEquals(new Integer[]{1}, matches.toArray(Integer[]::new));
    }


    @Test
    public void testTwoFuzzyMatches() {
        BKTreeCloseTraitParser<Integer> closeTraitParser = new BKTreeCloseTraitParser<>();

        // Register available gifts with traits
        closeTraitParser.registerAvailableGift(1, List.of(
                GiftTrait.create("a", 1f, 1f),
                GiftTrait.create("b", 1f, 1f)
        ));
        closeTraitParser.registerAvailableGift(2, List.of(
                GiftTrait.create("a", 1f, 1f),
                GiftTrait.create("c", 1f, 1f)
        ));
        closeTraitParser.registerAvailableGift(3, Collections.singletonList(GiftTrait.create("d", 1f, 1f)));

        // Find closest available gift
        List<Integer> matches = closeTraitParser.findClosestAvailableGift(
                Collections.singletonList(GiftTrait.create("a", 1f, 1f))
        );


        assertArrayEquals(new Integer[]{1, 2}, matches.toArray(Integer[]::new));
    }

    /**
     * Test that no matches are found.
     */
    @Test
    public void testNoMatch() {
        BKTreeCloseTraitParser<Integer> closeTraitParser = new BKTreeCloseTraitParser<>();

        // Register available gift with trait
        closeTraitParser.registerAvailableGift(1, Collections.singletonList(GiftTrait.create("a", 1f, 1f)));

        // Find closest available gift with a different trait
        List<Integer> matches = closeTraitParser.findClosestAvailableGift(
                Collections.singletonList(GiftTrait.create("b", 1f, 1f))
        );

        // Assert that no gifts match
        assertArrayEquals(new Integer[]{}, matches.toArray(Integer[]::new));
    }

    /**
     * Test that the closest match is found when multiple traits are involved.
     */
    @Test
    public void testGoodClosest() {
        BKTreeCloseTraitParser<Integer> closeTraitParser = new BKTreeCloseTraitParser<>();

        // Register available gifts with traits
        closeTraitParser.registerAvailableGift(1, Collections.singletonList(GiftTrait.create("a", 1f, 1f)));
        closeTraitParser.registerAvailableGift(2, List.of(
                GiftTrait.create("a", 1f, 1f),
                GiftTrait.create("b", 20f, 1f)
        ));

        // Find closest available gift
        List<Integer> matches = closeTraitParser.findClosestAvailableGift(List.of(
                GiftTrait.create("a", 1f, 1f),
                GiftTrait.create("b", 1f, 1f)
        ));

        // Assert that only gift 2 matches
        assertArrayEquals(new Integer[]{2}, matches.toArray(Integer[]::new));
    }
}
