package com.dat.genshinmod.item.weapon;

import com.dat.genshinmod.registry.ModSounds;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
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
import net.minecraftforge.common.ForgeMod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Trượng Hộ Ma (Staff of Homa): trường thương hệ hỏa.
 *  - Nội tại: đòn trúng thiêu đốt địch và tích 1 "Linh Hỏa" (tối đa 3). Máu ≤ 50% thì thêm sát thương lửa.
 *  - Kỹ năng (chuột phải), chưa đủ Linh Hỏa: hiến máu để vào trạng thái Hộ Ma.
 *    Trong lúc đó đòn đánh mạnh hơn và hồi máu, người cầm có hiệu ứng lửa quanh người.
 *  - Đủ 3 Linh Hỏa: bùng nổ lửa diện rộng quanh người.
 */
public class StaffOfHomaItem extends SwordItem implements RarityWeapon {
    private static final UUID REACH_ID = UUID.fromString("a7c3e1d2-4b5f-4a6e-8d90-1f2e3c4b5a69");

    // ===== Thông số dễ chỉnh =====
    private static final int MAX_FLAME = 3;                 // số Linh Hỏa tối đa
    private static final double REACH_BONUS = 0.5;          // tầm đánh cộng thêm
    private static final int IGNITE_SECONDS = 4;            // thời gian thiêu đốt khi đánh trúng

    private static final float LOW_HP_RATIO = 0.5F;         // dưới mức máu này thì kích hoạt bonus
    private static final float LOW_HP_BONUS_DAMAGE = 3.0F;  // sát thương lửa thêm khi máu thấp

    private static final float SKILL_HP_COST = 4.0F;        // máu hiến tế (4 = 2 trái tim)
    private static final float SKILL_MIN_HP = 6.0F;         // máu phải lớn hơn mức này mới dùng được
    public static final int MODE_TICKS = 180;               // thời gian Hộ Ma (180 tick = 9 giây)
    /** true = bướm lửa + trượng bốc cháy chỉ có ở trượng ĐÃ PHÙ PHÉP; false = mọi trượng đều có. */
    public static final boolean FX_ENCHANT_ONLY = true;
    private static final int SKILL_COOLDOWN = 400;          // hồi chiêu skill (20 giây)
    private static final float MODE_BONUS_DAMAGE = 2.0F;    // sát thương lửa thêm khi Hộ Ma
    private static final float MODE_HEAL = 1.0F;            // hồi máu mỗi đòn trúng khi Hộ Ma

    private static final double BURST_RADIUS = 4.5;         // bán kính bùng nổ
    private static final float BURST_DAMAGE = 9.0F;
    private static final int BURST_IGNITE_SECONDS = 6;
    private static final int BURST_COOLDOWN = 200;          // hồi chiêu bùng nổ (10 giây)

    // ===== Âm lượng (chỉnh tại đây) =====
    private static final float VOL_SLASH = 0.7F;
    private static final float VOL_CHARGE = 0.7F;
    private static final float VOL_READY = 1.0F;
    private static final float VOL_SKILL = 1.0F;
    private static final float VOL_BURST = 1.0F;

    public StaffOfHomaItem(Tier tier, int dmg, float speed, Properties props) {
        super(tier, dmg, speed, props);
    }

    @Override
    public WeaponTier getWeaponTier() {
        return WeaponTier.FIVE_STAR;
    }

