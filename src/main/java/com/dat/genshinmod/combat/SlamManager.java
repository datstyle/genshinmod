package com.dat.genshinmod.combat;

import com.dat.genshinmod.network.FxPacket;
import com.dat.genshinmod.network.ModNetwork;
import com.dat.genshinmod.sound.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Chiêu dậm: bay lên, lơ lửng một nhịp, lao xuống rồi nổ vòng điện xung.
 * Mọi chuyển động do SERVER điều khiển trực tiếp (dịch chuyển từng bước) thay vì đặt vận tốc,
 * để máy người chơi và server không "cãi nhau" về vị trí (nguyên nhân gây bị văng / giật về).
 */
public class SlamManager {
    private static final double RISE_HEIGHT = 3.6;    // độ cao bay lên (block)
    private static final int RISE_TICKS = 8;           // số tick bay lên
    private static final int HOVER_TICKS = 4;          // lơ lửng trên không (0.2 giây)
    private static final double SLAM_SPEED = 2.2;      // lao xuống bao nhiêu block mỗi tick
    private static final int MAX_TICKS = 60;           // an toàn: quá lâu thì hủy

    private enum Phase { RISE, HOVER, FALL }

    private static class Slam {
        Phase phase = Phase.RISE;
        int age = 0;
        int counter = 0;
        double offset = 0;      // đã bay lên cao bao nhiêu so với lúc bắt đầu
        boolean enchanted;
    }

    private static final Map<UUID, Slam> SLAMS = new HashMap<>();

    public static void start(ServerPlayer player) {
        if (SLAMS.containsKey(player.getUUID())) return;

        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.fallDistance = 0;

        ServerLevel level = player.serverLevel();
        boolean enchanted = player.getMainHandItem().isEnchanted();   // giáo phù phép thì bản đặc biệt
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.DASH.get(), SoundSource.PLAYERS, 1.0f, 0.8f);
        if (!enchanted) {   // giáo phù phép: hình ảnh do máy người chơi vẽ (SlamFx), không dùng hạt
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.2, player.getZ(),
                    25, 0.4, 0.1, 0.4, 0.08);
        }

        Slam s = new Slam();
        s.enchanted = enchanted;
        SLAMS.put(player.getUUID(), s);

        if (s.enchanted) {   // máy người chơi ở gần sẽ vẽ xoáy điện + cung vàng quanh người
            ModNetwork.sendNear(level, player.position(), 64.0,
                    new FxPacket(FxPacket.RISE, player.getId(), player.getX(), player.getY(), player.getZ(), 0L, true));
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SLAMS.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        Iterator<Map.Entry<UUID, Slam>> it = SLAMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Slam> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive() || p.isSpectator() || !step(p, e.getValue())) it.remove();
        }
    }

    private static boolean step(ServerPlayer p, Slam s) {
        ServerLevel level = p.serverLevel();
        s.age++;
        if (s.age > MAX_TICKS) return false;
        p.fallDistance = 0;   // không bị sát thương rơi

        if (s.phase == Phase.RISE) {
            s.counter++;
            double target = RISE_HEIGHT * Math.sin((double) s.counter / RISE_TICKS * Math.PI / 2);
            boolean moved = shift(p, target - s.offset);
            s.offset = target;

            if (!s.enchanted) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.3, p.getZ(),
                        5, 0.3, 0.2, 0.3, 0.03);
            }
            if (!moved || s.counter >= RISE_TICKS) { s.phase = Phase.HOVER; s.counter = 0; }
            return true;
        }

        if (s.phase == Phase.HOVER) {
            shift(p, 0);   // giữ nguyên vị trí trên không
            if (!s.enchanted) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(),
                        10, 0.5, 0.5, 0.5, 0.06);
            }
            if (++s.counter >= HOVER_TICKS) { s.phase = Phase.FALL; s.counter = 0; }
            return true;
        }

        // FALL: lao xuống, dừng khi chạm đất
        double before = p.getY();
        boolean moved = shift(p, -SLAM_SPEED);
        if (!s.enchanted) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(),
                    8, 0.2, 0.9, 0.2, 0.02);
        }
        double dropped = before - p.getY();
        if (!moved || dropped < SLAM_SPEED - 0.01) {   // bị chặn lại nghĩa là đã chạm đất
            land(p, level, s.enchanted);
            return false;
        }
        return true;
    }

    /** Dịch người chơi theo chiều dọc (có va chạm) rồi báo vị trí mới cho máy người chơi. */
    private static boolean shift(ServerPlayer p, double dy) {
        double before = p.getY();
        if (dy != 0) p.move(MoverType.SELF, new Vec3(0, dy, 0));
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        // Hướng nhìn để TƯƠNG ĐỐI: máy người chơi giữ nguyên góc nhìn của chính họ, không bị khoá chuột
        p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(),
                EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
        return dy == 0 || Math.abs(p.getY() - before) > 1.0E-4;
    }

    private static void land(ServerPlayer p, ServerLevel level, boolean enchanted) {
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.fallDistance = 0;
        ElectroPulseManager.start(p, enchanted);   // vòng điện xung (bản phù phép có thêm hiệu ứng)
        level.playSound(null, p.getX(), p.getY(), p.getZ(),
                ModSounds.BURST.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
        if (!enchanted) {
            level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.1, p.getZ(),
                    3, 0.8, 0.1, 0.8, 0.0);
        }
    }
}