package com.dat.genshinmod;

import com.dat.genshinmod.client.ClientEvents;
import com.dat.genshinmod.client.HaloBurstAftermath;
import com.dat.genshinmod.client.SlamFx;
import com.dat.genshinmod.client.TooltipColors;
import com.dat.genshinmod.combat.ElectroPulseManager;
import com.dat.genshinmod.combat.SlamManager;
import com.dat.genshinmod.effect.ModEffects;
import com.dat.genshinmod.item.ModCreativeTabs;
import com.dat.genshinmod.item.ModItems;
import com.dat.genshinmod.loot.ModLootModifiers;
import com.dat.genshinmod.sound.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.ForgeRegistries;
import com.dat.genshinmod.network.ModNetwork;
import org.slf4j.Logger;
@Mod(genshinmod.MODID)
public class genshinmod
{
    public static final String MODID = "genshinmod";
    private static final Logger LOGGER = LogUtils.getLogger();

    public genshinmod(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(ClientEvents::registerModels);
        modEventBus.addListener(this::commonSetup);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        ModLootModifiers.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModEffects.EFFECTS.register(FMLJavaModLoadingContext.get().getModEventBus());

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener(ElectroPulseManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(SlamManager::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onHotbar);
        MinecraftForge.EVENT_BUS.addListener(ClientEvents::onContainerForeground);
        MinecraftForge.EVENT_BUS.addListener(TooltipColors::onTooltipColor);
        modEventBus.addListener(this::addCreative);

        // MỚI: phần chỉ chạy ở client (giáo đeo sau lưng + chặn tiếng đánh vanilla)
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ClientEvents::addLayers);
            MinecraftForge.EVENT_BUS.addListener(ClientEvents::onPlaySound);
            MinecraftForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
            MinecraftForge.EVENT_BUS.addListener(SlamFx::onRenderLevel);
            MinecraftForge.EVENT_BUS.addListener(TooltipColors::onTooltipColor);
            MinecraftForge.EVENT_BUS.addListener(HaloBurstAftermath::onGui);
        }

        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        event.enqueueWork(ModNetwork::register);
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.logDirtBlock)
            LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.BLOODSOAKED_RUINS);
            event.accept(ModItems.STAFF_OF_HOMA);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("HELLO from server starting");
    }
}