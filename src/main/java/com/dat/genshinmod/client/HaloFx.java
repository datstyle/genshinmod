package com.dat.genshinmod.client;

import com.dat.genshinmod.effect.ModEffects;
import com.dat.genshinmod.genshinmod;
import com.dat.genshinmod.item.FracturedHaloItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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

/**
 * Hiệu ứng của Hào Quang Tách Rời, vẽ bằng hình học ở client (KHÔNG dùng hạt):
 *  - Khiên cầu xanh quanh người + tia điện bắn ra mỗi giây.
 *  - Tia điện nhấp nháy quanh mục tiêu đang Tê Liệt.
 *  - Nắm đấm robot giáng xuống + vòng sóng + sét.
 * Không cần packet: client đọc NBT của vũ khí đang cầm (đã đồng bộ sẵn).
 */
@Mod.EventBusSubscriber(modid = genshinmod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HaloFx {

    private static final ResourceLocation FIST_TEX = new ResourceLocation(genshinmod.MODID, "textures/fx/halo_fist.png");

    // vùng UV trong halo_fist.png (64x64)
    private static final float[] CASING = uv(2, 2, 30, 30);
    private static final float[] SCREEN = uv(34, 2, 62, 30);
    private static final float[] TREAD = uv(2, 34, 30, 62);
    private static final float[] BRONZE = uv(34, 34, 62, 62);

    private static final int FIST_TOTAL = 40;        // tổng thời gian nắm đấm tồn tại (tick)
    private static final int PULSE_FLASH = 7;        // tia điện hiện bao nhiêu tick sau mỗi lần bắn
    private static final double MAX_DIST_SQR = 64.0 * 64.0;

    private HaloFx() {}

    private static float[] uv(int u0, int v0, int u1, int v1) {
        return new float[]{u0 / 64F, v0 / 64F, u1 / 64F, v1 / 64F};
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        long now = level.getGameTime();
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType glowType = RenderType.lightning();
        RenderType fistType = RenderType.entityTranslucentEmissive(FIST_TEX);

        // ================= Người chơi cầm Hào Quang Tách Rời =================
        for (AbstractClientPlayer p : level.players()) {
            ItemStack held = FracturedHaloItem.held(p);
            if (held.isEmpty() || !held.hasTag()) continue;
            if (p.distanceToSqr(cam) > MAX_DIST_SQR) continue;
            CompoundTag tag = held.getTag();

            double px = Mth.lerp(pt, p.xo, p.getX()) - cam.x;
            double py = Mth.lerp(pt, p.yo, p.getY()) - cam.y;
            double pz = Mth.lerp(pt, p.zo, p.getZ()) - cam.z;

            long burstTick = tag.contains("BurstTick") ? tag.getLong("BurstTick") : Long.MIN_VALUE / 2;
            float burstAge = (now - burstTick) + pt;
            boolean skillOn = tag.getLong("SkillUntil") > now;
            boolean fistOn = burstAge >= 0F && burstAge < FIST_TOTAL;
            if (!skillOn && !fistOn) continue;

            // ---------- Phần 1: vẽ phát sáng (khiên, tia điện, vòng sóng, sét) ----------
            VertexConsumer glow = buffers.getBuffer(glowType);
            pose.pushPose();
            PoseStack.Pose last = pose.last();

            if (skillOn) {
                long start = tag.getLong("SkillStart");
                float age = (now - start) + pt;
                float left = tag.getLong("SkillUntil") - now - pt;
                boolean firstPersonSelf = p == mc.player && mc.options.getCameraType().isFirstPerson();
                drawShield(glow, last, px, py + 0.9, pz, age, left, firstPersonSelf);
                drawPulseArcs(glow, last, p.getId(), start, age, px, py + 1.0, pz);
            }
            if (fistOn) {
                double ix = tag.getDouble("BurstX") - cam.x;
                double iy = tag.getDouble("BurstY") - cam.y;
                double iz = tag.getDouble("BurstZ") - cam.z;
                drawImpactEffects(glow, last, p.getId(), burstTick, burstAge, ix, iy, iz);
            }
            pose.popPose();

            // ---------- Phần 2: nắm đấm robot (có texture) ----------
            if (fistOn) {
                VertexConsumer fv = buffers.getBuffer(fistType);
                double ix = tag.getDouble("BurstX") - cam.x;
                double iy = tag.getDouble("BurstY") - cam.y;
                double iz = tag.getDouble("BurstZ") - cam.z;
                float slam = FracturedHaloItem.BURST_DELAY;
                float h;
                if (burstAge < slam) {
                    float t = burstAge / slam;
                    h = 7.0F * (1F - t * t);                                   // rơi nhanh dần
                } else if (burstAge < slam + 3F) {
                    h = 0.35F * Mth.sin((burstAge - slam) / 3F * Mth.PI);      // nảy nhẹ khi chạm đất
                } else {
                    h = 0F;
                }
                float alpha = burstAge < 30F ? 1F : Math.max(0F, 1F - (burstAge - 30F) / (FIST_TOTAL - 30F));
                pose.pushPose();
                pose.translate(ix, iy + h, iz);
                pose.mulPose(Axis.YP.rotationDegrees(-tag.getFloat("BurstYaw")));
                drawFist(fv, pose.last(), alpha);
                pose.popPose();
            }
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
        buffers.endBatch(fistType);
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

    // ============================== Nắm đấm ==============================

    private static void drawImpactEffects(VertexConsumer vc, PoseStack.Pose pose, int id, long burstTick, float age,
                                          double ix, double iy, double iz) {
        float slam = FracturedHaloItem.BURST_DELAY;
        if (age < slam) return;
        float since = age - slam;
        float R = (float) FracturedHaloItem.BURST_RADIUS;

        // vòng sóng lan ra mặt đất
        float t = Mth.clamp(since / 10F, 0F, 1F);
        if (t < 1F) {
            float r1 = 0.5F + t * R;
            float width = 0.7F * (1F - t) + 0.2F;
            int al = (int) ((1F - t) * 255);
            ring(vc, pose, ix, iy + 0.06, iz, Math.max(0F, r1 - width), r1, 28, 190, 150, 255, al);
            ring(vc, pose, ix, iy + 0.07, iz, Math.max(0F, r1 - width * 0.4F), r1, 28, 245, 235, 255, al);
        }

        // sét giáng xuống trong vài tick đầu
        if (since < 6F) {
            float fade = 1F - since / 6F;
            Random rnd = new Random(burstTick * 13L + id * 31L + ((int) (since / 2F)) * 7L);
            bolt(vc, pose, new Vec3(ix, iy + 14, iz), new Vec3(ix, iy, iz), 0.2F, fade, rnd, 9, 0.9);
            for (int k = 0; k < 5; k++) {
                double ang = rnd.nextDouble() * Math.PI * 2, dist = 1.0 + rnd.nextDouble() * (R - 1.5);
                double x = ix + Math.cos(ang) * dist, z = iz + Math.sin(ang) * dist;
                bolt(vc, pose, new Vec3(x, iy + 12, z), new Vec3(x, iy, z), 0.1F, fade, rnd, 8, 0.7);
            }
        }
    }

    private static void drawFist(VertexConsumer vc, PoseStack.Pose pose, float a) {
        // thân hộp (mặt dưới là màn hình tím)
        box(vc, pose, 0F, 0.8F, 0F, 1.1F, 0.8F, 1.1F, CASING, CASING, SCREEN, a);
        // viền màn hình phát sáng: hộp mỏng hơi nhô ra phía dưới
        box(vc, pose, 0F, 0.05F, 0F, 1.18F, 0.06F, 1.18F, CASING, CASING, SCREEN, a);
        // bánh xe cổ tay (trục theo phương ngang)
        wheel(vc, pose, 0F, 2.45F, 0F, 0.85F, 0.75F, 14, TREAD, BRONZE, a);
        // cần nối lên trên
        box(vc, pose, 0F, 3.8F, 0F, 0.25F, 0.55F, 0.25F, CASING, BRONZE, CASING, a);
    }

    private static void box(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float cz,
                            float hx, float hy, float hz, float[] side, float[] top, float[] bottom, float a) {
        float x0 = cx - hx, x1 = cx + hx, y0 = cy - hy, y1 = cy + hy, z0 = cz - hz, z1 = cz + hz;
        face(vc, pose, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, side, 0.85F, a);   // +X
        face(vc, pose, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, side, 0.85F, a);   // -X
        face(vc, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, side, 0.70F, a);   // +Z
        face(vc, pose, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, side, 0.70F, a);   // -Z
        face(vc, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, top, 1.00F, a);    // +Y
        face(vc, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, bottom, 1.00F, a); // -Y
    }

    private static void wheel(VertexConsumer vc, PoseStack.Pose pose, float cx, float cy, float cz,
                              float radius, float halfLen, int segs, float[] tread, float[] cap, float a) {
        for (int i = 0; i < segs; i++) {
            float a0 = Mth.TWO_PI * i / segs, a1 = Mth.TWO_PI * (i + 1) / segs;
            float y0 = cy + radius * Mth.cos(a0), z0 = cz + radius * Mth.sin(a0);
            float y1 = cy + radius * Mth.cos(a1), z1 = cz + radius * Mth.sin(a1);
            float shade = 0.6F + 0.4F * Math.max(0F, Mth.cos((a0 + a1) * 0.5F));
            face(vc, pose, cx - halfLen, y0, z0, cx + halfLen, y0, z0, cx + halfLen, y1, z1, cx - halfLen, y1, z1, tread, shade, a);
            face(vc, pose, cx + halfLen, cy, cz, cx + halfLen, y0, z0, cx + halfLen, y1, z1, cx + halfLen, y1, z1, cap, 0.9F, a);
            face(vc, pose, cx - halfLen, cy, cz, cx - halfLen, y1, z1, cx - halfLen, y0, z0, cx - halfLen, y0, z0, cap, 0.8F, a);
        }
    }

    private static void face(VertexConsumer vc, PoseStack.Pose pose,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float[] uv, float shade, float a) {
        tv(vc, pose, ax, ay, az, uv[0], uv[3], shade, a);
        tv(vc, pose, bx, by, bz, uv[2], uv[3], shade, a);
        tv(vc, pose, cx, cy, cz, uv[2], uv[1], shade, a);
        tv(vc, pose, dx, dy, dz, uv[0], uv[1], shade, a);
    }

    private static void tv(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                           float u, float v, float shade, float a) {
        vc.vertex(pose.pose(), x, y, z)
                .color(shade, shade, shade, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), 0F, 1F, 0F)
                .endVertex();
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

    private static void ring(VertexConsumer vc, PoseStack.Pose pose, double cx, double cy, double cz,
                             float r0, float r1, int segs, int r, int g, int b, int a) {
        for (int i = 0; i < segs; i++) {
            float a0 = Mth.TWO_PI * i / segs, a1 = Mth.TWO_PI * (i + 1) / segs;
            double[][] v = {
                    {cx + r0 * Mth.cos(a0), cy, cz + r0 * Mth.sin(a0)},
                    {cx + r1 * Mth.cos(a0), cy, cz + r1 * Mth.sin(a0)},
                    {cx + r1 * Mth.cos(a1), cy, cz + r1 * Mth.sin(a1)},
                    {cx + r0 * Mth.cos(a1), cy, cz + r0 * Mth.sin(a1)}};
            quad2(vc, pose, v, r, g, b, a);
        }
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
