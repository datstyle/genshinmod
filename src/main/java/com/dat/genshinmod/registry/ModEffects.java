package com.dat.genshinmod.registry;

import com.dat.genshinmod.effect.ParalysisEffect;
import com.dat.genshinmod.genshinmod;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, genshinmod.MODID);

    public static final RegistryObject<MobEffect> PARALYSIS = EFFECTS.register("paralysis", ParalysisEffect::new);
}
