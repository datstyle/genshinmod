package com.dat.genshinmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;

/**
 * Khiên "Cầu Điện Cực Quang" của Hào Quang Tách Rời (trạng thái ENCHANT) — mô phỏng khiên Ineffa trong Genshin.
 *
 * Thành phần (bám theo ảnh tham khảo):
 *  - Vỏ cầu trong suốt, mép sáng, giữa gần như trong (hiệu ứng Fresnel: sáng theo góc nhìn)
 *  - Vòng sáng + hoa văn trên mặt đất (vòng ngoài, vòng trong, cung xoay, nan hoa)
 *  - Lưới kinh/vĩ tuyến mờ chạy trên vỏ, xoay chậm
 *  - Cung sét hồng-trắng chạy dọc mặt trong của vỏ
 *  - 2 drone nhỏ bay vòng quanh, có vệt đuôi và tia lửa cam
 *  - Mỗi xung (20 tick) vỏ chớp sáng + sét toả từ người ra vỏ + sóng lan trên đất
 *
 * Chỉ dùng VertexConsumer của RenderType.lightning() (POSITION_COLOR, cộng sáng) — chính là `glow`
 * trong HaloFx.onRenderLevel. Toạ độ truyền vào là TƯƠNG ĐỐI CAMERA, giống HaloFx.
 */
public final class HaloShieldFx {
    private HaloShieldFx() {}

    private static final int LAT = 16;
    private static final int LON = 28;
    private static final float TAU = (float) (Math.PI * 2.0);
    /** Cắt vỏ cầu dưới chân (so với y chân) để không sáng lên bên dưới mặt đất. */
    private static final double FLOOR_CUT = -0.3;

    private static final int[] VIOLET = {140, 70, 255};
    private static final int[] PINK = {255, 140, 240};
    private static final int[] WHITE = {255, 238, 255};
    private static final int[] SPARK = {255, 175, 60};

    /**
     * @param id          entity id người chơi (làm seed ngẫu nhiên, mỗi người một kiểu sét)
     * @param start       tick bắt đầu skill (tag "SkillStart") — để seed ổn định
     * @param age         số tick từ lúc cast (có partial tick)
     * @param left        số tick còn lại của skill (có partial tick)
     * @param fx,fy,fz    vị trí CHÂN người chơi, đã trừ toạ độ camera
     * @param firstPerson đang ở góc nhìn thứ nhất của chính mình (giảm độ sáng để không chói)
     * @param radius      bán kính khiên — truyền FracturedHaloItem.ARC_RADIUS để hình ảnh = vùng sát thương
     */
    public static void render(VertexConsumer vc, PoseStack.Pose pose, int id, long start,
                              float age, float left, double fx, double fy, double fz,
                              boolean firstPerson, float radius) {
        float in = clamp01(age / 5f);
        float out = clamp01(left / 12f);
        float vis = in * out;
        if (vis <= 0.01f) return;

        float ease = 1f - (1f - in) * (1f - in) * (1f - in);
        float scale = (0.55f + 0.45f * ease) * (0.92f + 0.08f * out);
        float rx = radius * scale;
        float ry = radius * 0.88f * scale;
        double cy = fy + 0.9;
        double floorY = fy + FLOOR_CUT;

        float pulseAge = age % 20f;
        float flash = pulseAge < 7f ? 1f - pulseAge / 7f : 0f;
        int pulseIdx = (int) (age / 20f);

        float shellMul = vis * (firstPerson ? 0.3f : 1f);
        float fxMul = vis * (firstPerson ? 0.6f : 1f);

        shell(vc, pose, fx, cy, fz, rx, ry, age, flash, shellMul, floorY);
        gridLines(vc, pose, fx, cy, fz, rx, ry, age, flash, fxMul, floorY);
        arcs(vc, pose, id, start, age, fx, cy, fz, rx, ry, fxMul, floorY);
        if (flash > 0f) pulseBolts(vc, pose, id, start, pulseIdx, age, fx, cy, fz, rx, ry, flash, fxMul);
        ground(vc, pose, fx, fy, fz, radius * 0.86f * scale, age, flash, pulseIdx, vis);
        drones(vc, pose, id, age, fx, fy, fz, rx, vis);
    }

    // ------------------------------------------------------------------ vỏ cầu (Fresnel)

