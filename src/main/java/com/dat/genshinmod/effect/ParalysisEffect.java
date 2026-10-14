package com.dat.genshinmod.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Tê Liệt: đứng yên tại chỗ (tốc độ di chuyển về 0, không nhảy lên được). Client vẽ tia điện quanh mục tiêu có hiệu ứng này. */
public class ParalysisEffect extends MobEffect {

    public ParalysisEffect() {
        super(MobEffectCategory.HARMFUL, 0x9FD8FF);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, "7a1c5d22-3b0e-4c6f-9a41-2d8e5f10b3c7",
                -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        Vec3 m = entity.getDeltaMovement();
        entity.setDeltaMovement(0.0D, Math.min(m.y, 0.0D), 0.0D);
    }
}
