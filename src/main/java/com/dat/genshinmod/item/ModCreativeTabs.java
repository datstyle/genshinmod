package com.dat.genshinmod.item;
import com.dat.genshinmod.genshinmod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
;public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, genshinmod.MODID);

    public static final RegistryObject<CreativeModeTab> GENSHIN_TAB = TABS.register("genshin_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.genshinmod"))
                    .icon(() -> new ItemStack(ModItems.BLOODSOAKED_RUINS.get()))
                    .displayItems((params, out) -> {
                        out.accept(ModItems.BLOODSOAKED_RUINS.get());
                        out.accept(ModItems.STAFF_OF_HOMA.get());
                    })
                    .build());

    public static void register(IEventBus bus) { TABS.register(bus); }
}