    // Tầm đánh dài hơn một chút (trường thương)
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.MAINHAND) return base;
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.putAll(base);
        b.put(ForgeMod.ENTITY_REACH.get(),
                new AttributeModifier(REACH_ID, "Homa reach", REACH_BONUS, AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    // ===== Tiếng vung trượng =====
    // Chạy mỗi lần vung tay. Bỏ qua khi vừa dùng skill chuột phải (skill có tiếng riêng).
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        Level level = entity.level();
        if (!level.isClientSide) {
            long now = level.getGameTime();
            boolean justUsedSkill = stack.hasTag() && now - stack.getTag().getLong("SkillTick") <= 3;
            if (!justUsedSkill) {
                float pitch = 0.95F + level.random.nextFloat() * 0.1F;
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        ModSounds.HOMA_SLASH.get(), SoundSource.PLAYERS, VOL_SLASH, pitch);
            }
        }
        return false; // không hủy hành động vung
    }

    // ===== Nội tại =====
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide) {
            ServerLevel sl = (ServerLevel) attacker.level();

            target.setSecondsOnFire(IGNITE_SECONDS);
            // Tích Linh Hỏa
            int before = getFlame(stack);
            int flame = Math.min(MAX_FLAME, before + 1);
            stack.getOrCreateTag().putInt("Flame", flame);
            if (flame > before) {
                if (flame == MAX_FLAME) {
                    // đủ 3 Linh Hỏa
                    sl.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                            ModSounds.HOMA_READY.get(), SoundSource.PLAYERS, VOL_READY, 1.0f);
                } else {
                    // nhận thêm 1 Linh Hỏa (lên cao dần theo số Linh Hỏa)
                    sl.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
                            ModSounds.HOMA_CHARGE.get(), SoundSource.PLAYERS, VOL_CHARGE, 0.9f + 0.15f * flame);
                }
            }

            // Sát thương lửa thêm
            boolean mode = isModeActive(stack, sl);
            boolean lowHp = attacker.getHealth() <= attacker.getMaxHealth() * LOW_HP_RATIO;
            float extra = (mode ? MODE_BONUS_DAMAGE : 0F) + (lowHp ? LOW_HP_BONUS_DAMAGE : 0F);
            if (extra > 0F) {
                target.invulnerableTime = 0;
                target.hurt(attacker.damageSources().inFire(), extra);
                sl.sendParticles(ParticleTypes.FLAME,
                        target.getX(), target.getY() + 1, target.getZ(), 14, 0.3, 0.5, 0.3, 0.05);
            }
            if (mode) {
                attacker.heal(MODE_HEAL);
            }
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    // ===== Kỹ năng chuột phải =====
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);

        ServerLevel sl = (ServerLevel) level;

        if (getFlame(stack) >= MAX_FLAME) {
            // BÙNG NỔ: lửa diện rộng quanh người
            AABB box = player.getBoundingBox().inflate(BURST_RADIUS);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x != player)) {
                e.hurt(player.damageSources().playerAttack(player), BURST_DAMAGE);
                e.setSecondsOnFire(BURST_IGNITE_SECONDS);
                Vec3 dir = e.position().subtract(player.position());
                dir = new Vec3(dir.x, 0, dir.z).normalize();
                e.push(dir.x * 0.8, 0.3, dir.z * 0.8);
                e.hurtMarked = true;
            }
            sl.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(),
                    90, 2.0, 0.6, 2.0, 0.08);
            sl.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 1, player.getZ(),
                    20, 1.5, 0.4, 1.5, 0.0);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.HOMA_BURST.get(), SoundSource.PLAYERS, VOL_BURST, 1.0f);
            stack.getOrCreateTag().putInt("Flame", 0);
            stack.getOrCreateTag().putLong("SkillTick", level.getGameTime());
            player.getCooldowns().addCooldown(this, BURST_COOLDOWN);
        } else {
            // HỘ MA: hiến máu để tăng sức mạnh trong thời gian ngắn
            boolean creative = player.getAbilities().instabuild;
            if (!creative && player.getHealth() <= SKILL_MIN_HP) {
                player.displayClientMessage(Component.literal("Máu quá thấp để hiến tế!")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            if (!creative) {
                player.setHealth(player.getHealth() - SKILL_HP_COST);
            }
            stack.getOrCreateTag().putLong("ModeUntil", level.getGameTime() + MODE_TICKS);
            stack.getOrCreateTag().putLong("SkillTick", level.getGameTime());
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MODE_TICKS, 0));
            if (!usesGeometryFx(stack)) {   // trượng phù phép dùng bướm lửa vẽ ở client, không dùng hạt
                sl.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1, player.getZ(),
                        40, 0.5, 0.8, 0.5, 0.05);
            }
            // Tiếng vào Hộ Ma: dùng homa_charge, hạ giọng cho trầm
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.HOMA_CHARGE.get(), SoundSource.PLAYERS, VOL_SKILL, 0.7f);
            player.getCooldowns().addCooldown(this, SKILL_COOLDOWN);
        }
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    // ===== Hiệu ứng lửa quanh người khi đang cầm =====
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (level.isClientSide || !selected || !(entity instanceof Player player)) return;

        long t = level.getGameTime();
        ServerLevel sl = (ServerLevel) level;

        if (t % 3 == 0 && isModeActive(stack, level) && !usesGeometryFx(stack)) {
            sl.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(),
                    3, 0.35, 0.6, 0.35, 0.01);
        }
        if (t % 8 == 0 && getFlame(stack) >= MAX_FLAME) {
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(),
                    2, 0.3, 0.6, 0.3, 0.01);
        }
    }

    // ===== Tên và thông tin hiển thị =====
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack)).withStyle(getWeaponTier().boldStyle());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        WeaponTier t = getWeaponTier();
        int f = getFlame(stack);

        tip.add(Component.literal(t.stars).withStyle(t.style()));
        tip.add(Component.translatable(t.langKey()).append(" . ").append(Component.translatable("staffhoma.genshinmod.type_spear")).withStyle(ChatFormatting.GRAY));
        tip.add(Component.empty());

        tip.add(Component.translatable("staffhoma.genshinmod.charge").withStyle(ChatFormatting.GRAY)
                .append(": ")
                .append(Component.literal("●".repeat(f)).withStyle(ChatFormatting.GOLD))
                .append(Component.literal("○".repeat(MAX_FLAME - f)).withStyle(ChatFormatting.DARK_GRAY)));
        tip.add(Component.empty());

        tip.add(Component.translatable("staffhoma.genshinmod.passive").withStyle(t.boldStyle()));
        tip.add(Component.translatable("staffhoma.genshinmod.passive_1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("staffhoma.genshin.passive_2").withStyle(ChatFormatting.GRAY));
        tip.add(Component.empty());

        tip.add(Component.translatable("staffhoma.genshin.skill").withStyle(t.boldStyle()));
        tip.add(Component.translatable("staffhoma.genshin.skill_1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("staffhoma.genshin.skill_2").withStyle(ChatFormatting.GRAY));
        tip.add(Component.empty());
        if (level != null && isModeActive(stack, level)) {
            tip.add(Component.empty());
            tip.add(Component.translatable("staffhoma.genshin.notifaction").withStyle(ChatFormatting.GOLD));
        }
    }

    /** Trượng này có dùng hiệu ứng hình học (bướm lửa, trượng bốc cháy) thay cho hạt không. */
    public static boolean usesGeometryFx(ItemStack stack) {
        return !FX_ENCHANT_ONLY || stack.isEnchanted();
    }

    /** Đang trong Hộ Ma và được dùng hiệu ứng hình học (client dùng hàm này để vẽ bướm / đổi model). */
    public static boolean isFxActive(ItemStack stack, long gameTime) {
        return stack.hasTag() && stack.getTag().getLong("ModeUntil") > gameTime && usesGeometryFx(stack);
    }

    private static int getFlame(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt("Flame") : 0;
    }

    private static boolean isModeActive(ItemStack stack, Level level) {
        return stack.hasTag() && stack.getTag().getLong("ModeUntil") > level.getGameTime();
    }
}
