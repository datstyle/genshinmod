package com.dat.genshinmod.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

final class HaloVisualState {
    private static final long NONE = Long.MIN_VALUE / 2;
    private static final Map<Integer, HaloVisualState> STATES = new HashMap<>();
    private static ClientLevel lastLevel;

    // Khiên
    long skillStart = NONE;
    long skillUntil = NONE;
    boolean skillEnch;

    // Chiêu cuối
    long burstTick = NONE;
    double burstX, burstY, burstZ;
    float burstYaw;
    boolean burstEnch;

    private HaloVisualState() {}

    /** Gọi 1 lần đầu mỗi lần render: xoá bản lưu khi đổi thế giới và dọn người chơi đã biến mất. */
    static void begin(ClientLevel level) {
        if (level != lastLevel) {
            STATES.clear();
            lastLevel = level;
        }
        STATES.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    /** Cập nhật từ cây vũ khí đang cầm (nếu có) rồi trả về trạng thái đã lưu của người chơi. */
    static HaloVisualState of(Player player, ItemStack held) {
        HaloVisualState s = STATES.computeIfAbsent(player.getId(), k -> new HaloVisualState());
        if (!held.isEmpty() && held.hasTag()) s.read(held.getTag());
        return s;
    }

    private void read(CompoundTag tag) {
        if (tag == null) return;
        long until = tag.getLong("SkillUntil");
        if (until >= skillUntil) {
            skillUntil = until;
            skillStart = tag.getLong("SkillStart");
            skillEnch = tag.getBoolean("SkillEnch");
        }
        if (tag.contains("BurstTick")) {
            long bt = tag.getLong("BurstTick");
            if (bt >= burstTick) {
                burstTick = bt;
                burstX = tag.getDouble("BurstX");
                burstY = tag.getDouble("BurstY");
                burstZ = tag.getDouble("BurstZ");
                burstYaw = tag.getFloat("BurstYaw");
                burstEnch = tag.getBoolean("BurstEnch");
            }
        }
    }
}