package com.dat.genshinmod.registry;

import com.dat.genshinmod.genshinmod;
import com.dat.genshinmod.item.weapon.BloodsoakedRuinsItem;
import com.dat.genshinmod.item.weapon.FracturedHaloItem;
import com.dat.genshinmod.item.weapon.StaffOfHomaItem;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.*;

public class ModItems {
        public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, genshinmod.MODID);

        public static final RegistryObject<Item> BLOODSOAKED_RUINS = ITEMS.register("bloodsoaked_ruins",
                () -> new BloodsoakedRuinsItem(Tiers.NETHERITE, 4, -2.6F,
                        new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

        public static final RegistryObject<Item> STAFF_OF_HOMA = ITEMS.register("staff_of_homa",
                () -> new StaffOfHomaItem(Tiers.NETHERITE, 4, -2.5F,
                        new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

        public static final RegistryObject<Item> FRACTURED_HALO = ITEMS.register("fractured_halo",
                () -> new FracturedHaloItem(Tiers.NETHERITE, 4, -2.5F,
                        new Item.Properties().rarity(Rarity.EPIC).fireResistant()));
}