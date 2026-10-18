package com.dat.genshinmod.client.render;

import com.dat.genshinmod.registry.ModItems;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

public class SpearBackLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final int REFRESH_TICKS = 10;

    // Chỉ số của từng vũ khí trong mảng
    private static final int SPEAR = 0, STAFF = 1, HALO = 2, COUNT = 3;

    // ID item của Hào Quang Tách Rời. Nếu bạn đăng ký tên khác thì sửa chuỗi này.
    private static final ResourceLocation HALO_ID = new ResourceLocation("genshinmod", "fractured_halo");

    private static Item haloItem;   // tra cứu một lần rồi nhớ lại

    private static final class Cache {
        long lastTick = -1L;   // -1 = chưa tìm lần nào
        final ItemStack[] stacks = new ItemStack[COUNT];
        final int[] slots = new int[COUNT];
        long lastFxTick = Long.MIN_VALUE;

        Cache() {
            java.util.Arrays.fill(stacks, ItemStack.EMPTY);
            java.util.Arrays.fill(slots, Integer.MAX_VALUE);
        }
    }

    private static final Map<Integer, Cache> CACHE = new HashMap<>();

    public SpearBackLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    private static Item itemOf(int which) {
        switch (which) {
            case SPEAR: return ModItems.BLOODSOAKED_RUINS.get();
            case STAFF: return ModItems.STAFF_OF_HOMA.get();
            default:
                if (haloItem == null) {
                    Item i = ForgeRegistries.ITEMS.getValue(HALO_ID);
                    haloItem = (i == null) ? Items.AIR : i;   // AIR = chưa có cây này, bỏ qua
                }
                return haloItem;
        }
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || player.isSpectator()) return;

        Cache c = refresh(player);

        // Chỉ đeo MỘT cây: cây nằm ở ô thấp hơn trong túi đồ (thanh nhanh ưu tiên trước).
        // Cây nào đang cầm trên tay thì bỏ qua.
        int best = -1;
        for (int i = 0; i < COUNT; i++) {
            if (c.stacks[i].isEmpty()) continue;
            if (holdingItem(player, itemOf(i))) continue;
            if (best < 0 || c.slots[i] < c.slots[best]) best = i;
        }
        if (best < 0) return;
        ItemStack stack = c.stacks[best];

        float idleBob = Mth.sin(ageInTicks * 0.12F) * 0.035F;
        float walkBob = Mth.cos(limbSwing * 0.6662F * 2.0F) * 0.04F * limbSwingAmount;
        float bob = idleBob + walkBob;
        float sway = Mth.sin(ageInTicks * 0.12F + 1.0F) * 2.0F;

        // Tất cả dùng chung vị trí và độ nghiêng với giáo Blood
        drawOnBack(pose, buffer, light, player, stack, bob, 0.34F, -25.0F + sway);

        if (best == STAFF) spawnFlameIfCharged(player, c, stack);
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
            for (int i = 0; i < COUNT; i++) {
                c.slots[i] = findSlot(inv, itemOf(i));
                c.stacks[i] = c.slots[i] == Integer.MAX_VALUE ? ItemStack.EMPTY : inv.getItem(c.slots[i]);
            }
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
        if (item == Items.AIR) return Integer.MAX_VALUE;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) return i;
        }
        return Integer.MAX_VALUE;
    }
}