package com.badiei.neoexchange.datagen;

import com.badiei.neoexchange.NeoExchange;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Custom tags for the mod
 */
public class ModTags {
    public static class Items {
        /**
         * Tag for all useable Neo Stones
         * Can be used to check if an item is a valid stone
         */
        public static final TagKey<Item> USEABLE_STONES = tag("useable_stones");
        
        private static TagKey<Item> tag(String name) {
            return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, name));
        }
    }
}
