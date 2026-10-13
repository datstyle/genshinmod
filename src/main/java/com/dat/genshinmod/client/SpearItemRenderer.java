package com.dat.genshinmod.client;

import com.dat.genshinmod.genshinmod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Vẽ cây giáo: thân giáo + đèn lồng (rủ xuống theo trọng lực, đung đưa).
 * Khi giáo được phù phép: thêm quầng sáng xanh nhạt "bốc cháy" quanh mũi giáo.
 */
public class SpearItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation MAIN =
            new ResourceLocation(genshinmod.MODID, "item/bloodsoaked_ruins_main");
    public static final ResourceLocation LANTERN =
            new ResourceLocation(genshinmod.MODID, "item/bloodsoaked_ruins_lantern");
    public static final ResourceLocation AURA_HEAD =
            new ResourceLocation(genshinmod.MODID, "item/bloodsoaked_ruins_aura_head");
    public static final ResourceLocation AURA_LANTERN =
            new ResourceLocation(genshinmod.MODID, "item/bloodsoaked_ruins_aura_lantern");

    // Điểm treo đèn (đỉnh sợi xích), đơn vị block: (9.75, 24, 8) / 16
    private static final float PX = 9.75F / 16F;
    private static final float PY = 24.0F / 16F;
    private static final float PZ = 0.5F;

    // Tâm quầng sáng quanh mũi giáo (8, 27, 8) / 16 và quanh đèn lồng (9.75, 16, 8) / 16
    private static final float HX = 0.5F, HY = 27.0F / 16F, HZ = 0.5F;
    private static final float LX = 9.75F / 16F, LY = 1.0F, LZ = 0.5F;

    public SpearItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
              Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose,
                             MultiBufferSource buffer, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer ir = mc.getItemRenderer();
        BakedModel main = mc.getModelManager().getModel(MAIN);
        BakedModel lantern = mc.getModelManager().getModel(LANTERN);

        boolean aura = stack.isEnchanted() && ctx != ItemDisplayContext.GUI;

        // 1. Thân giáo
        renderPart(ir, main, stack, pose, buffer, light, overlay, stack.hasFoil());
        if (aura) {
            renderAura(ir, mc.getModelManager().getModel(AURA_HEAD), stack, pose, buffer, HX, HY, HZ);
        }

        // 2. Đèn lồng: quay sao cho rủ xuống theo trọng lực, quanh điểm treo
        Vector3f down = hangDirection(ctx, pose);
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0F, -1F, 0F), down);

        pose.pushPose();
        pose.translate(PX, PY, PZ);
        pose.mulPose(q);
        pose.translate(-PX, -PY, -PZ);
        renderPart(ir, lantern, stack, pose, buffer, light, overlay, stack.hasFoil());
        pose.popPose();
    }

    private static void renderPart(ItemRenderer ir, BakedModel model, ItemStack stack, PoseStack pose,
                                   MultiBufferSource buffer, int light, int overlay, boolean foil) {
        for (RenderType rt : model.getRenderTypes(stack, true)) {
            VertexConsumer vc = ItemRenderer.getFoilBufferDirect(buffer, rt, true, foil);
            ir.renderModelLists(model, stack, light, overlay, pose, vc);
        }
    }

    /** Quầng sáng mỏng 2 lớp, phập phồng nhẹ, luôn sáng (không bị tối theo môi trường). */
    private static void renderAura(ItemRenderer ir, BakedModel model, ItemStack stack, PoseStack pose,
                                   MultiBufferSource buffer, float cx, float cy, float cz) {
        double t = Util.getMillis() / 1000.0;
        float inner = (float) (1.0 + 0.04 * Math.sin(t * 4.0));
        float outer = (float) (1.12 + 0.05 * Math.sin(t * 3.0 + 1.3));
        for (float s : new float[]{inner, outer}) {
            pose.pushPose();
            pose.translate(cx, cy, cz);
            pose.scale(s, s, s);
            pose.translate(-cx, -cy, -cz);
            renderPart(ir, model, stack, pose, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
            pose.popPose();
        }
    }

    /** Hướng "xuống" của thế giới, biểu diễn trong hệ toạ độ của chính cây giáo. */
    private static Vector3f hangDirection(ItemDisplayContext ctx, PoseStack pose) {
        Minecraft mc = Minecraft.getInstance();
        Vector3f dView = new Vector3f(0F, -1F, 0F);

        boolean flat = ctx == ItemDisplayContext.GUI || ctx == ItemDisplayContext.FIXED
                || ctx == ItemDisplayContext.HEAD;
        if (!flat) {
            new Quaternionf(mc.gameRenderer.getMainCamera().rotation()).conjugate().transform(dView);

            double speed = mc.player != null ? mc.player.getDeltaMovement().horizontalDistance() : 0.0;
            double t = Util.getMillis() / 1000.0;
            double k = 0.10 + 0.40 * Math.min(1.0, speed * 5.0);
            dView.add((float) (Math.sin(t * 3.2) * k), 0F, (float) (Math.cos(t * 2.5 + 1.0) * k * 0.7));
            dView.normalize();
        }

        Matrix3f inv = new Matrix3f(pose.last().normal()).transpose();
        Vector3f local = inv.transform(dView);
        if (local.lengthSquared() < 1.0E-6F) return new Vector3f(0F, -1F, 0F);
        return local.normalize();
    }
}