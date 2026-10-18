package com.dat.genshinmod.item.weapon;

import com.dat.genshinmod.client.render.SpearItemRenderer;
import com.dat.genshinmod.combat.SlamManager;
import com.dat.genshinmod.registry.ModSounds;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ForgeMod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class BloodsoakedRuinsItem extends SwordItem implements RarityWeapon {
    private static final UUID REACH_ID = UUID.fromString("5b1f6a3e-7c2d-4e8a-9a41-3d2c8f0b7e11");
    private static final int MAX_ENERGY = 3;

    // ===== Thông số dễ chỉnh =====
    private static final double REACH_BONUS = 0.5;   // tầm đánh cộng thêm (0 là bằng tay không; sinh tồn tầm khoảng 3 + số này)
    private static final double DASH_SPEED = 1.0;    // độ xa của cú lao tới đâm
    private static final double DASH_RANGE = 3.5;    // vùng trúng đòn của cú lao
    private static final float DASH_DAMAGE = 7.0F;

    public BloodsoakedRuinsItem(Tier tier, int dmg, float speed, Properties props) {
        super(tier, dmg, speed, props);
    }

    // Độ hiếm: FOUR_STAR (tím), FIVE_STAR (vàng), LEGENDARY (đỏ)
    @Override
    public WeaponTier getWeaponTier() {
        return WeaponTier.FIVE_STAR;
    }

    // Tầm đánh dài hơn một chút (giáo)
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.MAINHAND) return base;
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.putAll(base);
        b.put(ForgeMod.ENTITY_REACH.get(),
                new AttributeModifier(REACH_ID, "Polearm reach", REACH_BONUS, AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    // Nội tại: đánh trúng cộng 1 điện tích, địch ướt nhận thêm sét
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide) {
            int before = getEnergy(stack);
            int energy = Math.min(MAX_ENERGY, before + 1);
            stack.getOrCreateTag().putInt("Energy", energy);

            attacker.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    ModSounds.HIT.get(), SoundSource.PLAYERS, 0.8f, 1.0f);

            if (before < MAX_ENERGY && energy == MAX_ENERGY) {
                attacker.level().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                        ModSounds.CHARGE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            }

            if (target.isInWaterOrRain()) {
                target.invulnerableTime = 0;
                target.hurt(attacker.damageSources().lightningBolt(), 3f);
                ((ServerLevel) attacker.level()).sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        target.getX(), target.getY() + 1, target.getZ(), 12, 0.3, 0.5, 0.3, 0.1);
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);

        ServerLevel sl = (ServerLevel) level;
        if (getEnergy(stack) >= MAX_ENERGY) {
            // BURST: bay lên, dậm xuống, vòng điện xung khi chạm đất
            SlamManager.start((ServerPlayer) player);
            stack.getOrCreateTag().putInt("Energy", 0);
            player.getCooldowns().addCooldown(this, 200); // 10 giây
        } else {
            // SKILL: lao tới đâm xuyên
            Vec3 look = player.getLookAngle();
            player.setDeltaMovement(look.x * DASH_SPEED, 0.15, look.z * DASH_SPEED);
            player.hurtMarked = true;
            AABB box = player.getBoundingBox().expandTowards(look.scale(DASH_RANGE)).inflate(1);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x != player)) {
                e.hurt(player.damageSources().playerAttack(player), DASH_DAMAGE);
            }
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(),
                    20, 0.4, 0.5, 0.4, 0.1);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.DASH.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            player.getCooldowns().addCooldown(this, 60); // 3 giây
        }
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    // Vẽ giáo bằng renderer riêng (thân giáo + đèn lồng đung đưa + quầng sáng phù phép)
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new SpearItemRenderer();
                return renderer;
            }
        });
    }

    // Tên vũ khí: đậm và đúng màu độ hiếm (hiện cả trên thanh hotbar khi cầm)
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack)).withStyle(getWeaponTier().boldStyle());
    }

    // Khung thông tin khi rê chuột
    @Override
public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
    WeaponTier t = getWeaponTier();
    int e = getEnergy(stack);

    tip.add(Component.literal(t.stars).withStyle(t.style()));
    tip.add(Component.translatable(t.langKey()).append(" · ")
            .append(Component.translatable("tooltip.genshinmod.type_spear"))
            .withStyle(ChatFormatting.GRAY));
    tip.add(Component.empty());

    tip.add(Component.translatable("tooltip.genshinmod.charge").withStyle(ChatFormatting.GRAY)
            .append(": ")
            .append(Component.literal("●".repeat(e)).withStyle(ChatFormatting.AQUA))
            .append(Component.literal("○".repeat(MAX_ENERGY - e)).withStyle(ChatFormatting.DARK_GRAY)));
    tip.add(Component.empty());

    tip.add(Component.translatable("tooltip.genshinmod.passive").withStyle(t.boldStyle()));
    tip.add(Component.translatable("tooltip.genshinmod.passive_1").withStyle(ChatFormatting.GRAY));
    tip.add(Component.translatable("tooltip.genshinmod.passive_2").withStyle(ChatFormatting.GRAY));
    tip.add(Component.empty());

    tip.add(Component.translatable("tooltip.genshinmod.skill").withStyle(t.boldStyle()));
    tip.add(Component.translatable("tooltip.genshinmod.skill_1").withStyle(ChatFormatting.GRAY));
    tip.add(Component.translatable("tooltip.genshinmod.skill_2").withStyle(ChatFormatting.GRAY));
    tip.add(Component.empty());
    if (stack.isEnchanted()) {
        tip.add(Component.empty());
        tip.add(Component.translatable("tooltip.genshinmod.enchanted").withStyle(ChatFormatting.AQUA));
    }
}

    private static int getEnergy(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt("Energy") : 0;
    }
}