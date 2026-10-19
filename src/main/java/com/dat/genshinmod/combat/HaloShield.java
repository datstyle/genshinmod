package com.dat.genshinmod.combat;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
public final class HaloShield {
    private HaloShield() {}
    public static final float PER_ATTACK = 1.5f;
    public static final float MIN = 6.0f;
    public static final float MAX = 40.0f;
    public static final float VISUAL_FULL = 24.0f;


    public static double attack(ItemStack stack) {
        double atk = 1.0;
        for (AttributeModifier m : stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE)) {
            if (m.getOperation() == AttributeModifier.Operation.ADDITION) atk += m.getAmount();
        }
        return atk;
    }


    public static float forWeapon(ItemStack stack) {
        float v = (float) (attack(stack) * PER_ATTACK);
        return v < MIN ? MIN : (v > MAX ? MAX : v);
    }

    public static float thickness(float absorption) {
        float t = absorption / VISUAL_FULL;
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    //Không cộng dồn, hết skill thì sẽ máu vàng về 0
    public static void apply(Player player, float shield) {
        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), shield));
    }
    public static void apply(Player player, ItemStack weapon) {
        apply(player, forWeapon(weapon));
    }
    public static void clear(Player player) {
        player.setAbsorptionAmount(0f);
    }
}