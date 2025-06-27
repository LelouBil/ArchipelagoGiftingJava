package net.leloubil.archipelago.gifting.tests.api;

import dev.koifysh.archipelago.Client;
import kotlin.Unit;
import net.leloubil.archipelago.gifting.testutils.ArchipelagoContainer;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static net.leloubil.archipelago.gifting.testutils.ArchipelagoContainerKt.cliquePlayers;
import static org.junit.jupiter.api.Assertions.*;


@Testcontainers
public class JavaGiftingServiceTests {

    @Container
    private final ArchipelagoContainer multiworld = new ArchipelagoContainer(cliquePlayers(3));


    @Test
    public void testCreateService() {
        Client client1 = multiworld.blockingPlayerClient(1);
        JavaGiftingService service = new JavaGiftingService(client1);
        service.close();
    }


    @Test
    public void testOpenAndCloseGiftBox() throws ExecutionException, InterruptedException {
        Client client1 = multiworld.blockingPlayerClient(1);
        try (JavaGiftingService service = new JavaGiftingService(client1)) {

            CompletableFuture<Boolean> openFuture = service.openGiftBox(true, Collections.emptyList());
            boolean opened = openFuture.get(); // This blocks until the operation completes
            assertTrue(opened);

            CompletableFuture<Boolean> closeFuture = service.closeGiftBox();
            boolean closed = closeFuture.get(); // This blocks until the operation completes
            assertTrue(closed);
        }
    }


    @Test
    public void testSendGift() throws GiftSendingException, ExecutionException, InterruptedException {

        Client client1 = multiworld.blockingPlayerClient(1);
        Client client2 = multiworld.blockingPlayerClient(1);
        try (JavaGiftingService service = new JavaGiftingService(client1)) {
            try (JavaGiftingService service2 = new JavaGiftingService(client2)) {
                service2.openGiftBox(true, Collections.emptyList()).get();

                // Create a gift to send
                List<GiftTrait> traits = new ArrayList<>();
                // Use the static factory method instead of the constructor
                traits.add(GiftTrait.create("Healing", 1.0f, 1.0f));
                GiftItem item = new GiftItem("Health Potion", traits, 100);

                // Send the gift
                // Parameters: item, amount, recipient player slot, recipient player team
                CompletableFuture<Unit> sendFuture = service.sendGift(item, 1, 2, 1);
                sendFuture.get(); // This blocks until the operation completes
            }
        }
    }


    @Test
    public void testListenForReceivedGifts() throws GiftSendingException, ExecutionException, InterruptedException, TimeoutException {
        GiftItem giftItem = new GiftItem("Magic Wand", Collections.singletonList(GiftTrait.create("Magic", 1.0f, 1.0f)), 50);
        int giftAmount = 1;

        Client client1 = multiworld.blockingPlayerClient(1);
        Client client2 = multiworld.blockingPlayerClient(2);
        try (JavaGiftingService service1 = new JavaGiftingService(client1)) {
            try (JavaGiftingService service2 = new JavaGiftingService(client2)) {
                CompletableFuture<ReceivedGift> receptionFuture = new CompletableFuture<>();
                // Register a listener for received gifts

                service2.openGiftBox(true, Collections.emptyList()).get();
                service2.registerReceivedGiftListener(receptionFuture::complete);

                // Start listening for gifts
                service2.startListeningForGifts();

                System.out.println("Sending gift");
                service1.sendGift(giftItem, giftAmount, client2.getSlot(), client2.getTeam()).get();

                System.out.println("Waiting for reception");
                ReceivedGift gift = receptionFuture.get(5, TimeUnit.SECONDS);

                assertFalse(gift.isRefund());
                assertEquals(giftItem, gift.getItem());
                assertEquals(giftAmount, gift.getAmount());
                assertEquals(client1.getSlot(), gift.getSenderPlayerSlot());
                assertEquals(client1.getTeam(), gift.getSenderPlayerTeam());
            }
        }
    }

    @Test
    public void testGetGiftBoxState() throws ExecutionException, InterruptedException, GiftSendingException {
        GiftItem giftItem = new GiftItem("Health Potion",
                Collections.singletonList(GiftTrait.create("Healing", 1.0f, 1.0f)), 100);
        int giftAmount = 1;

        Client client1 = multiworld.blockingPlayerClient(1);
        Client client2 = multiworld.blockingPlayerClient(2);

        try (JavaGiftingService service1 = new JavaGiftingService(client1)) {
            try (JavaGiftingService service2 = new JavaGiftingService(client2)) {
                // Open gift box for recipient
                service2.openGiftBox(true, Collections.emptyList()).get();

                // Check that gift box is empty initially
                List<ReceivedGift> initialContents = service2.getGiftBoxContents().get();
                assertTrue(initialContents.isEmpty());

                // Send a gift
                service1.sendGift(giftItem, giftAmount, client2.getSlot(), client2.getTeam()).get();

                // Check that gift box now contains the gift
                List<ReceivedGift> contents = service2.getGiftBoxContents().get();
                assertEquals(1, contents.size());

                ReceivedGift gift = contents.get(0);
                assertEquals(giftItem, gift.getItem());
                assertEquals(giftAmount, gift.getAmount());
                assertEquals(client1.getSlot(), gift.getSenderPlayerSlot());
                assertEquals(client1.getTeam(), gift.getSenderPlayerTeam());
                assertFalse(gift.isRefund());
            }
        }
    }

    @Test
    public void testRemoveGiftFromBox() throws ExecutionException, InterruptedException, GiftSendingException {
        GiftItem giftItem = new GiftItem("Mana Potion",
                Collections.singletonList(GiftTrait.create("Magic", 1.0f, 1.0f)), 75);
        int giftAmount = 2;

        Client client1 = multiworld.blockingPlayerClient(1);
        Client client2 = multiworld.blockingPlayerClient(2);

        try (JavaGiftingService service1 = new JavaGiftingService(client1)) {
            try (JavaGiftingService service2 = new JavaGiftingService(client2)) {
                // Open gift box for recipient
                service2.openGiftBox(true, Collections.emptyList()).get();

                // Send a gift
                service1.sendGift(giftItem, giftAmount, client2.getSlot(), client2.getTeam()).get();

                // Check that gift box contains the gift
                List<ReceivedGift> contents = service2.getGiftBoxContents().get();
                assertEquals(1, contents.size());

                ReceivedGift gift = contents.get(0);

                // Remove the gift from the box
                Boolean removeResult = service2.removeGiftFromBox(gift).get();
                assertTrue(removeResult);

                // Check that gift box is now empty
                List<ReceivedGift> afterRemovalContents = service2.getGiftBoxContents().get();
                assertTrue(afterRemovalContents.isEmpty());
            }
        }
    }


}
