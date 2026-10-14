package com.dat.genshinmod.client;

import com.dat.genshinmod.genshinmod;
import com.dat.genshinmod.item.FracturedHaloItem;
import com.dat.genshinmod.item.ModItems;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Khi Hào Quang Tách Rời đủ 100% điện năng: vòng chấm sáng xoay quanh ô vũ khí kèm vài ngôi sao lấp lánh.
 * Hiện ở cả hotbar và túi đồ. Thanh độ bền (nếu có) vẫn hiện bình thường.
 */
@Mod.EventBusSubscriber(modid = genshinmod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HaloSlotGlow implements IItemDecorator {

    private static final int DOTS = 24;             // số chấm trên vòng
    private static final float RADIUS = 8.2F;       // bán kính vòng (ô vật phẩm rộng 16 px)
    private static final int RING_RGB = 0x9FE8FF;   // màu vòng (xanh điện)

    @SubscribeEvent
    public static void onRegister(RegisterItemDecorationsEvent event) {
        event.register(ModItems.FRACTURED_HALO.get(), new HaloSlotGlow());
    }

    @Override
    public boolean render(GuiGraphics g, Font font, ItemStack stack, int xOffset, int yOffset) {
        if (FracturedHaloItem.getEnergy(stack) < FracturedHaloItem.MAX_ENERGY) return false;

        float t = (Util.getMillis() % 100000L) / 1000F;
        float cx = xOffset + 8F, cy = yOffset + 8F;

        // vòng chấm xoay, mỗi chấm sáng tối lệch pha nhau
        for (int i = 0; i < DOTS; i++) {
            float ang = Mth.TWO_PI * i / DOTS + t * 1.2F;
            float pulse = 0.5F + 0.5F * Mth.sin(t * 5F + i * 0.9F);
            int a = (int) (70 + 160 * pulse);
            int px = Math.round(cx + Mth.cos(ang) * RADIUS);
            int py = Math.round(cy + Mth.sin(ang) * RADIUS);
            g.fill(px, py, px + 1, py + 1, (a << 24) | RING_RGB);
        }

        // 3 ngôi sao lấp lánh hình dấu + chạy dọc theo vòng
        for (int k = 0; k < 3; k++) {
            float tw = Mth.sin(t * 6F + k * 2.1F);
            if (tw < 0.2F) continue;                                   // lúc tắt lúc sáng
            float ang = -t * 0.9F + k * (Mth.TWO_PI / 3F);
            int sx = Math.round(cx + Mth.cos(ang) * (RADIUS - 0.5F));
            int sy = Math.round(cy + Mth.sin(ang) * (RADIUS - 0.5F));
            int col = ((int) (255 * tw) << 24) | 0xFFFFFF;
            g.fill(sx, sy - 1, sx + 1, sy + 2, col);                   // nét dọc
            g.fill(sx - 1, sy, sx + 2, sy + 1, col);                   // nét ngang
        }
        return false;   // false = vẫn vẽ thanh độ bền mặc định
    }
}