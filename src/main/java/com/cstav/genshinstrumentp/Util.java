package com.cstav.genshinstrumentp;

import java.util.function.Supplier;

import com.cstav.genshinstrument.item.InstrumentItem;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class Util {

    public static final CompoundTag TAG_EMPTY = new CompoundTag();

    public static CompoundTag getOrCreateElementTag(final ItemStack item, final String key) {
        return getOrCreateElementTag(Main.modTag(item), key);
    }
    public static CompoundTag getOrCreateElementTag(final CompoundTag parent, final String key) {
        return getOrCreateTag(parent, key, CompoundTag.TAG_COMPOUND, () -> new CompoundTag());
    }

    public static ListTag getOrCreateListTag(final CompoundTag parent, final String key) {
        return getOrCreateTag(parent, key, CompoundTag.TAG_LIST, () -> new ListTag());
    }

    public static <T extends Tag> T getOrCreateTag(ItemStack item, String key, int type, Supplier<T> orElse) {
        return getOrCreateTag(Main.modTag(item), key, type, orElse);
    }
    @SuppressWarnings("unchecked")
    public static <T extends Tag> T getOrCreateTag(CompoundTag parent, String key, int type, Supplier<T> orElse) {
        if (parent.contains(key, type))
            return (T) parent.get(key);

        final T tag = orElse.get();
        parent.put(key, tag);
        return tag;
    }


    public static InteractionHand getInstrumentHand(final LivingEntity entity) {
        return (entity.getMainHandItem().getItem() instanceof InstrumentItem)
            ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }
    @SuppressWarnings("resource")
    @OnlyIn(Dist.CLIENT)
    public static InteractionHand getInstrumentHand() {
        return getInstrumentHand(Minecraft.getInstance().player);
    }

}
