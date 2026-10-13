package com.dat.genshinmod.client;

import com.dat.genshinmod.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import org.joml.Vector3f;

/**
 * Khi giáo được phù phép: bốc tia điện xanh nhạt liên tục
 * (cầm trên tay, đeo sau lưng, hoặc rơi dưới đất). Chưa phù phép thì không có gì.
 */
public class EnchantAura {
    // Màu xanh nhạt (R, G, B từ 0 đến 1) và kích thước hạt
    private static final DustParticleOptions PALE_BLUE =
            new DustParticleOptions(new Vector3f(0.62F, 0.86F, 1.0F), 0.8F);

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;
        if (level.getGameTime() % 2 != 0) return;   // cứ 2 tick một lần cho nhẹ máy

        Item spear = ModItems.BLOODSOAKED_RUINS.get();

        for (Player p : level.players()) {
            ItemStack hand = p.getMainHandItem();
            if (hand.is(spear) && hand.isEnchanted()) {
                aroundHeld(level, p);
            } else if (p == mc.player && hasEnchantedInInventory(p, spear)) {
                aroundBack(level, p);
            }
        }

        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof ItemEntity ie) {
                ItemStack s = ie.getItem();
                if (s.is(spear) && s.isEnchanted()) aroundDropped(level, ie);
            }
        }
    }

    private static boolean hasEnchantedInInventory(Player p, Item spear) {
        for (ItemStack s : p.getInventory().items) {
            if (s.is(spear) && s.isEnchanted()) return true;
        }
        return false;
    }

    // Cầm trên tay: dọc theo thân giáo (chéo lên phía trước)
    private static void aroundHeld(ClientLevel level, Player p) {
        double yaw = Math.toRadians(p.yBodyRot);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 base = p.position().add(0, 1.0, 0).add(right.scale(0.4)).add(fwd.scale(0.35));
        Vec3 dir = fwd.scale(0.55).add(0, 0.85, 0).normalize();
        spawnAlong(level, base, dir, -1.2, 1.3);
    }

    // Đeo sau lưng: dọc theo giáo chéo sau lưng
    private static void aroundBack(ClientLevel level, Player p) {
        double yaw = Math.toRadians(p.yBodyRot);
        Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 base = p.position().add(0, 1.1, 0).subtract(fwd.scale(0.35));
        Vec3 dir = new Vec3(0, 0.9, 0).add(right.scale(0.35)).normalize();
        spawnAlong(level, base, dir, -0.9, 1.1);
    }

    // Rơi dưới đất: tỏa quanh vật phẩm
    private static void aroundDropped(ClientLevel level, ItemEntity ie) {
        RandomSource r = level.random;
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    ie.getX() + (r.nextDouble() - 0.5) * 1.0, ie.getY() + 0.1 + r.nextDouble() * 0.4,
                    ie.getZ() + (r.nextDouble() - 0.5) * 1.0,
                    (r.nextDouble() - 0.5) * 0.05, 0.02, (r.nextDouble() - 0.5) * 0.05);
        }
        level.addParticle(PALE_BLUE, ie.getX() + (r.nextDouble() - 0.5) * 0.8, ie.getY() + 0.2,
                ie.getZ() + (r.nextDouble() - 0.5) * 0.8, 0, 0.01, 0);
    }

    private static void spawnAlong(ClientLevel level, Vec3 base, Vec3 dir, double t0, double t1) {
        RandomSource r = level.random;
        for (int i = 0; i < 2; i++) {   // tia điện trắng xanh
            Vec3 pos = base.add(dir.scale(t0 + r.nextDouble() * (t1 - t0)));
            level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    pos.x + (r.nextDouble() - 0.5) * 0.2, pos.y + (r.nextDouble() - 0.5) * 0.2,
                    pos.z + (r.nextDouble() - 0.5) * 0.2,
                    (r.nextDouble() - 0.5) * 0.05, (r.nextDouble() - 0.5) * 0.05, (r.nextDouble() - 0.5) * 0.05);
        }
        Vec3 pos = base.add(dir.scale(t0 + r.nextDouble() * (t1 - t0)));   // hạt bụi xanh nhạt
        level.addParticle(PALE_BLUE, pos.x, pos.y, pos.z, 0, 0.01, 0);
    }
}