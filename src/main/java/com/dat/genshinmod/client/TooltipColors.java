package com.dat.genshinmod.client;

import com.dat.genshinmod.item.RarityWeapon;
import com.dat.genshinmod.item.WeaponTier;
import net.minecraftforge.client.event.RenderTooltipEvent;

/** Viền và nền khung thông tin đổi theo độ hiếm: 4★ tím, hiếm vàng, siêu hiếm đỏ. */
public class TooltipColors {
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        if (!(event.getItemStack().getItem() instanceof RarityWeapon weapon)) return;
        WeaponTier t = weapon.getWeaponTier();

        event.setBorderStart(0xFF000000 | t.rgb);          // viền sáng ở trên
        event.setBorderEnd(0xFF000000 | t.tint(0.55));     // viền tối dần ở dưới
        event.setBackgroundStart(0xF0000000 | t.tint(0.10));
        event.setBackgroundEnd(0xF0000000 | t.tint(0.05));
    }
}