    private static void shell(VertexConsumer vc, PoseStack.Pose pose, double cx, double cy, double cz,
                              float rx, float ry, float age, float flash, float mul, double floorY) {
        for (int i = 0; i < LAT; i++) {
            float t0 = (float) Math.PI * i / LAT;
            float t1 = (float) Math.PI * (i + 1) / LAT;
            for (int j = 0; j < LON; j++) {
                float p0 = TAU * j / LON;
                float p1 = TAU * (j + 1) / LON;
                double[] a = pt(cx, cy, cz, rx, ry, t0, p0);
                double[] b = pt(cx, cy, cz, rx, ry, t0, p1);
                double[] c = pt(cx, cy, cz, rx, ry, t1, p1);
                double[] d = pt(cx, cy, cz, rx, ry, t1, p0);
                double mx = (a[0] + b[0] + c[0] + d[0]) / 4.0;
                double my = (a[1] + b[1] + c[1] + d[1]) / 4.0;
                double mz = (a[2] + b[2] + c[2] + d[2]) / 4.0;
                if (my < floorY) continue;

                // pháp tuyến ellipsoid tại tâm ô, so với hướng nhìn (camera ở gốc toạ độ)
                double nx = (mx - cx) / ((double) rx * rx);
                double ny = (my - cy) / ((double) ry * ry);
                double nz = (mz - cz) / ((double) rx * rx);
                double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double vl = Math.sqrt(mx * mx + my * my + mz * mz);
                if (nl < 1e-9 || vl < 1e-9) continue;
                double facing = Math.abs(nx * -mx + ny * -my + nz * -mz) / (nl * vl);
                float rim = (float) Math.pow(1.0 - facing, 2.2);

                float shimmer = 0.75f + 0.25f * (float) Math.sin(age * 0.25f + i * 0.8f + j * 0.6f);
                float al = (0.035f + 0.6f * rim) * shimmer * (1f + 1.1f * flash) * mul;
                int[] col = mix(VIOLET, PINK, rim * 1.2f + flash * 0.5f);
                col = mix(col, WHITE, flash * 0.4f * rim);
                quad2(vc, pose, new double[][] {a, b, c, d}, col[0], col[1], col[2], c255(al));
            }
        }
    }

    // ------------------------------------------------------------------ lưới kinh/vĩ tuyến

    private static void gridLines(VertexConsumer vc, PoseStack.Pose pose, double cx, double cy, double cz,
                                  float rx, float ry, float age, float flash, float mul, double floorY) {
        int al = c255(0.2f * (1f + flash) * mul);
        if (al <= 0) return;
        float rot = age * 0.012f;
        final int steps = 24;
        for (int m = 0; m < 6; m++) {
            float ph = rot + TAU * m / 6f;
            double[] prev = null;
            for (int s = 0; s <= steps; s++) {
                double[] p = pt(cx, cy, cz, rx * 1.002f, ry * 1.002f, (float) Math.PI * s / steps, ph);
                if (prev != null && prev[1] >= floorY && p[1] >= floorY) {
                    strip(vc, pose, prev, p, 0.012f, 200, 150, 255, al);
                }
                prev = p;
            }
        }
        for (int l = 1; l <= 3; l++) {
            float t = (float) Math.PI * l / 4f;
            double[] prev = null;
            for (int s = 0; s <= LON; s++) {
                double[] p = pt(cx, cy, cz, rx * 1.002f, ry * 1.002f, t, TAU * s / LON + rot);
                if (prev != null && prev[1] >= floorY && p[1] >= floorY) {
                    strip(vc, pose, prev, p, 0.012f, 200, 150, 255, al);
                }
                prev = p;
            }
        }
    }

    // ------------------------------------------------------------------ cung sét chạy dọc vỏ

