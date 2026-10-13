package com.dat.genshinmod.item;

import net.minecraft.network.chat.Style;

/** Độ hiếm của vũ khí: quyết định màu tên, màu viền thông tin và dòng sao. */
public enum WeaponTier {
    FOUR_STAR("Vũ khí 4★", "★★★★", 0xB46BFF),            // tím
    FIVE_STAR("Vũ khí hiếm", "★★★★★", 0xFFC247),          // vàng
    LEGENDARY("Vũ khí siêu hiếm", "★★★★★★", 0xFF4B4B);    // đỏ

    public final String label;
    public final String stars;
    public final int rgb;

    WeaponTier(String label, String stars, int rgb) {
        this.label = label;
        this.stars = stars;
        this.rgb = rgb;
    }

    public Style style() {
        return Style.EMPTY.withColor(rgb);
    }

    public Style boldStyle() {
        return Style.EMPTY.withColor(rgb).withBold(true);
    }

    /** Màu rgb nhân với hệ số f (0 đến 1) để làm tối. */
    public int tint(double f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f);
        int g = (int) (((rgb >> 8) & 0xFF) * f);
        int b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }
    public String langKey() {
        return "tier.genshinmod." + name().toLowerCase();
    }
}