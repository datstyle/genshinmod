package com.dat.genshinmod.client.fx;

import com.dat.genshinmod.item.weapon.FracturedHaloItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

/**
 * Làm tối nhẹ màn hình (≤ 38%) trong lúc màn hình hologram của chiêu cuối xuất hiện — giống đoạn thế giới
 * tối đi trong video. Chỉ áp dụng khi đứng trong 18 block quanh điểm nổ.
 *
 * Hạt (sương, tia lửa) đã được thay bằng hiệu ứng vẽ chi tiết trong HaloBurstFx.aftermath(),
 * nên class này KHÔNG còn onClientTick và không dùng particle nào.
 * Đọc các tag có sẵn trên vũ khí: BurstTick / BurstX / BurstZ. Không cần sửa server hay thêm packet.
 */
public final class HaloBurstAftermath {
    private HaloBurstAftermath() {}

    public static void onGui(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.VIGNETTE.id())) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        long now = level.getGameTime();
        float pt = event.getPartialTick();

        float best = 0f;
        for (Player p : level.players()) {
            ItemStack held = FracturedHaloItem.held(p);
            if (held.isEmpty() || !held.hasTag()) continue;
            CompoundTag tag = held.getTag();
            if (tag == null || !tag.contains("BurstTick")) continue;
            float age = (float) (now - tag.getLong("BurstTick")) + pt;
            if (age < 8f || age > 28f) continue;

            double dx = mc.player.getX() - tag.getDouble("BurstX");
            double dz = mc.player.getZ() - tag.getDouble("BurstZ");
            double dist = Math.sqrt(dx * dx + dz * dz);
            float near = (float) Math.max(0.0, 1.0 - dist / 18.0);

            float rise = Math.min(1f, (age - 8f) / 3f);   // tối dần trong 3 tick
            float fall = Math.min(1f, (28f - age) / 8f);  // sáng lại trong 8 tick cuối
            best = Math.max(best, Math.min(rise, fall) * near);
        }
        if (best <= 0.01f) return;

        GuiGraphics g = event.getGuiGraphics();
        int a = (int) (best * 0.38f * 255f);
        g.fill(0, 0, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(),
               (a << 24) | 0x0A0620);
    }
}