    private static void arcs(VertexConsumer vc, PoseStack.Pose pose, int id, long start, float age,
                             double cx, double cy, double cz, float rx, float ry, float mul, double floorY) {
        final int n = 12;
        final float len = 1.3f; // ~75 độ
        long tickSeed = (long) (age / 2f);
        for (int k = 0; k < 6; k++) {
            float sign = (k % 2 == 0) ? 1f : -1f;
            float a = 0.5f + k * 0.42f;
            float b = k * 1.9f + sign * age * 0.011f;
            double nx = Math.sin(a) * Math.cos(b);
            double ny = Math.cos(a);
            double nz = Math.sin(a) * Math.sin(b);
            // u = n × up, v = n × u
            double ux = -nz, uy = 0.0, uz = nx;
            double ul = Math.sqrt(ux * ux + uz * uz);
            if (ul < 1e-4) { ux = 1.0; uz = 0.0; } else { ux /= ul; uz /= ul; }
            double vx = ny * uz;
            double vy = nz * ux - nx * uz;
            double vz = -ny * ux;

            float head = sign * age * (0.09f + 0.015f * k) + k * 1.3f;
            Random rnd = new Random(id * 31L + k * 7919L + tickSeed * 131L + start);
            double[][] pts = new double[n + 1][];
            for (int m = 0; m <= n; m++) {
                float th = head - sign * len * m / n;
                double c = Math.cos(th), s = Math.sin(th);
                double dx = c * ux + s * vx;
                double dy = c * uy + s * vy;
                double dz = c * uz + s * vz;
                double j = (m == 0 || m == n) ? 0.0 : 0.035;
                pts[m] = new double[] {
                    cx + rx * 0.97 * dx + (rnd.nextDouble() - 0.5) * j,
                    cy + ry * 0.97 * dy + (rnd.nextDouble() - 0.5) * j,
                    cz + rx * 0.97 * dz + (rnd.nextDouble() - 0.5) * j};
            }
            for (int m = 1; m <= n; m++) {
                if (pts[m - 1][1] < floorY || pts[m][1] < floorY) continue;
                float f = (float) Math.pow(1.0 - (m - 1) / (double) n, 1.3);
                strip(vc, pose, pts[m - 1], pts[m], 0.045f, VIOLET[0], VIOLET[1], VIOLET[2], c255(0.3f * f * mul));
                strip(vc, pose, pts[m - 1], pts[m], 0.012f, WHITE[0], WHITE[1], WHITE[2], c255(0.95f * f * mul));
            }
        }
    }

    private static void pulseBolts(VertexConsumer vc, PoseStack.Pose pose, int id, long start, int pulseIdx,
                                   float age, double cx, double cy, double cz, float rx, float ry,
                                   float flash, float mul) {
        long tickSeed = (long) (age / 2f);
        for (int k = 0; k < 7; k++) {
            Random rnd = new Random(id * 977L + start * 13L + pulseIdx * 31L + tickSeed * 131L + k);
            float ang = TAU * k / 7f + pulseIdx * 0.7f + (float) rnd.nextDouble() * 0.4f;
            double y = (rnd.nextDouble() - 0.5) * 1.2;
            double h = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double[] a = {cx, cy + 0.1, cz};
            double[] b = {cx + Math.cos(ang) * h * rx * 0.98, cy + y * ry * 0.98, cz + Math.sin(ang) * h * rx * 0.98};
            if (b[1] < cy - ry * 0.5) b[1] = cy - ry * 0.5;
            bolt(vc, pose, a, b, 0.045f, flash * mul, rnd, 6, 0.35, PINK, WHITE);
        }
    }

    // ------------------------------------------------------------------ mặt đất

    private static void ground(VertexConsumer vc, PoseStack.Pose pose, double fx, double fy, double fz,
                               float gr, float age, float flash, int pulseIdx, float vis) {
        if (gr < 0.2f) return;
        double y = fy + 0.03;
        ring(vc, pose, fx, y, fz, 0f, gr, 36, VIOLET, 0.07f * vis);
        ring(vc, pose, fx, y, fz, gr - 0.28f, gr + 0.03f, 48, VIOLET, 0.28f * vis);
        int[] core = mix(PINK, WHITE, 0.4f + flash * 0.6f);
        ring(vc, pose, fx, y + 0.005, fz, gr - 0.07f, gr, 48, core, 0.95f * vis);
        float ir = gr * 0.62f;
        ring(vc, pose, fx, y + 0.003, fz, ir - 0.02f, ir + 0.02f, 40, PINK, 0.4f * vis);

        for (int k = 0; k < 3; k++) {
            float st = age * 0.04f + k * TAU / 3f;
            ringArc(vc, pose, fx, y + 0.004, fz, gr * 0.72f, gr * 0.86f, st, 0.9f, 10, VIOLET, 0.5f * vis);
        }
        int spokeAl = c255(0.22f * vis);
        float rot = -age * 0.015f;
        for (int k = 0; k < 8; k++) {
            float ang = rot + TAU * k / 8f;
            double[] p = {fx + Math.cos(ang) * ir, y + 0.002, fz + Math.sin(ang) * ir};
            double[] q = {fx + Math.cos(ang) * (gr - 0.07f), y + 0.002, fz + Math.sin(ang) * (gr - 0.07f)};
            strip(vc, pose, p, q, 0.012f, 220, 170, 255, spokeAl);
        }
        if (age < 8f) { // vòng "bung" lúc vừa cast
            float t = age / 8f;
            float r1 = gr * (0.3f + 0.9f * t);
            ring(vc, pose, fx, y + 0.006, fz, Math.max(0f, r1 - 0.25f), r1, 40, WHITE, (1f - t) * 0.8f * vis);
        }
        if (flash > 0f) { // sóng lan theo mỗi xung
            float r1 = gr * (1f - flash) + 0.1f;
            ring(vc, pose, fx, y + 0.007, fz, Math.max(0f, r1 - 0.2f), r1, 40, PINK, flash * 0.6f * vis);
        }
    }

