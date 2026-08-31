package dev.projectgolf.item;

import net.minecraft.world.item.Item;

/**
 * Handheld golf locator. All rendering is client-only; the item itself carries no ticking entity
 * state and creates no chunkloading.
 */
public final class GoldenSpotterItem extends Item {
    public GoldenSpotterItem(Properties properties) {
        super(properties.stacksTo(1));
    }
}
