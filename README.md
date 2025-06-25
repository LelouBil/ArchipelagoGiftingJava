# ArchipelagoGiftingJvm

A JVM implementation of the Archipelago Gifting Protocol.
This library uses the [Archipelago Java Client](https://github.com/ArchipelagoMW/Java-Client)
for communication with the Archipelago server.

This library and readme were heavily inspired from the following .NET Gifting 
Library: https://github.com/agilbert1412/Archipelago.Gifting.Net/

The library is written in Kotlin and is best used with Kotlin features, but it is fully compatible with Java
and other JVM languages.

This library provides a simple and easy way to interact with
the [Gifting API](https://github.com/agilbert1412/Archipelago.Gifting.Net/blob/main/Documentation/Gifting%20API.md)

# Installation
## Gradle

```kotlin
dependencies {
    implementation("net.leloubil:ArchipelagoGiftingJvm:1.0")
}
```

# Documentation

## Creating a GiftingService Instance

For non-Kotlin users, you should use the JavaGiftingService class, which doesn't expose Kotlin-specific features.

```kotlin
// client must be an already connected instance of Client from the Archipelago Java Client
val service = GiftingService(client)
```

```java
JavaGiftingService service = new JavaGiftingService(client);
```

A new gifting service starts to listen to archipelago events when created, such as gift reception.
It **does not** automatically open **nor close** a giftBox.

You must do so by calling the `openGiftBox` or`closeGiftBox` methods.

## Opening and Closing a GiftBox

To inform the multiworld that your slot is willing and able to receive gifts, you must open a GiftBox.

You must specify if you accept any gifts or only gifts with specific traits.

```kotlin
// Open a giftBox that accepts any gifts
service.openGiftBox(true, emptyList())
// Open a giftBox that only accepts gifts with specific traits
service.openGiftBox(false, listOf("Armor", "Consumable"))
```

Once a giftBox is open, you can close it at any time, but you do not have to.

You can leave your giftBox open for as long as you wish, even across different sessions.

An open giftBox can receive gifts at any time. If your game can only receive gifts while online (sync),
you should close the giftBox when disconnecting to prevent receiving gifts while offline.

If your game can receive gifts while offline,
you can keep the giftBox open forever and simply check on the gifts when logging in.

## Receiving Gifts

### Kotlin

You should collect the `receivedGifts` flow from the `GiftingService` to get notified of new gifts as they arrive.

The flow will not drop any gifts, and they will arrive in the order they were sent. Except if there were already
multiple gifts in the giftBox when you started collecting the flow, in which case the gifts that were already there
will be received in an arbitrary order.

```kotlin
service.receivedGifts.collect { gift ->
    // Process the gift
    println("I got a gift: $gift !")
}
```

### Java

You should register a listener to the `JavaGiftingService` to get notified of new gifts as they arrive.

Then call the `startListeningForGifts` method to start receiving gifts.
If you do not register a listener **before** calling this method, gifts will be lost.

```java
class GiftHandler {
    public void initializeGifting() {
        service.addGiftListener(() -> {
            // Process the gift
            System.out.println("I got a gift: " + gift + " !");
        });
        service.startListeningForGifts();
    }
}
```

## Creating Gifts

To send a gift, you first need to create a GiftItem, and optionally, Gift Traits.
If you do not define GiftTraits, the gift will be sent without any traits and can only be parsed by name.

```kotlin
val gift = GiftItem(
    "Master Sword",
    listOf(
        GiftTrait("Sword", 2f),
        GiftTrait("Legendary", 2.0)
    ), 100f
)
```

A gift item has a name, an amount, and a value. It is important to note that the value is for one instance of the item.
The total value of the gift will be the value multiplied by the amount.

A gift can have as many traits as you wish, and it is up to the receiver to decide how to interpret these traits.

It is recommended to add more traits rather than fewer, so it is more likely to be understandable by various games.

It is not recommended to add many synonymous, or very similar, traits,
as this makes it more complicated for the multiworld developers to keep track of commonly used traits
that they should support. For example, a gift should probably not carry both the trait "Stone" and the trait "Rock".

"Stone", being a "common" trait based on the specification, is preferable.

## Gift Traits

A Trait is defined by a string describing the trait itself. It is usually a single word. While you can put anything
there,
some common traits are available in the class `GiftFlag` as constants, for convenience.

Furthermore, a trait has two extra values, which are the quality and the duration of the trait.
What these values mean exactly will depend on the game,
but it is intended that a value of `1.0` describes an average quality or duration for a given game.

For example, if your game contains speed boosts that can last 30s, 60s, or 90s,
then a "Speed" trait of duration 1.0 would be a 60s Speed boost. A duration of 0.5 would be 30s,
1.5 would be 90s, and if your mod can generate these with custom values, you could interpret a duration of 10.0 as 600 s.

Once again, it is completely up to the various game developers to define what these values mean for their game.
They are intended to convey a vague concept, not strict descriptions.

They should be used to distinguish characteristics of otherwise-similar items from the same game,
and their values should always be considered relative, not absolute.

Both the quality and the duration are optional and will default to 1.0 if omitted.

## Sending Gifts

Before attempting to send a gift, you can check if your desired receiver has an open GiftBox.
You can provide the traits of your intended gift to also know if they can accept your gift specifically

```kotlin
// Send 5 of myItem to the player with slot 1 on team 0
service.sendGift(myItem, 5, 1, 0)
```

The function returns a result that you can check to see if the gift was sent successfully or not, and the reason if it
was not.

Checking the state of the giftBox before proceeding is optional but recommended to avoid pointless operations.

## Java

In Java, sending works the same, but the failure to send a gift will throw an exception instead of returning a value.

## Processing a Gift

Games can choose to process gifts as they wish. They can use the item name, they can use the traits, or any combination
of both. Usually, gifts that come from the same game can be parsed by name for maximum accuracy, but within reason,
games should try to understand items from other games too.

Parsing by traits can be done however you wish. But the library offers a BKTreeCloseTraitParser class that allows 
you to register items with their traits and find the closest match to a given set of traits.

To use it, you must first initialize it and then register every one of your possible receivable gifts in the parser,
with their traits.
This list can come directly from the gifts you can send other players, or all in-game items, or any set you wish. But
every item should have traits. The more items you have, the more traits you will need on the average item to get
accurate parsing.

For example, if you have 10 items that all have the same traits, the Parser will not be able to distinguish them. The 
"Perfect" item list has every item with distinct traits. But even an imperfect list will work.

Your items can be any type you want, and the class is built as a generic so that you can tell it what types are your
items. In the following example, the type `string` is used and the game is presumed to freely create items by name.

```kotlin
val parser = BKTreeCloseTraitParser<String>()
parser.registerAvailableGift("Master Sword", listOf(GiftTrait("Sword", 2f), GiftTrait("Legendary", 2.0)))
parser.registerAvailableGift("Hylian Shield", listOf(GiftTrait("Shield", 1f), GiftTrait("Legendary", 2.0)))
val closestItems = parser.findClosestAvailableGift(
    listOf(GiftTrait("Sword", 2f), GiftTrait("Legendary", 2.0))
)
```

The parser should be kept in memory for the whole duration of the session to avoid having to register the same things
over and over.
When you receive a gift that you wish to parse by traits, you can do:

```kotlin
val closestItems = parser.findClosestAvailableGift(gift.traits)
```

This list might be empty if no item was found that shared any trait.

If you aren't pleased by the distance algorithm, you may provide your own as an argument to BKTreeCloseTraitParser,
having the following signature

```cs
typealias DistanceFunction =
            (List<GiftTrait>, MutableMap<String, Pair<Float, Float>>, BKTreeCloseTraitParser.BooleanWrapper) -> Float
```

For this method, all the traits of the registered gift with the same name have been added together for performance
reasons

## Rejecting a Gift

If you receive a gift that you cannot or will not process properly in the current game, 3 options are available to you.

1: Refunding that gift.

```kotlin
val receivedGift: GiftItem// the gift you received
service.refundGift(receivedGift)
```

When refunding a gift, that gift will be sent back to the original sender, with the flag `isRefund` now set to `true`,
so they know it is not a new gift, but a refund of a gift they originally sent.
It is then up to the original sender client to decide what to do with it. Typically, they would simply give back the
item to the player.

2: Selling the gift

The gift can carry a value in Archipelago currency, which can be added to the EnergyLink for everyone to use if your
game supports interacting with the EnergyLink. A value of zero should be interpreted as 
"coming from a game without multiworld currency", and selling is not a good choice for these gifts

3: Ignoring that gift completely. This should be a last resort, as the item will then be lost forever.