    // ------------------------------------------------------------------ drone bay quanh

    private static void drones(VertexConsumer vc, PoseStack.Pose pose, int id, float age,
                               double fx, double fy, double fz, float rx, float vis) {
        for (int k = 0; k < 2; k++) {
            float ang = age * 0.07f + k * (float) Math.PI;
            double[] pos = dronePos(fx, fy, fz, rx, ang, age, k);

            double[] prev = null;
            for (int s = 0; s <= 8; s++) {
                double[] p = dronePos(fx, fy, fz, rx, ang - s * 0.05f, age, k);
                if (prev != null) {
                    strip(vc, pose, prev, p, 0.03f, PINK[0], PINK[1], PINK[2], c255((1f - s / 8f) * 0.6f * vis));
                }
                prev = p;
            }
            cube(vc, pose, pos[0], pos[1], pos[2], 0.14f, 0.14f, 0.14f, VIOLET, 0.28f * vis);
            cube(vc, pose, pos[0], pos[1], pos[2], 0.07f, 0.07f, 0.11f, WHITE, 0.95f * vis);

            Random rnd = new Random(id * 53L + k * 17L + (long) (age / 2f) * 7L);
            for (int s = 0; s < 2; s++) {
                if (rnd.nextFloat() < 0.4f) continue;
                double[] end = {pos[0] + (rnd.nextDouble() - 0.5) * 0.7,
                                pos[1] + (rnd.nextDouble() - 0.5) * 0.7,
                                pos[2] + (rnd.nextDouble() - 0.5) * 0.7};
                bolt(vc, pose, pos, end, 0.015f, 0.9f * vis, rnd, 3, 0.08, SPARK, WHITE);
            }
        }
    }

    private static double[] dronePos(double fx, double fy, double fz, float rx, float ang, float age, int k) {
        double bob = 0.12 * Math.sin(age * 0.12 + k * 2.0);
        return new double[] {fx + rx * 0.94 * Math.cos(ang), fy + 1.15 + bob, fz + rx * 0.94 * Math.sin(ang)};
    }

    // ------------------------------------------------------------------ helper vẽ

    private static double[] pt(double cx, double cy, double cz, float rx, float ry, float t, float p) {
        return new double[] {
            cx + rx * Math.sin(t) * Math.cos(p),
            cy + ry * Math.cos(t),
            cz + rx * Math.sin(t) * Math.sin(p)};
    }

    private static void cv(VertexConsumer vc, PoseStack.Pose pose, double x, double y, double z,
                           int r, int g, int b, int a) {
        vc.vertex(pose.pose(), (float) x, (float) y, (float) z)
          .color(clamp255(r), clamp255(g), clamp255(b), clamp255(a))
          .endVertex();
    }

    /** Vẽ 2 mặt (lightning RenderType có cull) nên đẩy đỉnh xuôi rồi ngược. */
    private static void quad2(VertexConsumer vc, PoseStack.Pose pose, double[][] v, int r, int g, int b, int a) {
        if (a <= 0) return;
        for (int i = 0; i < 4; i++) cv(vc, pose, v[i][0], v[i][1], v[i][2], r, g, b, a);
        for (int i = 3; i >= 0; i--) cv(vc, pose, v[i][0], v[i][1], v[i][2], r, g, b, a);
    }

