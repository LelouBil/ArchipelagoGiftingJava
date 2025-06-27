package net.leloubil.archipelago.gifting.tests.utils

import net.leloubil.archipelago.gifting.tests.api.GiftTrait
import kotlin.math.abs
import kotlin.math.min

// A BK-tree implementation for finding the closest available gifts based on traits.
// Source: https://github.com/agilbert1412/Archipelago.Gifting.Net/blob/main/Archipelago.Gifting.Net/Archipelago.Gifting.Net/Utilities/CloseTraitParser/BKTreeCloseTraitParser.cs
// (converted to Kotlin)

typealias DistanceFunction =
            (List<GiftTrait>, MutableMap<String, Pair<Float, Float>>, BKTreeCloseTraitParser.BooleanWrapper) -> Float

class BKTreeCloseTraitParser<T>(distanceDelegate: DistanceFunction? = null) {
    private val _items: MutableList<T> = mutableListOf()
    private val _traits: MutableMap<String, Pair<Float, Float>> = mutableMapOf()
    private val _children: MutableMap<Float, BKTreeCloseTraitParser<T>> = mutableMapOf()
    private val _distance: DistanceFunction = distanceDelegate ?: ::defaultDistance


    fun registerAvailableGift(availableGift: T, traits: List<GiftTrait>) {
        if (_items.isEmpty()) {
            _items.add(availableGift)
            traits.forEach { giftTrait ->
                _traits.merge(
                    giftTrait.name.name, giftTrait.quality to giftTrait.duration
                ) { oldValue, newValue ->
                    oldValue.first + newValue.first to
                            oldValue.second + newValue.second
                }
            }

            return
        }

        val isCompatible = BooleanWrapper()
        val distance = _distance(traits, _traits, isCompatible)
        if (distance == 0.0f) {
            _items.add(availableGift)
            return
        }

        _children.getOrPut(distance) {
            BKTreeCloseTraitParser(_distance)
        }.registerAvailableGift(availableGift, traits)
    }

    private fun findClosestAvailableGift(
        giftTraits: List<GiftTrait>,
        bestDistance: FloatWrapper,
        closestItems: MutableList<T>
    ) {
        val isCompatible = BooleanWrapper()
        val distance = _distance(giftTraits, _traits, isCompatible)
        if (isCompatible.value) {
            if (abs(distance - bestDistance.value) < 0.0001) {
                closestItems.addAll(_items)
            } else if (distance < bestDistance.value) {
                closestItems.clear()
                closestItems.addAll(_items)
                bestDistance.value = distance
            }
        }

        for (keyValuePair in _children.entries) {
            if (distance - keyValuePair.key < bestDistance.value + 0.0001) {
                keyValuePair.value.findClosestAvailableGift(giftTraits, bestDistance, closestItems)
            }
        }
    }

    fun findClosestAvailableGift(giftTraits: List<GiftTrait>): MutableList<T> {
        val closestItems: MutableList<T> = mutableListOf()
        val bestDistance = FloatWrapper(Float.Companion.MAX_VALUE)
        findClosestAvailableGift(giftTraits, bestDistance, closestItems)
        return closestItems
    }


    class BooleanWrapper {
        var value: Boolean = false
    }

    class FloatWrapper(var value: Float)

    private fun defaultDistance(
        giftTraits: List<GiftTrait>, traits: MutableMap<String, Pair<Float, Float>>,
        isCompatible: BooleanWrapper
    ): Float {
        val traitsCopy = traits.toMutableMap()
        var distance = 0.0f
        giftTraits.forEach { giftTrait ->
            traitsCopy.remove(giftTrait.name.name)?.let { values ->
                if (values.first * giftTrait.quality <= 0) {
                    distance += 1.0f
                } else {
                    val d = values.first / giftTrait.quality
                    distance += 1 - min(1 / d, d)
                }

                if (values.second * giftTrait.duration <= 0) {
                    distance += 1.0f
                } else {
                    val d = values.second / giftTrait.duration
                    distance += 1 - min(1 / d, d)
                }
            } ?: run {
                distance += 1.0f
            }
        }
        distance += traitsCopy.size
        isCompatible.value = traitsCopy.size != traits.size
        return distance
    }
}
