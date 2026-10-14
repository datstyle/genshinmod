package com.dat.genshinmod.client;

import com.dat.genshinmod.genshinmod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Vẽ Hào Quang Tách Rời: thân vũ khí đứng yên + lưỡi cưa xoay NGƯỢC chiều kim đồng hồ quanh trục giữa.
 * Lưỡi cưa luôn sáng (không bị tối theo môi trường) để có cảm giác phát sáng.
 */
public class HaloItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation BODY =
            new ResourceLocation(genshinmod.MODID, "item/fractured_halo_body");
    public static final ResourceLocation SAW =
            new ResourceLocation(genshinmod.MODID, "item/fractured_halo_saw");

    // Số pixel đã nâng phần đầu lên khi kéo dài thân (khớp với file fractured_halo_body.json / _saw.json).
    // Muốn kéo dài thân thêm hay ngắn bớt thì sửa số này CÙNG LÚC với việc sửa 2 file json.
    private static final float HEAD_LIFT = 6.0F;

    // Tâm lưỡi cưa trong model: (8, 19.08 + HEAD_LIFT, 8) / 16
    private static final float PX = 8.0F / 16F;
    private static final float PY = (19.08F + HEAD_LIFT) / 16F;
    private static final float PZ = 8.0F / 16F;

    // Tốc độ quay (độ/giây). Số DƯƠNG = ngược chiều kim đồng hồ khi nhìn thẳng vào mặt lưỡi cưa
    // (mặt có tấm giáp trắng nằm bên phải). Muốn quay ngược lại thì đổi thành số âm.
    private static final float SPIN_DEG_PER_SEC = 140.0F;

    public HaloItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
              Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                             MultiBufferSource buffer, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer ir = mc.getItemRenderer();
        BakedModel body = mc.getModelManager().getModel(BODY);
        BakedModel saw = mc.getModelManager().getModel(SAW);

        // 1. Thân vũ khí (đứng yên)
        renderPart(ir, body, stack, pose, buffer, light, overlay, stack.hasFoil());

        // 2. Lưỡi cưa: quay quanh tâm (cố định theo thời gian thật, không phụ thuộc FPS)
        float angle = (float) (((Util.getMillis() % 360000L) / 1000.0 * SPIN_DEG_PER_SEC) % 360.0);
        pose.pushPose();
        pose.translate(PX, PY, PZ);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        pose.translate(-PX, -PY, -PZ);
        renderPart(ir, saw, stack, pose, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
        pose.popPose();
    }

    private static void renderPart(ItemRenderer ir, BakedModel model, ItemStack stack, PoseStack pose,
                                   MultiBufferSource buffer, int light, int overlay, boolean foil) {
        for (RenderType rt : model.getRenderTypes(stack, true)) {
            VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buffer, rt, true, foil);
            ir.renderModelLists(model, stack, light, overlay, pose, vc);
        }
    }
}