    private static void ring(VertexConsumer vc, PoseStack.Pose pose, double cx, double cy, double cz,
                             float r0, float r1, int segs, int[] col, float alpha) {
        ringArc(vc, pose, cx, cy, cz, r0, r1, 0f, TAU, segs, col, alpha);
    }

    private static void ringArc(VertexConsumer vc, PoseStack.Pose pose, double cx, double cy, double cz,
                                float r0, float r1, float startAng, float sweep, int segs, int[] col, float alpha) {
        int al = c255(alpha);
        if (al <= 0) return;
        for (int i = 0; i < segs; i++) {
            float a0 = startAng + sweep * i / segs;
            float a1 = startAng + sweep * (i + 1) / segs;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            double[][] v = {
                {cx + r0 * c0, cy, cz + r0 * s0},
                {cx + r1 * c0, cy, cz + r1 * s0},
                {cx + r1 * c1, cy, cz + r1 * s1},
                {cx + r0 * c1, cy, cz + r0 * s1}};
            quad2(vc, pose, v, col[0], col[1], col[2], al);
        }
    }

    /** Dải mảnh luôn quay mặt về camera (pháp tuyến = hướng đoạn × vị trí tương đối camera). */
    private static void strip(VertexConsumer vc, PoseStack.Pose pose, double[] p, double[] q, float w,
                              int r, int g, int b, int a) {
        if (a <= 0) return;
        double dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2];
        double mx = (p[0] + q[0]) * 0.5, my = (p[1] + q[1]) * 0.5, mz = (p[2] + q[2]) * 0.5;
        double nx = dy * mz - dz * my;
        double ny = dz * mx - dx * mz;
        double nz = dx * my - dy * mx;
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-5) return;
        double k = w / nl;
        nx *= k; ny *= k; nz *= k;
        double[][] v = {
            {p[0] + nx, p[1] + ny, p[2] + nz},
            {q[0] + nx, q[1] + ny, q[2] + nz},
            {q[0] - nx, q[1] - ny, q[2] - nz},
            {p[0] - nx, p[1] - ny, p[2] - nz}};
        quad2(vc, pose, v, r, g, b, a);
    }

    /** Tia sét gấp khúc có màu tuỳ chỉnh (2 lớp: quầng + lõi). */
    private static void bolt(VertexConsumer vc, PoseStack.Pose pose, double[] a, double[] b, float width,
                             float alpha, Random rnd, int segs, double jitter, int[] glow, int[] core) {
        double[][] pts = new double[segs + 1][];
        pts[0] = a;
        pts[segs] = b;
        for (int i = 1; i < segs; i++) {
            double t = (double) i / segs;
            pts[i] = new double[] {
                a[0] + (b[0] - a[0]) * t + (rnd.nextDouble() - 0.5) * jitter,
                a[1] + (b[1] - a[1]) * t + (rnd.nextDouble() - 0.5) * jitter,
                a[2] + (b[2] - a[2]) * t + (rnd.nextDouble() - 0.5) * jitter};
        }
        float al = clamp01(alpha);
        for (int pass = 0; pass < 2; pass++) {
            float w = pass == 0 ? width * 3.2f : width;
            int[] col = pass == 0 ? glow : core;
            int a8 = c255(al * (pass == 0 ? 0.35f : 1f));
            for (int i = 0; i < segs; i++) strip(vc, pose, pts[i], pts[i + 1], w, col[0], col[1], col[2], a8);
        }
    }

    private static void cube(VertexConsumer vc, PoseStack.Pose pose, double cx, double cy, double cz,
                             float hx, float hy, float hz, int[] col, float alpha) {
        int al = c255(alpha);
        if (al <= 0) return;
        double x0 = cx - hx, x1 = cx + hx, y0 = cy - hy, y1 = cy + hy, z0 = cz - hz, z1 = cz + hz;
        double[][][] faces = {
            {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}},
            {{x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}, {x0, y0, z0}},
            {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
            {{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}},
            {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}},
            {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}};
        for (double[][] f : faces) quad2(vc, pose, f, col[0], col[1], col[2], al);
    }

    private static int[] mix(int[] a, int[] b, float t) {
        t = clamp01(t);
        return new int[] {
            (int) (a[0] + (b[0] - a[0]) * t),
            (int) (a[1] + (b[1] - a[1]) * t),
            (int) (a[2] + (b[2] - a[2]) * t)};
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    private static int clamp255(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }
    private static int c255(float v) { return (int) (clamp01(v) * 255f); }
}