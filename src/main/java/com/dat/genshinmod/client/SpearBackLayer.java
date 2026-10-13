package com.dat.genshinmod.client;

import com.dat.genshinmod.item.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class SpearBackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final int REFRESH_TICKS = 10;

    private static final class Cache {
        long lastTick = -1L;   // -1 = chưa tìm lần nào
        ItemStack spear = ItemStack.EMPTY;
        ItemStack staff = ItemStack.EMPTY;
        int spearSlot = Integer.MAX_VALUE;
        int staffSlot = Integer.MAX_VALUE;
        long lastFxTick = Long.MIN_VALUE;
    }

    private static final Map<Integer, Cache> CACHE = new HashMap<>();

    public SpearBackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || player.isSpectator()) return;

        Cache c = refresh(player);
        // Cây nào đang cầm trên tay thì không đeo sau lưng
        boolean spearOk = !c.spear.isEmpty() && !holdingItem(player, ModItems.BLOODSOAKED_RUINS.get());
        boolean staffOk = !c.staff.isEmpty() && !holdingItem(player, ModItems.STAFF_OF_HOMA.get());
        if (!spearOk && !staffOk) return;

        // Chỉ đeo MỘT cây: cây nằm ở ô thấp hơn trong túi đồ (thanh nhanh ưu tiên trước)
        boolean drawStaff = staffOk && (!spearOk || c.staffSlot < c.spearSlot);
        ItemStack stack = drawStaff ? c.staff : c.spear;

        float idleBob = Mth.sin(ageInTicks * 0.12F) * 0.035F;
        float walkBob = Mth.cos(limbSwing * 0.6662F * 2.0F) * 0.04F * limbSwingAmount;
        float bob = idleBob + walkBob;
        float sway = Mth.sin(ageInTicks * 0.12F + 1.0F) * 2.0F;

        // Cả hai dùng chung vị trí và độ nghiêng với giáo Blood
        drawOnBack(pose, buffer, light, player, stack, bob, 0.34F, -25.0F + sway);

        if (drawStaff) spawnFlameIfCharged(player, c, stack);
    }

    private void drawOnBack(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                            ItemStack stack, float bob, float depth, float tilt) {
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        pose.translate(0.0F, 0.05F + bob, depth);
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(tilt));
        pose.mulPose(Axis.XP.rotationDegrees(6.0F));
        pose.scale(0.85F, 0.85F, 0.85F);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                player, stack, ItemDisplayContext.NONE, false, pose, buffer,
                player.level(), light, OverlayTexture.NO_OVERLAY, player.getId());
        pose.popPose();
    }

    /** Tia lửa nhỏ bay ra từ đầu trượng đeo sau lưng khi đủ 3 Linh Hỏa (tối đa một lần mỗi tick). */
    private static void spawnFlameIfCharged(AbstractClientPlayer p, Cache c, ItemStack staff) {
        if (!staff.hasTag() || staff.getTag().getInt("Flame") < 3) return;
        long now = p.level().getGameTime();
        if (now == c.lastFxTick || p.getRandom().nextInt(3) != 0) return;
        c.lastFxTick = now;

        double rad = Math.toRadians(p.yBodyRot);
        double backX = Math.sin(rad) * 0.35;     // phía sau lưng người chơi
        double backZ = -Math.cos(rad) * 0.35;
        double headY = p.getY() + (p.isCrouching() ? 1.9 : 2.2);
        double ox = (p.getRandom().nextDouble() - 0.5) * 0.3;
        double oz = (p.getRandom().nextDouble() - 0.5) * 0.3;
        p.level().addParticle(ParticleTypes.FLAME, p.getX() + backX + ox, headY, p.getZ() + backZ + oz,
                0.0, 0.02, 0.0);
    }

    private static Cache refresh(AbstractClientPlayer p) {
        Cache c = CACHE.computeIfAbsent(p.getId(), id -> new Cache());
        long now = p.level().getGameTime();
        if (c.lastTick < 0 || now - c.lastTick >= REFRESH_TICKS || now < c.lastTick) {
            c.lastTick = now;
            Inventory inv = p.getInventory();
            c.spearSlot = findSlot(inv, ModItems.BLOODSOAKED_RUINS.get());
            c.staffSlot = findSlot(inv, ModItems.STAFF_OF_HOMA.get());
            c.spear = c.spearSlot == Integer.MAX_VALUE ? ItemStack.EMPTY : inv.getItem(c.spearSlot);
            c.staff = c.staffSlot == Integer.MAX_VALUE ? ItemStack.EMPTY : inv.getItem(c.staffSlot);
            if (CACHE.size() > 128) {   // dọn bớt người chơi đã rời đi
                CACHE.clear();
                CACHE.put(p.getId(), c);
            }
        }
        return c;
    }

    private static boolean holdingItem(AbstractClientPlayer p, Item item) {
        return p.getMainHandItem().is(item) || p.getOffhandItem().is(item);
    }

    /** Trả về chỉ số ô đầu tiên chứa item, hoặc Integer.MAX_VALUE nếu không có. */
    private static int findSlot(Inventory inv, Item item) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) return i;
        }
        return Integer.MAX_VALUE;
    }
}