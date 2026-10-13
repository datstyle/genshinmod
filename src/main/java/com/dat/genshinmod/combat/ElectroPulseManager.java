package com.dat.genshinmod.combat;

import com.dat.genshinmod.network.FxPacket;
import com.dat.genshinmod.network.ModNetwork;
import com.dat.genshinmod.network.ShakePacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

import java.util.*;

/**
 * Vòng điện xung khi chạm đất: gây sát thương theo từng vòng lan ra.
 * Giáo thường: hiệu ứng bằng hạt. Giáo phù phép: server chỉ tính sát thương,
 * còn toàn bộ hình ảnh do máy người chơi vẽ (SlamFx).
 */
public class ElectroPulseManager {
    private static final int TOTAL_TICKS = 57;   // 2.87 giây, khớp độ dài âm thanh

    // { tick bắt đầu, số tick vòng lan ra, bán kính tối đa, sát thương }
    private static final double[][] RINGS = {
            {2,  10, 7.0, 12},   // khớp cú nổ ở 0.1s
            {16, 10, 6.0, 6},    // khớp nhịp ở 0.8s
            {38, 10, 5.0, 4},    // khớp nhịp ở 1.9s
    };

    // Bản phù phép mạnh hơn và rộng hơn (phải khớp ENCHANT_RADIUS trong SlamFx)
    private static final float ENCHANT_DAMAGE = 1.5F;
    private static final double ENCHANT_RADIUS = 1.3;

    // Bật/tắt rung màn hình (đang TẮT)
    private static final boolean SHAKE_ENABLED = false;

    private static final List<Pulse> PULSES = new ArrayList<>();

    private static class Pulse {
        final ServerLevel level;
        final UUID owner;
        final Vec3 center;
        final boolean enchanted;
        final List<Set<Integer>> hit = new ArrayList<>();
        int age = 0;

        Pulse(ServerLevel level, UUID owner, Vec3 center, boolean enchanted) {
            this.level = level;
            this.owner = owner;
            this.center = center;
            this.enchanted = enchanted;
            for (int i = 0; i < RINGS.length; i++) hit.add(new HashSet<>());
        }
    }

    public static void start(ServerPlayer player) {
        start(player, false);
    }

    public static void start(ServerPlayer player, boolean enchanted) {
        ServerLevel level = player.serverLevel();
        Vec3 c = player.position();
        PULSES.add(new Pulse(level, player.getUUID(), c, enchanted));

        if (enchanted) {   // báo cho máy người chơi ở gần dựng hiệu ứng hình ảnh
            ModNetwork.sendNear(level, c, 64.0,
                    new FxPacket(FxPacket.LAND, player.getId(), c.x, c.y, c.z, level.random.nextLong(), true));
        } else {
            level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PULSES.isEmpty()) return;
        Iterator<Pulse> it = PULSES.iterator();
        while (it.hasNext()) {
            if (!step(it.next())) it.remove();
        }
    }

    private static boolean step(Pulse p) {
        Player owner = p.level.getPlayerByUUID(p.owner);
        if (owner == null || p.age > TOTAL_TICKS) return false;

        double radiusScale = p.enchanted ? ENCHANT_RADIUS : 1.0;
        float damageScale = p.enchanted ? ENCHANT_DAMAGE : 1.0F;

        for (int i = 0; i < RINGS.length; i++) {
            double[] r = RINGS[i];
            int local = p.age - (int) r[0];
            if (local < 0 || local > r[1]) continue;

            double progress = local / r[1];
            double radius = r[2] * radiusScale * Math.sin(progress * Math.PI / 2);   // lan nhanh lúc đầu, chậm dần
            if (!p.enchanted) drawRing(p, radius);   // giáo phù phép: vòng do máy người chơi vẽ
            damageRing(p, owner, i, radius, (float) r[3] * damageScale);
        }

        // Giáo thường: tia điện lấp lánh quanh vùng, thưa dần theo âm thanh
        if (!p.enchanted && p.age % 2 == 0) {
            double fade = 1.0 - (double) p.age / TOTAL_TICKS;
            int count = (int) (6 * fade) + 1;
            for (int k = 0; k < count; k++) {
                double a = p.level.random.nextDouble() * Math.PI * 2;
                double d = p.level.random.nextDouble() * 6.0;
                p.level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        p.center.x + Math.cos(a) * d, p.center.y + 0.1 + p.level.random.nextDouble() * 1.2,
                        p.center.z + Math.sin(a) * d, 1, 0.1, 0.2, 0.1, 0.03);
            }
        }

        // Rung màn hình ba nhịp, khớp ba nhịp nổ của âm thanh (chỉ giáo phù phép)
        if (p.enchanted && SHAKE_ENABLED) {
            if (p.age == 2) shake(p, 1.4F, 14);
            else if (p.age == 16) shake(p, 0.8F, 8);
            else if (p.age == 38) shake(p, 0.5F, 6);
        }

        p.age++;
        return true;
    }

    private static void shake(Pulse p, float intensity, int duration) {
        Vec3 c = p.center;
        ModNetwork.sendNear(p.level, c, 48.0, new ShakePacket(intensity, duration, c.x, c.y, c.z));
    }

    private static void drawRing(Pulse p, double radius) {
        if (radius < 0.3) return;
        int points = (int) (radius * 7) + 10;
        for (int k = 0; k < points; k++) {
            double a = 2 * Math.PI * k / points;
            double x = p.center.x + Math.cos(a) * radius;
            double z = p.center.z + Math.sin(a) * radius;
            p.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, p.center.y + 0.15, z, 1, 0.03, 0.05, 0.03, 0.02);
            if (k % 3 == 0) {
                p.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, p.center.y + 0.9, z, 1, 0.03, 0.15, 0.03, 0.02);
            }
        }
    }

    private static void damageRing(Pulse p, Player owner, int ringIndex, double radius, float damage) {
        AABB box = new AABB(p.center, p.center).inflate(radius + 2.0, 3.0, radius + 2.0);
        for (LivingEntity e : p.level.getEntitiesOfClass(LivingEntity.class, box, x -> x != owner && x.isAlive())) {
            if (p.hit.get(ringIndex).contains(e.getId())) continue;

            double dx = e.getX() - p.center.x;
            double dz = e.getZ() - p.center.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(dist - radius) > 1.3 + e.getBbWidth() * 0.5) continue;

            p.hit.get(ringIndex).add(e.getId());
            float dmg = damage + (e.isInWaterOrRain() ? 3f : 0f);   // địch ướt nhận thêm
            e.invulnerableTime = 0;
            e.hurt(owner.damageSources().playerAttack(owner), dmg);
            if (dist > 0.01) e.knockback(0.5, -dx / dist, -dz / dist);
        }
    }
}