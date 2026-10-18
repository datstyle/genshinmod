package com.dat.genshinmod.combat;

import com.dat.genshinmod.genshinmod;
import com.dat.genshinmod.item.weapon.FracturedHaloItem;
import com.dat.genshinmod.registry.ModEffects;
import com.dat.genshinmod.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Chạy phía server cho Hào Quang Tách Rời:
 *  - Kỹ năng: mỗi PULSE_INTERVAL tick bắn điện quanh người, gây sát thương + Tê Liệt, tích điện năng.
 *  - Nắm đấm: sau BURST_DELAY tick thì chạm đất, gây sát thương sấm sét diện rộng.
 */
@Mod.EventBusSubscriber(modid = genshinmod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HaloManager {

    private static final class Skill {
        final ServerPlayer owner;
        final long start;
        Skill(ServerPlayer owner, long start) { this.owner = owner; this.start = start; }
    }

    private static final class Burst {
        final ServerPlayer owner;
        final Vec3 pos;
        int ticks;
        Burst(ServerPlayer owner, Vec3 pos, int ticks) { this.owner = owner; this.pos = pos; this.ticks = ticks; }
    }

    private static final List<Skill> SKILLS = new ArrayList<>();
    private static final List<Burst> BURSTS = new ArrayList<>();

    private HaloManager() {}

    public static void startSkill(ServerPlayer player) {
        SKILLS.removeIf(s -> s.owner == player);
        SKILLS.add(new Skill(player, player.level().getGameTime()));
    }

    public static void queueBurst(ServerPlayer player, Vec3 pos) {
        BURSTS.add(new Burst(player, pos, FracturedHaloItem.BURST_DELAY));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Iterator<Skill> si = SKILLS.iterator();
        while (si.hasNext()) {
            Skill s = si.next();
            ServerPlayer p = s.owner;
            if (p.isRemoved() || !p.isAlive()) { si.remove(); continue; }
            long age = p.level().getGameTime() - s.start;
            if (age >= FracturedHaloItem.SKILL_TICKS) { si.remove(); continue; }
            if (age % FracturedHaloItem.PULSE_INTERVAL == 0) pulse(p);
        }

        Iterator<Burst> bi = BURSTS.iterator();
        while (bi.hasNext()) {
            Burst b = bi.next();
            if (b.owner.isRemoved() || !b.owner.isAlive()) { bi.remove(); continue; }
            if (--b.ticks <= 0) {
                impact(b);
                bi.remove();
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SKILLS.clear();
        BURSTS.clear();
    }

    private static boolean valid(ServerPlayer owner, LivingEntity e) {
        return e != owner && e.isAlive() && !(e instanceof ArmorStand) && !e.isAlliedTo(owner);
    }

    /** Một lần bắn điện quanh người. */
    private static void pulse(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        double r = FracturedHaloItem.ARC_RADIUS;
        double reach = r + 0.5;
        AABB box = p.getBoundingBox().inflate(r);
        int hits = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                x -> valid(p, x) && x.distanceToSqr(p) <= reach * reach)) {
            e.invulnerableTime = 0;
            e.hurt(level.damageSources().indirectMagic(p, p), FracturedHaloItem.ARC_DAMAGE);
            e.addEffect(new MobEffectInstance(ModEffects.PARALYSIS.get(), FracturedHaloItem.PARALYZE_TICKS, 0));
            hits++;
        }
        if (hits > 0) FracturedHaloItem.addEnergy(p, hits * FracturedHaloItem.ENERGY_PER_ARC_HIT);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.HALO_PULSE.get(),
                SoundSource.PLAYERS, 0.7F, 1.0F);
    }

    /** Nắm đấm chạm đất: sát thương sấm sét diện rộng + tia sét (chỉ để nhìn, không gây cháy). */
    private static void impact(Burst b) {
        ServerPlayer p = b.owner;
        ServerLevel level = p.serverLevel();
        Vec3 c = b.pos;
        double r = FracturedHaloItem.BURST_RADIUS;

        AABB box = new AABB(c.x - r, c.y - 2.0, c.z - r, c.x + r, c.y + 4.0, c.z + r);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> valid(p, x))) {
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            if (dx * dx + dz * dz > r * r) continue;
            e.invulnerableTime = 0;
            e.hurt(level.damageSources().indirectMagic(p, p), FracturedHaloItem.BURST_DAMAGE);
            e.addEffect(new MobEffectInstance(ModEffects.PARALYSIS.get(), FracturedHaloItem.BURST_PARALYZE_TICKS, 0));
            Vec3 dir = new Vec3(dx, 0, dz);
            dir = dir.lengthSqr() > 1.0E-4 ? dir.normalize() : Vec3.ZERO;
            e.push(dir.x * 0.6, 0.35, dir.z * 0.6);
            e.hurtMarked = true;
        }
        level.playSound(null, c.x, c.y, c.z, ModSounds.HALO_IMPACT.get(),
                SoundSource.PLAYERS, 1.2F, 1.0F);
    }
}
