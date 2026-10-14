package com.dat.genshinmod.client;

import com.dat.genshinmod.item.ModItems;
import com.dat.genshinmod.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;

import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;

import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
public class ClientEvents {

    // Âm lượng tiếng chém: 1.0 là mức gốc của file
    private static final float SLASH_VOLUME = 0.6F;

    private static final Set<String> BLOCKED = Set.of(
            "entity.player.attack.weak",
            "entity.player.attack.strong",
            "entity.player.attack.sweep",
            "entity.player.attack.crit",
            "entity.player.attack.knockback",
            "entity.player.attack.nodamage"
    );

    // Giáo đeo sau lưng
    @SuppressWarnings({"deprecation", "removal"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        PlayerRenderer normal = event.getSkin("default");
        if (normal != null) normal.addLayer(new SpearBackLayer(normal));

        PlayerRenderer slim = event.getSkin("slim");
        if (slim != null) slim.addLayer(new SpearBackLayer(slim));
    }

    // Chặn tiếng đánh vanilla khi cầm giáo
    public static void onPlaySound(PlaySoundEvent event) {
        try {
            SoundInstance snd = event.getSound();
            if (snd == null) return;

            ResourceLocation id = snd.getLocation();
            if (!"minecraft".equals(id.getNamespace()) || !BLOCKED.contains(id.getPath())) return;

            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null || !p.getMainHandItem().is(ModItems.BLOODSOAKED_RUINS.get())) return;
            if (p.distanceToSqr(snd.getX(), snd.getY(), snd.getZ()) > 9.0) return;

            event.setSound(null);
        } catch (Exception ignored) {
        }
    }

    // Mỗi lần bắt đầu vung tay khi cầm giáo: trượt hay trúng đều có tiếng chém
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;

        boolean startedSwing = p.swinging && p.swingTime == 0
                && p.getMainHandItem().is(ModItems.BLOODSOAKED_RUINS.get());
        if (!startedSwing) return;
        if (mc.options.keyUse.isDown()) return;   // chuột phải (skill) thì không phát tiếng chém

        float pitch = 0.95F + p.getRandom().nextFloat() * 0.15F;
        p.level().playLocalSound(p.getX(), p.getY(), p.getZ(),
                ModSounds.SLASH.get(), SoundSource.PLAYERS, SLASH_VOLUME, pitch, false);
    }
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(SpearItemRenderer.MAIN);
        event.register(SpearItemRenderer.LANTERN);
        event.register(SpearItemRenderer.AURA_HEAD);
        event.register(SpearItemRenderer.AURA_LANTERN);
        event.register(HaloItemRenderer.BODY);
        event.register(HaloItemRenderer.SAW);
    }
    // Vẽ ánh sáng lên ô nếu vũ khí đã đủ nội tại:
// giáo đủ 3 điện tích (tím xanh điện), Trượng Hộ Ma đủ 3 Linh Hỏa (rực lửa)
private static void drawGlow(GuiGraphics g, ItemStack s, int x, int y) {
    if (!s.hasTag()) return;
    if (s.is(ModItems.BLOODSOAKED_RUINS.get()) && s.getTag().getInt("Energy") >= 3) {
        SlotGlow.draw(g, x, y);
    } else if (s.is(ModItems.STAFF_OF_HOMA.get()) && s.getTag().getInt("Flame") >= 3) {
        SlotGlow.drawFire(g, x, y);
    }
}

// Thanh hotbar
public static void onHotbar(RenderGuiOverlayEvent.Post event) {
    if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
    Minecraft mc = Minecraft.getInstance();
    LocalPlayer p = mc.player;
    if (p == null || mc.options.hideGui) return;

    GuiGraphics g = event.getGuiGraphics();
    int w = event.getWindow().getGuiScaledWidth();
    int h = event.getWindow().getGuiScaledHeight();
    for (int i = 0; i < 9; i++) {
        drawGlow(g, p.getInventory().getItem(i), w / 2 - 90 + i * 20 + 2, h - 19);
    }
}

// Túi đồ, rương, creative...
public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
    AbstractContainerScreen<?> screen = event.getContainerScreen();
    for (Slot slot : screen.getMenu().slots) {
        if (slot.isActive()) {
            drawGlow(event.getGuiGraphics(), slot.getItem(), slot.x, slot.y);
        }
    }
}
}