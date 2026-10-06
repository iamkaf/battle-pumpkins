package com.iamkaf.battlepumpkins.pumpkin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * What burns inside a pumpkin. Lights only change the face's glow. Witch light has no item: Lantern Night pumpkins
 * spawn with it, and it is gone once the owner swaps it out.
 */
public enum Light {
    NONE(0xFF2D0003, 0xFF441300),
    TORCH(0xFFFFEC78, 0xFFF4A024),
    SOUL(0xFFAAF6FF, 0xFF40C4E8),
    REDSTONE(0xFFFF7850, 0xFFD02416),
    WITCH(0xFFBEFF96, 0xFF50D23C);

    private final int innerColor;
    private final int edgeColor;

    Light(int innerColor, int edgeColor) {
        this.innerColor = innerColor;
        this.edgeColor = edgeColor;
    }

    /** Color of a carved pixel surrounded by other carved pixels. */
    public int innerColor() {
        return innerColor;
    }

    /** Color of a carved pixel next to uncarved skin. */
    public int edgeColor() {
        return edgeColor;
    }

    public boolean glows() {
        return this != NONE;
    }

    public Optional<Item> item() {
        return switch (this) {
            case TORCH -> Optional.of(Items.TORCH);
            case SOUL -> Optional.of(Items.SOUL_TORCH);
            case REDSTONE -> Optional.of(Items.REDSTONE_TORCH);
            case NONE, WITCH -> Optional.empty();
        };
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<Light> fromItem(ItemStack stack) {
        return Arrays.stream(values()).filter(light -> light.item().filter(stack::is).isPresent()).findFirst();
    }

    public static Light byName(String name) {
        return Arrays.stream(values()).filter(light -> light.serializedName().equals(name)).findFirst().orElse(NONE);
    }
}
