package com.dat.genshinmod.client;

import com.dat.genshinmod.item.FracturedHaloItem;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Khi Hào Quang Tách Rời đủ 100% điện năng: ô vật phẩm phủ ánh điện tím, có tia sáng nhảy
 * và một vòng chấm sáng xoay quanh ô. Đây là phiên bản màu ĐIỆN TÍM (hệ Điện của Ineffa).
 * Muốn đổi màu: sửa các hằng số màu ở đầu file.
 */
@Mod.EventBusSubscriber(modid = "genshinmod", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class HaloSlotGlow {

    // ===== Màu (0xRRGGBB) =====
    private static final int TOP_RGB     = 0xB06BFF;   // tím điện (đỉnh ô)
    private static final int BOTTOM_RGB  = 0x6F8CFF;   // xanh tím (đáy ô)
    private static final int SPARK_RGB   = 0xFFFFFF;   // tia sáng lấp lánhs
    // ===== Vòng chấm =====
    private static final int DOTS = 20;                 // số chấm
    private static final float RADIUS = 9.6F;           // bán kính vòng

    private static final ResourceLocation HALO_ID = new ResourceLocation("genshinmod", "fractured_halo");

    @SubscribeEvent
    public static void register(RegisterItemDecorationsEvent event) {
        Item item = ForgeRegistries.ITEMS.getValue(HALO_ID);
        if (item == null || item == Items.AIR) return;
        event.register(item, new Decorator());
    }

    private static final class Decorator implements IItemDecorator {
        @Override
        public boolean render(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
            if (FracturedHaloItem.getEnergy(stack) < FracturedHaloItem.MAX_ENERGY) return false;
            long now = Util.getMillis();

            // 1) Lớp màu phủ ô, nhấp nháy nhẹ
            float wave = (float) (Math.sin(now / 240.0) * 0.5 + 0.5);
            int alpha = 55 + (int) (45 * (Math.sin(now / 110.0) * 0.5 + 0.5));
            int top = (alpha << 24) | mix(TOP_RGB, BOTTOM_RGB, wave);
            int bottom = ((alpha - 15) << 24) | mix(BOTTOM_RGB, TOP_RGB, wave);
            g.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 16, top, bottom, 0);

            // 2) Tia sáng lấp lánh nhảy trong ô
            for (int k = 0; k < 5; k++) {
                long seed = (now / 130L) * 31L + k * 977L;
                int sx = (int) Math.floorMod(seed * 7919L, 16L);
                int sy = (int) Math.floorMod(seed * 104729L, 16L);
                int a = (int) (230 * Math.abs(Math.sin(now / 90.0 + k)));
                g.fill(RenderType.guiOverlay(), x + sx, y + sy, x + sx + 1, y + sy + 1, (a << 24) | SPARK_RGB);
            }

            // 3) Vòng chấm xoay quanh ô, mỗi chấm sáng tối lệch pha
            double spin = now / 1800.0 * Math.PI * 2;
            for (int i = 0; i < DOTS; i++) {
                double ang = spin + i * (Math.PI * 2 / DOTS);
                int px = x + 8 + (int) Math.round(Math.cos(ang) * RADIUS);
                int py = y + 8 + (int) Math.round(Math.sin(ang) * RADIUS);
                int a = 90 + (int) (130 * (Math.sin(now / 180.0 + i) * 0.5 + 0.5));
            }
            return false;   // vẫn vẽ thanh độ bền / số lượng như bình thường
        }
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int gr = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (gr << 8) | bl;
    }
}
