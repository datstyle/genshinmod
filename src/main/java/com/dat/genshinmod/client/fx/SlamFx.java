package com.dat.genshinmod.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Hiệu ứng chiêu dậm bản phù phép, vẽ bằng HÌNH HỌC THẬT (không dùng hạt):
 * vòng xung, vết nứt phát sáng, cột sét, mũi pha lê, tia sét, cung trăng khuyết vàng.
 * Chạy hoàn toàn ở máy người chơi, mượt theo từng khung hình.
 */
public class SlamFx {
    public static final byte RISE = 0;
    public static final byte LAND = 1;

    // ===== Thông số khớp với server (ElectroPulseManager) =====
    private static final double[][] RINGS = {   // { tick bắt đầu, số tick lan ra, bán kính }
            {2, 10, 7.0}, {16, 10, 6.0}, {38, 10, 5.0}
    };
    private static final double ENCHANT_RADIUS = 1.3;
    private static final float LAND_TICKS = 62F;
    private static final float RISE_TICKS = 14F;

    // ===== Màu (R, G, B từ 0 đến 1) =====
    private static final float[] ICE = {0.80F, 0.90F, 1.00F};
    private static final float[] BLUE = {0.35F, 0.50F, 1.00F};
    private static final float[] VIOLET = {0.40F, 0.30F, 0.95F};
    private static final float[] GOLD = {1.00F, 0.78F, 0.28F};
    private static final float[] GOLD_HOT = {1.00F, 0.93F, 0.65F};

    private static final List<Fx> FX = new ArrayList<>();

    private static class Fx {
        byte kind;
        int entityId;
        Vec3 pos;
        boolean enchanted;
        long start;
        List<List<Vec3>> cracks = new ArrayList<>();      // vết nứt (toạ độ tương đối so với tâm)
        List<List<Vec3>> bolts0 = new ArrayList<>();      // tia sét đợt 1
        List<List<Vec3>> bolts1 = new ArrayList<>();      // tia sét đợt 2
        double[][] spikes;                                 // {góc, bán kính chân, chiều cao, độ nghiêng}
    }

