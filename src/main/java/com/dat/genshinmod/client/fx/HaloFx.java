package com.dat.genshinmod.client.fx;

import com.dat.genshinmod.genshinmod;
import com.dat.genshinmod.item.weapon.FracturedHaloItem;
import com.dat.genshinmod.registry.ModEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;
@Mod.EventBusSubscriber(modid = genshinmod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HaloFx {
    private static final int PULSE_FLASH = 7;        // tia điện hiện bao nhiêu tick sau mỗi lần bắn
    private static final double MAX_DIST_SQR = 64.0 * 64.0;

    private HaloFx() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        HaloVisualState.begin(level);

        long now = level.getGameTime();
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType glowType = RenderType.lightning();

        // ================= Người chơi cầm Hào Quang Tách Rời =================
        for (AbstractClientPlayer p : level.players()) {
            if (p.distanceToSqr(cam) > MAX_DIST_SQR) continue;
            // Trạng thái hiệu ứng được lưu lại: đổi sang ô khác vẫn vẽ tiếp tới khi skill/chiêu cuối hết hạn
            HaloVisualState st = HaloVisualState.of(p, FracturedHaloItem.held(p));

            double px = Mth.lerp(pt, p.xo, p.getX()) - cam.x;
            double py = Mth.lerp(pt, p.yo, p.getY()) - cam.y;
            double pz = Mth.lerp(pt, p.zo, p.getZ()) - cam.z;

            long burstTick = st.burstTick;
            float burstAge = (now - burstTick) + pt;
            boolean skillOn = st.skillUntil > now;
            boolean fistOn = burstAge >= 0.0f && burstAge < HaloBurstFx.FIST_END;
            if (!skillOn && !fistOn) continue;

            // ---------- Phần 1: vẽ phát sáng (khiên, tia điện, vòng sóng, sét) ----------
            VertexConsumer glow = buffers.getBuffer(glowType);
            pose.pushPose();
            PoseStack.Pose last = pose.last();

            if (skillOn) {
                long start = st.skillStart;
                float age = (now - start) + pt;
                float left = st.skillUntil - now - pt;
                boolean firstPersonSelf = p == mc.player && mc.options.getCameraType().isFirstPerson();
                if (st.skillEnch) {
                    HaloShieldFx.render(glow, last, p.getId(), start, age, left,
                            px, py, pz, firstPersonSelf, (float) FracturedHaloItem.ARC_RADIUS);
                } else {
                    drawShield(glow, last, px, py + 0.9, pz, age, left, firstPersonSelf);
                    drawPulseArcs(glow, last, p.getId(), start, age, px, py + 1.0, pz);
                }
            }
            if (fistOn) {
                double ix = st.burstX - cam.x;
                double iy = st.burstY - cam.y;
                double iz = st.burstZ - cam.z;
                // Chỉ vẽ bằng `glow`. KHÔNG gọi buffers.getBuffer(...) loại khác ở đây: các loại chung 1 bộ đệm nên sẽ làm hỏng `glow`
                HaloBurstFx.render(glow, last, p.getId(), burstTick, burstAge, ix, iy, iz,
                        st.burstYaw, px, py, pz, p.getYRot(), st.burstEnch);
            }
            pose.popPose();
        }

        // ================= Mục tiêu đang Tê Liệt: tia điện quanh người =================
        VertexConsumer glow = buffers.getBuffer(glowType);
        pose.pushPose();
        PoseStack.Pose last = pose.last();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || !le.hasEffect(ModEffects.PARALYSIS.get())) continue;
            if (e.distanceToSqr(cam) > 48.0 * 48.0) continue;
            double ex = Mth.lerp(pt, e.xo, e.getX()) - cam.x;
            double ey = Mth.lerp(pt, e.yo, e.getY()) - cam.y;
            double ez = Mth.lerp(pt, e.zo, e.getZ()) - cam.z;
            float w = e.getBbWidth() + 0.5F, hgt = e.getBbHeight() + 0.2F;
            Random rnd = new Random(e.getId() * 1337L + (now / 2) * 17L);
            for (int k = 0; k < 4; k++) {
                if (rnd.nextFloat() < 0.3F) continue;                          // nhấp nháy
                Vec3 a = new Vec3(ex + (rnd.nextDouble() - 0.5) * w, ey + rnd.nextDouble() * hgt, ez + (rnd.nextDouble() - 0.5) * w);
                Vec3 b = new Vec3(ex + (rnd.nextDouble() - 0.5) * w, ey + rnd.nextDouble() * hgt, ez + (rnd.nextDouble() - 0.5) * w);
                bolt(glow, last, a, b, 0.025F, 0.95F, rnd, 4, 0.25);
            }
        }
        pose.popPose();

        buffers.endBatch(glowType);
    }

    // ============================== Khiên + tia điện ==============================

    private static void drawShield(VertexConsumer vc, PoseStack.Pose pose, double cx, double cy, double cz,
                                   float age, float left, boolean firstPerson) {
        float base = firstPerson ? 0.05F : 0.15F;
        float fadeIn = Math.min(1F, age / 4F);
        float blink = (left < 15F && ((int) left) % 4 < 2) ? 0.35F : 1F;     // nhấp nháy khi sắp hết
        final int LAT = 10, LON = 16;
        final float rx = 1.15F, ry = 1.45F;
        for (int i = 0; i < LAT; i++) {
            float t0 = Mth.PI * i / LAT, t1 = Mth.PI * (i + 1) / LAT;
            for (int j = 0; j < LON; j++) {
                float p0 = Mth.TWO_PI * j / LON, p1 = Mth.TWO_PI * (j + 1) / LON;
                float a = base * fadeIn * blink * (0.7F + 0.3F * Mth.sin(age * 0.3F + i * 0.9F + j * 0.6F));
                int al = (int) (Mth.clamp(a, 0F, 1F) * 255);
                double[][] v = {
                        sph(cx, cy, cz, rx, ry, t0, p0), sph(cx, cy, cz, rx, ry, t0, p1),
                        sph(cx, cy, cz, rx, ry, t1, p1), sph(cx, cy, cz, rx, ry, t1, p0)};
                quad2(vc, pose, v, 110, 205, 255, al);
            }
        }
    }

    private static double[] sph(double cx, double cy, double cz, float rx, float ry, float t, float p) {
        return new double[]{cx + rx * Mth.sin(t) * Mth.cos(p), cy + ry * Mth.cos(t), cz + rx * Mth.sin(t) * Mth.sin(p)};
    }

    private static void drawPulseArcs(VertexConsumer vc, PoseStack.Pose pose, int id, long start, float age,
                                      double cx, double cy, double cz) {
        float pulseAge = age % FracturedHaloItem.PULSE_INTERVAL;
        if (pulseAge > PULSE_FLASH) return;
        int pulseIdx = (int) (age / FracturedHaloItem.PULSE_INTERVAL);
        float fade = 1F - pulseAge / PULSE_FLASH;
        Random rnd = new Random(start * 31L + pulseIdx * 977L + ((int) (pulseAge / 2F)) * 131L + id * 7919L);
        float R = (float) FracturedHaloItem.ARC_RADIUS;
        Vec3 origin = new Vec3(cx, cy, cz);
        for (int k = 0; k < 8; k++) {
            float ang = (k + rnd.nextFloat() * 0.6F) * (Mth.TWO_PI / 8F);
            Vec3 end = new Vec3(cx + Mth.cos(ang) * R, cy + (rnd.nextFloat() - 0.5F) * 0.9F, cz + Mth.sin(ang) * R);
            bolt(vc, pose, origin, end, 0.05F, fade, rnd, 6, 0.4);
        }
    }
    // ============================== Hình học phát sáng ==============================

    private static void cv(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z, int r, int g, int b, int a) {
        vc.vertex(pose.pose(), (float) x, (float) y, (float) z).color(r, g, b, a).endVertex();
    }

    /** Quad hai mặt (không bị cull) với màu đơn. */
    private static void quad2(VertexConsumer vc, PoseStack.Pose pose, double[][] v, int r, int g, int b, int a) {
        for (int i = 0; i < 4; i++) cv(vc, pose, v[i][0], v[i][1], v[i][2], r, g, b, a);
        for (int i = 3; i >= 0; i--) cv(vc, pose, v[i][0], v[i][1], v[i][2], r, g, b, a);
    }

    /** Tia điện zíc zắc từ a đến b (tọa độ đã trừ vị trí camera). Hai lớp: quầng xanh và lõi trắng. */
    private static void bolt(VertexConsumer vc, PoseStack.Pose pose, Vec3 a, Vec3 b, float width, float alpha,
                             Random rnd, int segs, double jitter) {
        Vec3[] pts = new Vec3[segs + 1];
        pts[0] = a;
        pts[segs] = b;
        for (int i = 1; i < segs; i++) {
            Vec3 base = a.lerp(b, i / (double) segs);
            pts[i] = base.add((rnd.nextDouble() - 0.5) * jitter, (rnd.nextDouble() - 0.5) * jitter, (rnd.nextDouble() - 0.5) * jitter);
        }
        float al = Mth.clamp(alpha, 0F, 1F);
        for (int pass = 0; pass < 2; pass++) {
            float w = pass == 0 ? width * 3.2F : width;
            int r = pass == 0 ? 80 : 235, g = pass == 0 ? 170 : 248, bl = 255;
            int a8 = (int) (al * (pass == 0 ? 0.35F : 1F) * 255);
            for (int i = 0; i < segs; i++) strip(vc, pose, pts[i], pts[i + 1], w, r, g, bl, a8);
        }
    }

    private static void strip(VertexConsumer vc, PoseStack.Pose pose, Vec3 p, Vec3 q, float w, int r, int g, int b, int a) {
        Vec3 dir = q.subtract(p);
        Vec3 mid = p.add(q).scale(0.5);                    // vị trí so với camera = hướng nhìn
        Vec3 n = dir.cross(mid);
        if (n.lengthSqr() < 1.0E-10) return;
        n = n.normalize().scale(w);
        double[][] v = {
                {p.x + n.x, p.y + n.y, p.z + n.z}, {q.x + n.x, q.y + n.y, q.z + n.z},
                {q.x - n.x, q.y - n.y, q.z - n.z}, {p.x - n.x, p.y - n.y, p.z - n.z}};
        quad2(vc, pose, v, r, g, b, a);
    }
}