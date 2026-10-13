package com.dat.genshinmod.sound;

import com.dat.genshinmod.genshinmod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, genshinmod.MODID);

    /* BloodSoaked Ruins */
    public static final RegistryObject<SoundEvent> DASH   = register("bloodsoaked_dash");
    public static final RegistryObject<SoundEvent> HIT    = register("bloodsoaked_hit");
    public static final RegistryObject<SoundEvent> CHARGE = register("bloodsoaked_charge");
    public static final RegistryObject<SoundEvent> BURST  = register("bloodsoaked_burst");
    public static final RegistryObject<SoundEvent> SLASH = register("bloodsoaked_slash");

    public static final RegistryObject<SoundEvent> BURST_ENCHANT = null;

    /* Staff_Of_Homa */
    public static final RegistryObject<SoundEvent> HOMA_SLASH = register("homa_slash");
    public static final RegistryObject<SoundEvent> HOMA_CHARGE = register("homa_charge");
    public static final RegistryObject<SoundEvent> HOMA_READY = register("homa_ready");
    public static final RegistryObject<SoundEvent> HOMA_BURST = register("homa_burst");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () ->
                SoundEvent.createVariableRangeEvent(new ResourceLocation(genshinmod.MODID, name)));
    }
}