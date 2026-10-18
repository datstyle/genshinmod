package com.dat.genshinmod.item.weapon;
import java.util.function.Consumer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import com.dat.genshinmod.client.render.HaloItemRenderer;
import com.dat.genshinmod.combat.HaloManager;
import com.dat.genshinmod.registry.ModSounds;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Hào Quang Tách Rời (Fractured Halo): trường thương 5★, vũ khí của Ineffa.
 *  - Chuột phải (kỹ năng): khiên quanh người, cứ mỗi giây bắn tia điện ra xung quanh trong 5 giây.
 *    Địch trúng tia điện mất máu và bị Tê Liệt.
 *  - Đòn đánh và tia điện tích ĐIỆN NĂNG. Đủ 100%: chuột phải = nắm đấm robot giáng sấm sét.
 */
public class FracturedHaloItem extends SwordItem implements RarityWeapon {
    private static final UUID REACH_ID = UUID.fromString("c4e2a9b7-1d3f-4b58-8a6c-5e7d90f1a2b3");

    // ===== Thông số dễ chỉnh =====
    public static final int MAX_ENERGY = 100;
    public static final int ENERGY_PER_HIT = 12;          // điện năng mỗi đòn đánh trúng
    public static final int ENERGY_PER_ARC_HIT = 3;       // điện năng mỗi mục tiêu trúng tia điện

    public static final int SKILL_TICKS = 200;            // thời gian duy trì kỹ năng: 100 = 5 giây
    public static final int SKILL_COOLDOWN = 200;         // hồi chiêu: 10 giây (tính từ lúc bấm)
    public static final int PULSE_INTERVAL = 20;          // cứ 1 giây bắn điện một lần
    public static final double ARC_RADIUS = 2.0;          // bán kính tia điện: 2 block
    public static final float ARC_DAMAGE = 3.0F;          // sát thương mỗi lần bắn (3 = 1.5 tim)
    public static final int PARALYZE_TICKS = 30;          // thời gian tê liệt mỗi lần bị bắn
    private static final int ABSORPTION_AMP = 1;          // khiên: Absorption II (4 tim)

    public static final double BURST_FORWARD = 3.5;       // nắm đấm giáng xuống cách người bao xa
    public static final double BURST_RADIUS = 5.0;        // bán kính sát thương nắm đấm
    public static final int BURST_DELAY = 45;              // tick từ lúc bấm đến lúc đấm chạm đất
    public static final float BURST_DAMAGE = 16.0F;       // sát thương sấm sét (16 = 8 tim)
    public static final int BURST_PARALYZE_TICKS = 20;

    public FracturedHaloItem(Tier tier, int dmg, float speed, Properties props) {
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
                new AttributeModifier(REACH_ID, "Halo reach", 0.5, AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    // ===== Tích điện năng khi đánh trúng =====
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide) {
            addEnergyTo(stack, ENERGY_PER_HIT);
        }
        return super.hurtEnemy(stack, target, attacker);
    }
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new HaloItemRenderer();
                return renderer;
            }
        });
    }

    // ===== Chuột phải =====
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.pass(stack);

        long now = level.getGameTime();
        CompoundTag tag = stack.getOrCreateTag();

        if (getEnergy(stack) >= MAX_ENERGY) {
            // ===== NẮM ĐẤM ROBOT: đủ 100% điện năng =====
            Vec3 look = player.getLookAngle();
            Vec3 fwd = new Vec3(look.x, 0, look.z);
            fwd = fwd.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : fwd.normalize();
            Vec3 impact = player.position().add(fwd.scale(BURST_FORWARD));

            tag.putInt("HaloEnergy", 0);
            tag.putLong("BurstTick", now);
            tag.putDouble("BurstX", impact.x);
            tag.putDouble("BurstY", impact.y);
            tag.putDouble("BurstZ", impact.z);
            tag.putFloat("BurstYaw", player.getYRot());
            boolean ench = stack.isEnchanted();
            tag.putBoolean("BurstEnch", ench);
            HaloManager.queueBurst(sp, impact);

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.HALO_BURST.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
        } else if (now >= tag.getLong("SkillCd")) {
            // ===== KỸ NĂNG: khiên + tia điện =====
            tag.putLong("SkillStart", now);
            tag.putLong("SkillUntil", now + SKILL_TICKS);
            tag.putLong("SkillCd", now + SKILL_COOLDOWN);
            tag.putBoolean("SkillEnch", stack.isEnchanted());
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, SKILL_TICKS, ABSORPTION_AMP));
            HaloManager.startSkill(sp);

            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.HALO_SKILL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            return InteractionResultHolder.fail(stack);
        }
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    // ===== Hàm tiện ích (client và server dùng chung) =====
    public static int getEnergy(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt("HaloEnergy") : 0;
    }

    public static void addEnergyTo(ItemStack stack, int amount) {
        stack.getOrCreateTag().putInt("HaloEnergy", Math.min(MAX_ENERGY, getEnergy(stack) + amount));
    }

    public static void addEnergy(Player player, int amount) {
        ItemStack s = held(player);
        if (!s.isEmpty()) {
            int before = getEnergy(s);
            addEnergyTo(s, amount);
            if (before < MAX_ENERGY && getEnergy(s) >= MAX_ENERGY) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        ModSounds.HALO_READY.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    /** Cây Hào Quang Tách Rời đang cầm trên tay (tay chính, không có thì tay phụ). */
    public static ItemStack held(Player player) {
        if (player.getMainHandItem().getItem() instanceof FracturedHaloItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof FracturedHaloItem) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    public static boolean isSkillActive(ItemStack stack, long gameTime) {
        return stack.hasTag() && stack.getTag().getLong("SkillUntil") > gameTime;
    }

    public static long skillCooldownLeft(ItemStack stack, long gameTime) {
        return stack.hasTag() ? Math.max(0L, stack.getTag().getLong("SkillCd") - gameTime) : 0L;
    }

    // ===== Tên và thông tin hiển thị =====
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(this.getDescriptionId(stack)).withStyle(getWeaponTier().boldStyle());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        WeaponTier t = getWeaponTier();
        tip.add(Component.literal(t.stars).withStyle(t.style()));
        tip.add(Component.translatable(t.langKey()).append(" . ").append(Component.translatable("halo.genshinmod.type")).withStyle(ChatFormatting.GRAY));
        tip.add(Component.empty());
        int energy = getEnergy(stack);
        int filled = energy * 20 / MAX_ENERGY;
        tip.add(Component.translatable("halo.genshinmod.charge").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("█".repeat(filled)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("█".repeat(20 - filled)).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(" " + energy + "%").withStyle(ChatFormatting.WHITE)));
        tip.add(Component.empty());

        tip.add(Component.translatable("halo.genshinmod.passive").withStyle(t.boldStyle()));
        tip.add(Component.translatable("halo.genshinmod.passive_1").withStyle(ChatFormatting.GRAY));
        tip.add(Component.empty());

        tip.add(Component.translatable("halo.genshinmod.skill").withStyle(t.boldStyle()));
        tip.add(Component.translatable("halo.genshinmod.skill_2").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("halo.genshinmod.skill_3").withStyle(ChatFormatting.GRAY));
        tip.add(Component.empty());

        tip.add(Component.translatable("halo.genshinmod.until").withStyle(t.boldStyle()));
        tip.add(Component.translatable("halo.genshinmod.until_1").withStyle(ChatFormatting.GRAY));
        if (stack.isEnchanted()) {
            tip.add(Component.translatable("halo.genshinmod.enchanted").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

}
