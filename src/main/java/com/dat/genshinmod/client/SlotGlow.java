package com.dat.genshinmod.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

/** Vẽ lớp ánh sáng lấp lánh phủ lên một ô vật phẩm 16x16. Có hai kiểu: điện (giáo) và lửa (Trượng Hộ Ma). */
public class SlotGlow {
    // Điện: tím xanh
    private static final int PURPLE = 0xB06BFF;
    private static final int BLUE = 0x4FC3FF;
    // Lửa: đỏ cam vàng
    private static final int RED = 0xFF3B1F;
    private static final int ORANGE = 0xFFA526;
    private static final int YELLOW = 0xFFE27A;

    private static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int gr = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (gr << 8) | bl;
    }

    /** Kiểu điện (giữ nguyên như cũ). x, y là góc trên trái của vật phẩm trong ô. */
    public static void draw(GuiGraphics g, int x, int y) {
        long now = Util.getMillis();
        float wave = (float) (Math.sin(now / 260.0) * 0.5 + 0.5);
        int alpha = 70 + (int) (60 * (Math.sin(now / 110.0) * 0.5 + 0.5));
        int top = (alpha << 24) | lerp(PURPLE, BLUE, wave);
        int bottom = (alpha << 24) | lerp(BLUE, PURPLE, wave);
        g.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 16, top, bottom, 0);
        sparkles(g, x, y, now, 4, 0xFFFFFF);
    }

    /** Kiểu lửa: màu đỏ cam bập bùng, đáy sáng hơn đỉnh, nhiều tia lửa vàng nhảy lên. */
    public static void drawFire(GuiGraphics g, int x, int y) {
        long now = Util.getMillis();
        float flick = (float) (Math.sin(now / 150.0) * 0.5 + 0.5);
        int alpha = 80 + (int) (70 * (Math.sin(now / 85.0) * 0.5 + 0.5));
        int top = ((alpha - 30) << 24) | lerp(RED, ORANGE, flick);
        int bottom = (Math.min(255, alpha + 40) << 24) | lerp(ORANGE, YELLOW, flick);
        g.fillGradient(RenderType.guiOverlay(), x, y, x + 16, y + 16, top, bottom, 0);
        sparkles(g, x, y, now, 6, YELLOW);
    }

    /** Vài điểm sáng lấp lánh nhảy chỗ liên tục. */
    private static void sparkles(GuiGraphics g, int x, int y, long now, int count, int rgb) {
        for (int k = 0; k < count; k++) {
            long seed = (now / 140L) * 31L + k * 977L;
            int sx = (int) Math.floorMod(seed * 7919L, 16L);
            int sy = (int) Math.floorMod(seed * 104729L, 16L);
            int a = (int) (220 * Math.abs(Math.sin(now / 90.0 + k)));
            g.fill(RenderType.guiOverlay(), x + sx, y + sy, x + sx + 1, y + sy + 1, (a << 24) | rgb);
        }
    }
}