    /** Server báo dựng hiệu ứng. */
    public static void add(byte kind, int entityId, double x, double y, double z, long seed, boolean enchanted) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Fx f = new Fx();
        f.kind = kind;
        f.entityId = entityId;
        f.pos = new Vec3(x, y, z);
        f.enchanted = enchanted;
        f.start = mc.level.getGameTime();
        if (kind == LAND) build(f, new Random(seed));
        FX.add(f);
    }

    // Sinh hình dạng ngẫu nhiên cố định theo seed
    private static void build(Fx f, Random r) {
        // 7 vết nứt tỏa ra từ tâm, có nhánh phụ
        int n = 7;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + (r.nextDouble() - 0.5) * 0.5;
            double len = 5.0 + r.nextDouble() * 3.0;
            List<Vec3> main = new ArrayList<>();
            main.add(new Vec3(0, 0.04, 0));
            double x = 0, z = 0;
            for (double d = 0.8; d < len; d += 0.7) {
                a += (r.nextDouble() - 0.5) * 0.5;
                x += Math.cos(a) * 0.7;
                z += Math.sin(a) * 0.7;
                main.add(new Vec3(x, 0.04, z));
                if (r.nextDouble() < 0.14) {
                    List<Vec3> branch = new ArrayList<>();
                    branch.add(new Vec3(x, 0.04, z));
                    double bx = x, bz = z, ba = a + (r.nextBoolean() ? 0.8 : -0.8);
                    for (int k = 0; k < 3; k++) {
                        ba += (r.nextDouble() - 0.5) * 0.5;
                        bx += Math.cos(ba) * 0.6;
                        bz += Math.sin(ba) * 0.6;
                        branch.add(new Vec3(bx, 0.04, bz));
                    }
                    f.cracks.add(branch);
                }
            }
            f.cracks.add(main);
        }

        // 2 đợt tia sét bắn chéo lên từ tâm
        for (int wave = 0; wave < 2; wave++) {
            for (int i = 0; i < 8; i++) {
                double ang = r.nextDouble() * Math.PI * 2;
                double pitch = 0.5 + r.nextDouble() * 0.9;
                Vec3 d = new Vec3(Math.cos(ang) * Math.cos(pitch), Math.sin(pitch), Math.sin(ang) * Math.cos(pitch));
                double len = 6.0 + r.nextDouble() * 5.0;
                int steps = 9;
                List<Vec3> pts = new ArrayList<>();
                for (int s = 0; s <= steps; s++) {
                    Vec3 p = new Vec3(0, 0.3, 0).add(d.scale(len * s / steps));
                    if (s > 0 && s < steps) {
                        p = p.add((r.nextDouble() - 0.5) * 0.7, (r.nextDouble() - 0.5) * 0.7, (r.nextDouble() - 0.5) * 0.7);
                    }
                    pts.add(p);
                }
                (wave == 0 ? f.bolts0 : f.bolts1).add(pts);
            }
        }

        // 6 mũi pha lê mọc quanh tâm
        f.spikes = new double[6][4];
        for (int i = 0; i < 6; i++) {
            f.spikes[i][0] = Math.PI * 2 * i / 6 + r.nextDouble() * 0.5;
            f.spikes[i][1] = 0.4 + r.nextDouble() * 0.9;
            f.spikes[i][2] = 3.5 + r.nextDouble() * 2.5;
            f.spikes[i][3] = 0.25 + r.nextDouble() * 0.3;
        }
    }

    // ================= VẼ =================
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || FX.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        PoseStack pose = event.getPoseStack();
        Matrix4f m = pose.last().pose();
        Vec3 cam = event.getCamera().getPosition();
        float partial = event.getPartialTick();
        float time = level.getGameTime() + partial;

        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());

        Iterator<Fx> it = FX.iterator();
        while (it.hasNext()) {
            Fx f = it.next();
            float age = time - f.start;
            if (age > (f.kind == LAND ? LAND_TICKS : RISE_TICKS)) { it.remove(); continue; }
            if (age < 0) continue;
            if (f.kind == LAND) renderLand(f, age, vc, m, cam);
            else renderRise(f, age, level, partial, vc, m, cam);
        }
        buf.endBatch(RenderType.lightning());
    }

    private static void renderLand(Fx f, float age, VertexConsumer vc, Matrix4f m, Vec3 cam) {
        Vec3 c = f.pos;

        // 1. Quầng sáng tím xanh trên mặt đất
        if (age >= 1 && age <= 34) {
            double radius = 6.5 * ENCHANT_RADIUS / 1.3 * Math.min(1.0, age / 6.0);
            float a = (float) (0.30 * (1.0 - age / 34.0));
            disc(vc, m, cam, c, c.y + 0.05, radius, VIOLET, a);
        }

        // 2. Ba vòng xung lan ra, khớp ba nhịp nổ
        for (double[] ring : RINGS) {
            float local = age - (float) ring[0];
            float len = (float) ring[1];
            if (local < 0 || local > len + 14) continue;
            double prog = Math.min(1.0, local / len);
            double radius = ring[2] * ENCHANT_RADIUS * Math.sin(prog * Math.PI / 2);
            float fade = local <= len ? 1F : 1F - (local - len) / 14F;
            double halfW = 0.2 + 0.3 * (1 - prog);
            ringFlat(vc, m, cam, c, c.y + 0.07, radius, halfW * 3.0, BLUE, 0.35F * fade);
            ringFlat(vc, m, cam, c, c.y + 0.08, radius, halfW, ICE, 0.95F * fade);
        }

        // 3. Vết nứt phát sáng, lan dần rồi mờ
        if (age >= 1 && age <= 52) {
            float reveal = Math.min(1F, (age - 1F) / 9F);
            float fade = age < 30 ? 1F : Math.max(0F, 1F - (age - 30F) / 22F);
            for (List<Vec3> line : f.cracks) {
                int n = (int) Math.ceil(line.size() * reveal);
                if (n < 2) continue;
                List<Vec3> pts = new ArrayList<>();
                for (int i = 0; i < n; i++) pts.add(c.add(line.get(i)));
                ribbon(vc, m, cam, pts, 0.38, BLUE, 0.30F * fade, 0.10F * fade, true);
                ribbon(vc, m, cam, pts, 0.11, ICE, 0.95F * fade, 0.45F * fade, true);
            }
        }

        // 4. Cột sét thẳng đứng
        if (age >= 2 && age <= 16) {
            double t = (age - 2) / 14.0;
            float a = (float) (0.95 * Math.pow(1 - t, 1.2));
            double hw = 0.25 + 0.55 * (1 - t);
            beam(vc, m, cam, c, hw, 14.0, ICE, a);
            beam(vc, m, cam, c, hw * 2.4, 14.0, BLUE, a * 0.35F);
        }

        // 5. Mũi pha lê xanh mọc lên rồi thu lại
        if (age >= 2 && age <= 17) {
            double grow = Math.min(1.0, (age - 2) / 4.0);
            double shrink = 1.0 - Math.max(0.0, (age - 10) / 7.0);
            double scale = grow * shrink;
            for (double[] sp : f.spikes) {
                Vec3 base = c.add(Math.cos(sp[0]) * sp[1], 0.05, Math.sin(sp[0]) * sp[1]);
                double h = sp[2] * scale;
                Vec3 tip = base.add(Math.cos(sp[0]) * sp[3] * h, h, Math.sin(sp[0]) * sp[3] * h);
                spike(vc, m, cam, base, tip, 0.3 * scale + 0.02);
            }
        }

        // 6. Tia sét bắn chéo (hai đợt, nhấp nháy)
        bolts(vc, m, cam, c, f.bolts0, age - 2F);
        bolts(vc, m, cam, c, f.bolts1, age - 4F);

        // 7. Ba cung trăng khuyết vàng quét tròn rồi lan ra
        if (age >= 4 && age <= 19) {
            double rad = 1.0 + (age - 4) * 0.5;
            float fade = age < 15 ? 1F : Math.max(0F, 1F - (age - 15F) / 4F);
            for (int s = 0; s < 3; s++) {
                double base = age * 0.55 + s * 2.094;
                arc(vc, m, cam, c, rad, c.y + 0.8, base, 1.5, 0.30, GOLD, 0.85F * fade);
                arc(vc, m, cam, c, rad, c.y + 0.8, base, 1.5, 0.10, GOLD_HOT, 1.0F * fade);
            }
        }
    }

    private static void renderRise(Fx f, float age, ClientLevel level, float partial, VertexConsumer vc, Matrix4f m, Vec3 cam) {
        Entity e = level.getEntity(f.entityId);
        if (e == null) return;
        Vec3 p = e.getPosition(partial);

        if (age < 8.5F) {   // hai sợi điện xoắn quanh người khi bay lên
            for (int k = 0; k < 2; k++) {
                List<Vec3> pts = new ArrayList<>();
                for (int i = 0; i <= 16; i++) {
                    double h = 2.4 * i / 16.0;
                    double ang = age * 0.9 + h * 2.6 + k * Math.PI;
                    pts.add(new Vec3(p.x + Math.cos(ang) * 0.85, p.y + h + age * 0.3, p.z + Math.sin(ang) * 0.85));
                }
                ribbon(vc, m, cam, pts, 0.28, BLUE, 0.25F, 0.0F, false);
                ribbon(vc, m, cam, pts, 0.09, ICE, 0.95F, 0.2F, false);
            }
        } else {            // lơ lửng: hai cung vàng quét quanh người
            float local = age - 8F;
            float fade = age < 12 ? 1F : Math.max(0F, 1F - (age - 12F) / 2F);
            for (int arcIdx = 0; arcIdx < 2; arcIdx++) {
                double base = local * 1.4 + arcIdx * Math.PI;
                arc(vc, m, cam, p, 2.0, p.y + 1.0 + 0.3 * 3.0, base, 1.6, 0.28, GOLD, 0.85F * fade);
                arc(vc, m, cam, p, 2.0, p.y + 1.0 + 0.3 * 3.0, base, 1.6, 0.09, GOLD_HOT, fade);
            }
        }
    }

    // ================= HÌNH HỌC CƠ BẢN =================
    private record P(double x, double y, double z, float r, float g, float b, float a) {}

    private static P p(Vec3 v, float[] c, float a) {
        return new P(v.x, v.y, v.z, c[0], c[1], c[2], Math.max(0F, Math.min(1F, a)));
    }

    private static void emit(VertexConsumer vc, Matrix4f m, Vec3 cam, P q) {
        vc.vertex(m, (float) (q.x - cam.x), (float) (q.y - cam.y), (float) (q.z - cam.z))
                .color(q.r, q.g, q.b, q.a).endVertex();
    }

    /** Một tứ giác, vẽ cả hai mặt (nhìn từ phía nào cũng thấy). */
    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 cam, P a, P b, P c, P d) {
        emit(vc, m, cam, a); emit(vc, m, cam, b); emit(vc, m, cam, c); emit(vc, m, cam, d);
        emit(vc, m, cam, d); emit(vc, m, cam, c); emit(vc, m, cam, b); emit(vc, m, cam, a);
    }

    private static Vec3 at(Vec3 c, double ang, double radius, double y) {
        return new Vec3(c.x + Math.cos(ang) * radius, y, c.z + Math.sin(ang) * radius);
    }

    /** Vòng phẳng trên mặt đất, sáng ở giữa mờ dần hai mép. */
    private static void ringFlat(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 c, double y,
                                 double radius, double halfW, float[] col, float alpha) {
        if (radius < 0.2 || alpha <= 0F) return;
        int seg = (int) Math.max(28, radius * 10);
        double inner = Math.max(0, radius - halfW);
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            P in0 = p(at(c, a0, inner, y), col, 0F), in1 = p(at(c, a1, inner, y), col, 0F);
            P mid0 = p(at(c, a0, radius, y), col, alpha), mid1 = p(at(c, a1, radius, y), col, alpha);
            P out0 = p(at(c, a0, radius + halfW, y), col, 0F), out1 = p(at(c, a1, radius + halfW, y), col, 0F);
            quad(vc, m, cam, in0, mid0, mid1, in1);
            quad(vc, m, cam, mid0, out0, out1, mid1);
        }
    }

    /** Đĩa sáng ở tâm, mờ dần ra mép. */
    private static void disc(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 c, double y,
                             double radius, float[] col, float alpha) {
        if (radius < 0.3 || alpha <= 0F) return;
        int seg = 40;
        P center = p(new Vec3(c.x, y, c.z), col, alpha);
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            P e0 = p(at(c, a0, radius, y), col, 0F), e1 = p(at(c, a1, radius, y), col, 0F);
            quad(vc, m, cam, center, e0, e1, e1);
        }
    }

    /** Dải phát sáng đi theo một chuỗi điểm. flat = nằm sát mặt đất, ngược lại luôn quay mặt về camera. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 cam, List<Vec3> pts, double width,
                               float[] col, float a0, float a1, boolean flat) {
        int n = pts.size();
        if (n < 2) return;
        Vec3[] left = new Vec3[n], right = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 prev = pts.get(Math.max(0, i - 1)), next = pts.get(Math.min(n - 1, i + 1));
            Vec3 dir = next.subtract(prev);
            if (dir.lengthSqr() < 1.0E-8) dir = new Vec3(0, 1, 0);
            Vec3 ref = flat ? new Vec3(0, 1, 0) : cam.subtract(pts.get(i));
            Vec3 side = dir.cross(ref);
            if (side.lengthSqr() < 1.0E-8) side = new Vec3(1, 0, 0);
            side = side.normalize().scale(width / 2.0);
            left[i] = pts.get(i).add(side);
            right[i] = pts.get(i).subtract(side);
        }
        for (int i = 0; i < n - 1; i++) {
            float ta = a0 + (a1 - a0) * i / (n - 1), tb = a0 + (a1 - a0) * (i + 1) / (n - 1);
            quad(vc, m, cam, p(left[i], col, ta), p(right[i], col, ta), p(right[i + 1], col, tb), p(left[i + 1], col, tb));
        }
    }

    /** Cung phẳng nhọn hai đầu, đầu sáng đuôi mờ (vệt chém). */
    private static void arc(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 c, double radius, double y,
                            double a0, double len, double halfW, float[] col, float alpha) {
        if (alpha <= 0F) return;
        int seg = 14;
        for (int i = 0; i < seg; i++) {
            double u0 = (double) i / seg, u1 = (double) (i + 1) / seg;
            double w0 = halfW * Math.sin(Math.PI * u0), w1 = halfW * Math.sin(Math.PI * u1);
            float al0 = (float) (alpha * u0), al1 = (float) (alpha * u1);
            double g0 = a0 + len * u0, g1 = a0 + len * u1;
            quad(vc, m, cam,
                    p(at(c, g0, radius - w0, y), col, al0), p(at(c, g0, radius + w0, y), col, al0),
                    p(at(c, g1, radius + w1, y), col, al1), p(at(c, g1, radius - w1, y), col, al1));
        }
    }

    /** Cột sáng thẳng đứng: hai mặt phẳng cắt nhau, sáng ở giữa, mờ dần hai bên và về phía đỉnh. */
    private static void beam(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 c, double hw, double height,
                             float[] col, float alpha) {
        if (alpha <= 0F) return;
        double[] lat = {-hw, -hw / 3, hw / 3, hw};
        float[] la = {0F, 1F, 1F, 0F};
        for (int plane = 0; plane < 2; plane++) {
            double phi = plane * Math.PI / 2;
            double dx = Math.cos(phi), dz = Math.sin(phi);
            for (int i = 0; i < 3; i++) {
                Vec3 b0 = new Vec3(c.x + dx * lat[i], c.y, c.z + dz * lat[i]);
                Vec3 b1 = new Vec3(c.x + dx * lat[i + 1], c.y, c.z + dz * lat[i + 1]);
                Vec3 t0 = b0.add(0, height, 0), t1 = b1.add(0, height, 0);
                quad(vc, m, cam, p(b0, col, alpha * la[i]), p(b1, col, alpha * la[i + 1]),
                        p(t1, col, 0F), p(t0, col, 0F));
            }
        }
    }

    /** Mũi nhọn tam giác (kim tự tháp mảnh): chân xanh, đỉnh trắng sáng. */
    private static void spike(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 base, Vec3 tip, double r) {
        Vec3[] b = new Vec3[3];
        for (int i = 0; i < 3; i++) {
            double a = Math.PI * 2 * i / 3;
            b[i] = base.add(Math.cos(a) * r, 0, Math.sin(a) * r);
        }
        P pt = p(tip, ICE, 1.0F);
        for (int i = 0; i < 3; i++) {
            P p0 = p(b[i], BLUE, 0.60F), p1 = p(b[(i + 1) % 3], BLUE, 0.60F);
            quad(vc, m, cam, p0, p1, pt, pt);
        }
    }

    /** Tia sét: dải quay mặt về camera, nhấp nháy, sống khoảng 7 tick. */
    private static void bolts(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 c, List<List<Vec3>> bolts, float local) {
        if (local < 0 || local > 7) return;
        float fade = 1F - local / 7F;
        float flicker = 0.65F + 0.35F * (float) Math.sin(local * 4.7);
        float a = fade * flicker;
        for (List<Vec3> bolt : bolts) {
            List<Vec3> pts = new ArrayList<>();
            for (Vec3 v : bolt) pts.add(c.add(v));
            ribbon(vc, m, cam, pts, 0.45, BLUE, 0.35F * a, 0.0F, false);
            ribbon(vc, m, cam, pts, 0.11, ICE, 0.95F * a, 0.15F * a, false);
        }
    }
}