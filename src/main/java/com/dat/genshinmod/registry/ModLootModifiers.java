package com.dat.genshinmod.registry;

import com.dat.genshinmod.genshinmod;
import com.dat.genshinmod.loot.AddItemModifier;
import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, genshinmod.MODID);

    static {
        MODIFIERS.register("add_item", AddItemModifier.CODEC);
    }

    public static void register(IEventBus bus) { MODIFIERS.register(bus); }
}