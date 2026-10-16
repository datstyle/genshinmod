package com.dat.genshinmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;

public final class HaloBurstFx {
    private HaloBurstFx() {}

    /** Tick nắm đấm chạm đất (khớp FracturedHaloItem.BURST_DELAY). */
    public static final float SLAM = 8f;
    /** Tick nắm đấm biến mất = BURST_DELAY (8) + BURST_PARALYZE_TICKS (50). Đổi số này để ở lâu/ngắn hơn. */
    public static final float FIST_END = SLAM + 20f;
    private static final float FADE_TICKS = 10f;

    /** Phóng to toàn bộ vật thể. */
    public static final float FIST_SCALE = 1.1f;
    /** Nửa rộng / nửa cao / nửa dày của thân màn hình (block, khi scale = 1). */
    private static final float HW = 1.5f, HH = 1.2f, HD = 0.45f;
    /** Bán kính và chiều dài trụ bên hông. */
    private static final float POD_R = 0.5f, POD_LEN = 0.55f;
    /** Vệt điện kéo từ vũ khí lúc bắn ra. */
    private static final boolean TRAIL = true;

    private static final float TAU = (float) (Math.PI * 2.0);
    private static final int[] PINK = {255, 140, 240};
    private static final int[] VIOLET = {150, 80, 255};
    private static final int[] WHITE = {255, 240, 255};
    private static final int[] GOLD = {255, 208, 130};
    private static final int[] AMBER = {255, 150, 70};

    private static final double[][] OUT = roundRect(1.0, 1.0, 0.13, 0.17, 5);
    private static final double[][] INNER = roundRect(0.86, 0.82, 0.10, 0.14, 5);

    /** Độ trong suốt tổng của vật thể (1 = đủ sáng, 0 = biến mất). */
    public static float fistAlpha(float age) {
        if (age >= FIST_END) return 0f;
        if (age < FIST_END - FADE_TICKS) return 1f;
        return (FIST_END - age) / FADE_TICKS;
    }

    // ================================================================== khung toạ độ của vật thể

    /** ux = trục ngang, vy = trục dọc, fw = hướng ra xa người dùng. w=-1 là mặt trước (hướng về người dùng). */
    private static final class Fr {
        final double cx, cy, cz, hw, hh, hd;
        final double[] ux, vy, fw;
        final float s;

        Fr(double[] c, double[] ux, double[] vy, double[] fw, double hw, double hh, double hd, float s) {
            this.cx = c[0]; this.cy = c[1]; this.cz = c[2];
            this.ux = ux; this.vy = vy; this.fw = fw;
            this.hw = hw; this.hh = hh; this.hd = hd; this.s = s;
        }

        /** Điểm theo toạ độ chuẩn hoá u,v ∈ [-1,1] và độ sâu w ∈ [-1,1]. */
        double[] at(double u, double v, double w) { return abs(u * hw, v * hh, w * hd); }

        /** Dịch điểm p đi a dọc ux và b dọc vy (dùng cho các ký hiệu nhỏ nằm trong mặt phẳng màn hình). */
        double[] abs0(double[] p, double a, double b) {
            return new double[] {
                p[0] + ux[0] * a + vy[0] * b,
                p[1] + ux[1] * a + vy[1] * b,
                p[2] + ux[2] * a + vy[2] * b};
        }

        /** Điểm theo khoảng cách tuyệt đối (block) dọc ux, vy, fw. */
        double[] abs(double a, double b, double c) {
            return new double[] {
                cx + ux[0] * a + vy[0] * b + fw[0] * c,
                cy + ux[1] * a + vy[1] * b + fw[1] * c,
                cz + ux[2] * a + vy[2] * b + fw[2] * c};
        }
    }

    // ================================================================== render

    /**
     * @param ix,iy,iz  điểm chạm đất (đã trừ camera)
     * @param yawDeg    yaw người dùng lúc cast (tag "BurstYaw") — màn hình quay mặt về phía họ
     * @param px,py,pz  vị trí CHÂN người dùng (đã trừ camera) — nơi nắm đấm bắn ra
     */
    public static void render(VertexConsumer vc, PoseStack.Pose pose, int id, long burstTick, float age,
                              double ix, double iy, double iz, float yawDeg, double px, double py, double pz) {
        if (age < 0f || age >= FIST_END) return;
        float alpha = fistAlpha(age);
        if (alpha <= 0.01f) return;

        float s = fistScale(age);
        double yaw = Math.toRadians(yawDeg);
        double[] fw = {-Math.sin(yaw), 0.0, Math.cos(yaw)};
        double[] ax = {Math.cos(yaw), 0.0, Math.sin(yaw)};
        float t = clamp01(age / SLAM);
        double rho = Math.toRadians((1f - t) * (1f - t) * 540f); // xoay quanh trục bắn, về 0 khi chạm
        double cr = Math.cos(rho), sr = Math.sin(rho);
        double[] ux = {ax[0] * cr, sr, ax[2] * cr};
        double[] vy = {-ax[0] * sr, cr, -ax[2] * sr};
        double[] c = fistCenter(age, ix, iy, iz, yawDeg, px, py, pz);
        Fr f = new Fr(c, ux, vy, fw, HW * s, HH * s, HD * s, s);

        boolean landed = age >= SLAM;
        float hAge = age - SLAM;
        float flick = age >= SLAM + 8f ? 0.82f + 0.18f * hash((int) (age * 2f), id) : 1f;
        float vis = alpha * flick;

        body(vc, pose, f, vis, age);
        screen(vc, pose, f, vis, age, landed ? hAge : 99f);
        pods(vc, pose, f, vis, age);
        chase(vc, pose, f, vis, age);
        glitch(vc, pose, f, vis, age, id);
        if (landed) {
            crtFlash(vc, pose, f, hAge, vis);
            lightning(vc, pose, f, id, burstTick, hAge, vis, iy);
            ground(vc, pose, f, hAge, vis, iy, id);
            dataPixels(vc, pose, f, age, vis, iy);
        }
        if (TRAIL && age < SLAM + 1f) trail(vc, pose, id, burstTick, age, ix, iy, iz, yawDeg, px, py, pz);
    }

    // ================================================================== chuyển động bắn ra

    private static double[] handPos(double px, double py, double pz, float yawDeg) {
        double yaw = Math.toRadians(yawDeg);
        double fwx = -Math.sin(yaw), fwz = Math.cos(yaw);
        double ax = Math.cos(yaw), az = Math.sin(yaw);
        return new double[] {px + fwx * 0.6 - ax * 0.35, py + 1.2, pz + fwz * 0.6 - az * 0.35};
    }

    /** Tâm vật thể: bay từ tay tới điểm chạm, rồi đứng yên (đáy chạm đất). */
    private static double[] fistCenter(float age, double ix, double iy, double iz, float yawDeg,
                                       double px, double py, double pz) {
        float t = clamp01(age / SLAM);
        float e = t * t * (3f - 2f * t); // smoothstep; đổi thành t*t nếu muốn lao nhanh hơn ở cuối
        double[] hand = handPos(px, py, pz, yawDeg);
        double ty = iy + HH * FIST_SCALE + 0.03;
        return new double[] {hand[0] + (ix - hand[0]) * e, hand[1] + (ty - hand[1]) * e, hand[2] + (iz - hand[2]) * e};
    }

    private static float fistScale(float age) {
        float t = clamp01(age / SLAM);
        float s = 0.15f + 0.85f * easeOut(clamp01(t / 0.7f));
        float pop = (age >= SLAM && age < SLAM + 3f) ? 1f + 0.06f * (float) Math.sin(Math.PI * (age - SLAM) / 3.0) : 1f;
        return FIST_SCALE * s * pop;
    }

    // ================================================================== thân màn hình

    private static void body(VertexConsumer vc, PoseStack.Pose pose, Fr f, float vis, float age) {
        // viền trước: quầng hồng + lõi đào sáng; viền sau: mờ hơn
        loop(vc, pose, f, OUT, -1.0, 0.11f, PINK, 0.38f * vis);
        loop(vc, pose, f, OUT, -1.0, 0.045f, mix(GOLD, WHITE, 0.55f), 0.98f * vis);
        loop(vc, pose, f, OUT, 1.0, 0.05f, VIOLET, 0.6f * vis);
        loop(vc, pose, f, INNER, -1.02, 0.02f, WHITE, 0.6f * vis);
        // 4 thanh nối mặt trước – mặt sau ở các góc
        for (int c = 0; c < 4; c++) {
            double[] p = OUT[c * 6 + 3];
            strip(vc, pose, f.at(p[0], p[1], -1.0), f.at(p[0], p[1], 1.0), 0.04f * f.s, VIOLET[0], VIOLET[1], VIOLET[2], c255(0.65f * vis));
            strip(vc, pose, f.at(p[0], p[1], -1.0), f.at(p[0], p[1], 1.0), 0.012f * f.s, WHITE[0], WHITE[1], WHITE[2], c255(0.6f * vis));
        }
        // thanh nẹp góc vàng + ốc vít
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                double[] k = f.at(sx * 0.97, sy * 0.95, -1.03);
                double[] kx = f.at(sx * 0.67, sy * 0.95, -1.03);
                double[] ky = f.at(sx * 0.97, sy * 0.60, -1.03);
                strip(vc, pose, k, kx, 0.08f * f.s, AMBER[0], AMBER[1], AMBER[2], c255(0.4f * vis));
                strip(vc, pose, k, ky, 0.08f * f.s, AMBER[0], AMBER[1], AMBER[2], c255(0.4f * vis));
                strip(vc, pose, k, kx, 0.03f * f.s, GOLD[0], GOLD[1], GOLD[2], c255(vis));
                strip(vc, pose, k, ky, 0.03f * f.s, GOLD[0], GOLD[1], GOLD[2], c255(vis));
                double[] sc = f.at(sx * 0.80, sy * 0.76, -1.03);
                float g = 0.05f * f.s;
                strip(vc, pose, f.abs0(sc, -g, 0), f.abs0(sc, g, 0), 0.012f * f.s, GOLD[0], GOLD[1], GOLD[2], c255(0.9f * vis));
                strip(vc, pose, f.abs0(sc, 0, -g), f.abs0(sc, 0, g), 0.012f * f.s, GOLD[0], GOLD[1], GOLD[2], c255(0.9f * vis));
            }
        }
    }

    /** Vệt sáng trắng chạy quanh viền ngoài. */
    private static void chase(VertexConsumer vc, PoseStack.Pose pose, Fr f, float vis, float age) {
        int n = OUT.length;
        for (int k = 0; k < 2; k++) {
            float pos = (age * 0.8f + k * n / 2f) % n;
            int i0 = (int) pos;
            for (int j = 0; j < 3; j++) {
                double[] a = OUT[(i0 + j) % n], b = OUT[(i0 + j + 1) % n];
                float fall = 1f - j / 3f;
                strip(vc, pose, f.at(a[0], a[1], -1.04), f.at(b[0], b[1], -1.04), 0.2f * f.s, PINK[0], PINK[1], PINK[2], c255(0.3f * fall * vis));
                strip(vc, pose, f.at(a[0], a[1], -1.04), f.at(b[0], b[1], -1.04), 0.07f * f.s, WHITE[0], WHITE[1], WHITE[2], c255(fall * vis));
            }
        }
    }

    // ================================================================== màn hình

    private static void screen(VertexConsumer vc, PoseStack.Pose pose, Fr f, float vis, float age, float boot) {
        float s = f.s;
        // scanline trôi lên
        float off = (age * 0.05f) % 0.2f;
        for (int k = 0; k < 10; k++) {
            double v = -0.85 + 0.2 * k + off;
            if (v > 0.88) continue;
            strip(vc, pose, f.at(-0.84, v, -1.01), f.at(0.84, v, -1.01), 0.006f * s, 255, 200, 255, c255(0.12f * vis));
        }
        // vạch quét CRT mỗi 26 tick
        float sw = (age % 26f) / 12f;
        if (sw < 1f) {
            double v = 0.9 - 1.8 * sw;
            strip(vc, pose, f.at(-0.86, v, -1.01), f.at(0.86, v, -1.01), 0.05f * s, PINK[0], PINK[1], PINK[2], c255(0.25f * vis));
            strip(vc, pose, f.at(-0.86, v, -1.01), f.at(0.86, v, -1.01), 0.012f * s, WHITE[0], WHITE[1], WHITE[2], c255(0.7f * vis));
        }
        // thanh ngang trên
        if (boot > 0.5f) rect(vc, pose, f, -0.62, 0.62, 0.38, 0.45, -1.02, PINK, WHITE, vis, 0.55f);
        // 4 ô ký hiệu: sáng lần lượt khi khởi động, sau đó nhấp nháy lệch pha
        for (int k = 0; k < 4; k++) {
            if (boot < k * 1.2f) continue;
            double u0 = -0.62 + 0.31 * k + 0.03, u1 = -0.62 + 0.31 * (k + 1) - 0.03;
            float pulse = boot < 99f && boot < 6f ? 1f : 0.55f + 0.45f * (float) Math.sin(age * 0.35f - k * 0.9f);
            rect(vc, pose, f, u0, u1, -0.30, 0.28, -1.02, PINK, WHITE, vis * (0.6f + 0.4f * pulse));
            for (int r = 0; r < 5; r++) {
                double v = -0.22 + 0.1 * r;
                strip(vc, pose, f.at(u0 + 0.03, v, -1.02), f.at(u1 - 0.03, v, -1.02), 0.012f * s, PINK[0], PINK[1], PINK[2], c255(0.4f * pulse * vis));
            }
        }
        // 3 đèn trạng thái ở góc trên trái
        int[][] cols = {AMBER, PINK, WHITE};
        for (int d = 0; d < 3; d++) {
            double[] p = f.at(-0.82 + 0.1 * d, 0.7, -1.02);
            float blink = 0.5f + 0.5f * (float) Math.sin(age * 0.5f + d * 2.1f);
            float g = 0.035f * s;
            quad2(vc, pose, new double[][] {f.abs0(p, -g, -g), f.abs0(p, g, -g), f.abs0(p, g, g), f.abs0(p, -g, g)},
                  cols[d][0], cols[d][1], cols[d][2], c255(0.9f * blink * vis));
        }
        // sao 4 cánh phía trên
        float tw = 0.6f + 0.4f * (float) Math.sin(age * 0.9f);
        double[] st = f.at(0.0, 1.28, -1.0);
        float L = 0.4f * s * tw;
        strip(vc, pose, f.abs0(st, 0, -L), f.abs0(st, 0, L), 0.03f * s, WHITE[0], WHITE[1], WHITE[2], c255(vis * tw));
        strip(vc, pose, f.abs0(st, -L * 0.7f, 0), f.abs0(st, L * 0.7f, 0), 0.03f * s, WHITE[0], WHITE[1], WHITE[2], c255(vis * tw));
    }

    // ================================================================== 2 trụ bên hông

    private static void pods(VertexConsumer vc, PoseStack.Pose pose, Fr f, float vis, float age) {
        float s = f.s;
        double x0 = f.hw + 0.06 * s, x1 = f.hw + POD_LEN * s, r = POD_R * s;
        for (int side = -1; side <= 1; side += 2) {
            // thân trụ: 10 mặt, sáng ở phía trên, tối ở phía dưới -> nhìn ra khối trụ
            for (int k = 0; k < 10; k++) {
                float a0 = TAU * k / 10f, a1 = TAU * (k + 1) / 10f;
                float lit = 0.5f + 0.5f * (float) Math.cos((a0 + a1) * 0.5f);
                int[] fc = mix(VIOLET, PINK, lit);
                quad2(vc, pose, new double[][] {pc(f, side, x0, a0, r), pc(f, side, x1, a0, r), pc(f, side, x1, a1, r), pc(f, side, x0, a1, r)},
                      fc[0], fc[1], fc[2], c255((0.12f + 0.2f * lit) * vis));
            }
            for (int k = 0; k < 10; k += 2) { // 5 gân dọc
                float th = TAU * k / 10f;
                strip(vc, pose, pc(f, side, x0, th, r), pc(f, side, x1, th, r), 0.02f * s, WHITE[0], WHITE[1], WHITE[2], c255(0.45f * vis));
            }
            // 2 vòng đai đồng
            for (int b = 1; b <= 2; b++) {
                double xb = x0 + (x1 - x0) * (b == 1 ? 0.35 : 0.68);
                circle(vc, pose, f, side, xb, r * 1.03, 0.07f, AMBER, 0.22f * vis, 22);
                circle(vc, pose, f, side, xb, r * 1.03, 0.03f, GOLD, 0.95f * vis, 22);
            }
            // nắp ngoài: 3 vòng đồng tâm
            circle(vc, pose, f, side, x1, r, 0.08f, PINK, 0.25f * vis, 24);
            circle(vc, pose, f, side, x1, r, 0.035f, mix(GOLD, WHITE, 0.5f), 0.95f * vis, 24);
            circle(vc, pose, f, side, x1, r * 0.72, 0.022f, VIOLET, 0.8f * vis, 20);
            circle(vc, pose, f, side, x1, r * 0.42, 0.022f, PINK, 0.8f * vis, 16);
            // 6 nan quay + tâm sáng
            for (int k = 0; k < 6; k++) {
                float th = age * 0.18f * side + TAU * k / 6f;
                strip(vc, pose, pc(f, side, x1 + 0.005, th, r * 0.2), pc(f, side, x1 + 0.005, th, r * 0.72), 0.022f * s, WHITE[0], WHITE[1], WHITE[2], c255(0.55f * vis));
            }
            for (int k = 0; k < 10; k++) {
                float a0 = TAU * k / 10f, a1 = TAU * (k + 1) / 10f;
                double[] ctr = pc(f, side, x1 + 0.01, 0f, 0.0);
                quad2(vc, pose, new double[][] {ctr, pc(f, side, x1 + 0.01, a0, r * 0.2), pc(f, side, x1 + 0.01, a1, r * 0.2), ctr},
                      WHITE[0], WHITE[1], WHITE[2], c255(0.7f * vis));
            }
            // 3 cung sáng quét quanh nắp
            for (int k = 0; k < 3; k++) {
                float st = age * 0.25f * side + TAU * k / 3f;
                double[] prev = null;
                for (int j = 0; j <= 8; j++) {
                    double[] p = pc(f, side, x1 + 0.012, st + side * 0.9f * j / 8f, r * 0.9);
                    if (prev != null) {
                        float fa = (float) j / 8f;
                        strip(vc, pose, prev, p, 0.08f * s, PINK[0], PINK[1], PINK[2], c255(0.2f * fa * vis));
                        strip(vc, pose, prev, p, 0.028f * s, WHITE[0], WHITE[1], WHITE[2], c255(0.9f * fa * vis));
                    }
                    prev = p;
                }
            }
            // 2 tia lửa vàng quay quanh trụ
            for (int k = 0; k < 2; k++) {
                float th = age * 0.3f * side + k * (float) Math.PI;
                double[] sp = pc(f, side, x1 + 0.1 * s, th, r * 1.2);
                float g = 0.07f * s;
                strip(vc, pose, f.abs0(sp, -g, 0), f.abs0(sp, g, 0), 0.02f * s, GOLD[0], GOLD[1], GOLD[2], c255(vis));
                strip(vc, pose, f.abs0(sp, 0, -g), f.abs0(sp, 0, g), 0.02f * s, GOLD[0], GOLD[1], GOLD[2], c255(vis));
            }
        }
    }

    /** Điểm trên mặt trụ: cách tâm màn hình `x` dọc trục ngang (theo `side`), góc th, bán kính rr (trong mặt phẳng vy–fw). */
    private static double[] pc(Fr f, int side, double x, double th, double rr) {
        return f.abs(side * x, Math.cos(th) * rr, Math.sin(th) * rr);
    }

    private static void circle(VertexConsumer vc, PoseStack.Pose pose, Fr f, int side, double x, double rr,
                               float width, int[] col, float alpha, int segs) {
        int al = c255(alpha);
        for (int i = 0; i < segs; i++) {
            double a0 = TAU * i / segs, a1 = TAU * (i + 1) / segs;
            strip(vc, pose, pc(f, side, x, a0, rr), pc(f, side, x, a1, rr), width * f.s, col[0], col[1], col[2], al);
        }
    }

    // ================================================================== pixel glitch quanh viền

    private static void glitch(VertexConsumer vc, PoseStack.Pose pose, Fr f, float vis, float age, int id) {
        int tick = (int) (age / 2f);
        for (int i = 0; i < 12; i++) {
            if (hash(i * 7 + tick, id) < 0.45f) continue;
            boolean horiz = hash(i, 20) < 0.5f;
            double sgn = hash(i, 21) < 0.5f ? -1 : 1;
            double u = horiz ? sgn * (1.06 + hash(i, 22) * 0.2) : (hash(i, 23) * 2 - 1) * 1.0;
            double v = horiz ? (hash(i, 24) * 2 - 1) * 1.0 : sgn * (1.06 + hash(i, 25) * 0.2);
            double[] p = f.at(u, v, -1.0);
            float g = 0.05f * f.s * (0.8f + hash(i, 26));
            int[] col = hash(i, 27) < 0.5f ? GOLD : WHITE;
            quad2(vc, pose, new double[][] {f.abs0(p, -g, -g), f.abs0(p, g, -g), f.abs0(p, g, g), f.abs0(p, -g, g)},
                  col[0], col[1], col[2], c255(0.85f * vis));
        }
    }

    // ================================================================== sau khi chạm đất

    private static void crtFlash(VertexConsumer vc, PoseStack.Pose pose, Fr f, float hAge, float vis) {
        if (hAge >= 4f) return;
        float k = 1f - hAge / 4f;
        double[][] q = {f.at(-1, -1, -1.05), f.at(1, -1, -1.05), f.at(1, 1, -1.05), f.at(-1, 1, -1.05)};
        quad2(vc, pose, q, 255, 225, 255, c255(0.5f * k * vis));
        // vòng xung kích mảnh trên đất
        float t = clamp01(hAge / 6f);
        if (t < 1f) groundRing(vc, pose, f, 0.3 * f.s + t * 3.6 * f.s, 0.06f * f.s, PINK, (1f - t) * vis);
    }

    private static void lightning(VertexConsumer vc, PoseStack.Pose pose, Fr f, int id, long burstTick,
                                  float hAge, float vis, double iy) {
        float grow = easeOut(clamp01(hAge / 3f));
        long seed = (long) (hAge / 2f);
        double x1 = f.hw + POD_LEN * f.s, r = POD_R * f.s;
        for (int side = -1; side <= 1; side += 2) {
            // 2 tia sét từ nắp trụ ra mặt đất
            for (int k = 0; k < 2; k++) {
                Random rnd = new Random(id * 131L + burstTick * 13L + side * 977L + k * 31L + seed * 7919L);
                double[] a = f.abs(side * x1, 0, 0);
                double len = (2.0 + rnd.nextDouble() * 2.5) * grow;
                double[] b = {a[0] + f.ux[0] * side * len + f.fw[0] * (rnd.nextDouble() - 0.5) * 2.4,
                              iy + 0.1 + rnd.nextDouble() * 0.3,
                              a[2] + f.ux[2] * side * len + f.fw[2] * (rnd.nextDouble() - 0.5) * 2.4};
                bolt(vc, pose, a, b, 0.035f, vis * (0.65f + 0.35f * rnd.nextFloat()), rnd, 8, 0.5, VIOLET, WHITE);
            }
            // cung bốc chéo lên từ góc trên
            Random r2 = new Random(id * 71L + burstTick * 7L + side * 313L + seed * 101L);
            double[] top = f.at(side * 0.92, 0.95, -1.0);
            double[] end = {top[0] + f.ux[0] * side * 1.6 * grow, top[1] + 1.1 * grow, top[2] + f.ux[2] * side * 1.6 * grow};
            bolt(vc, pose, top, end, 0.03f, vis * 0.85f, r2, 6, 0.45, PINK, WHITE);
            // phóng điện giữa trụ và khung
            for (int k = 0; k < 2; k++) {
                Random r3 = new Random(id * 17L + burstTick * 3L + side * 211L + k * 53L + seed * 61L);
                double[] a = pc(f, side, x1, r3.nextDouble() * TAU, r);
                double[] b = f.at(side * 1.0, (r3.nextDouble() - 0.5) * 1.2, -1.0);
                bolt(vc, pose, a, b, 0.022f, vis * 0.9f, r3, 4, 0.15, PINK, WHITE);
            }
        }
    }

    private static void ground(VertexConsumer vc, PoseStack.Pose pose, Fr f, float hAge, float vis, double iy, int id) {
        float s = f.s;
        double y = iy + 0.06;
        // vạch sáng ngang chân màn hình
        double len = (f.hw + POD_LEN * s + 1.0) * easeOut(clamp01(hAge / 3f));
        double[] a = {f.cx - f.ux[0] * len, y, f.cz - f.ux[2] * len};
        double[] b = {f.cx + f.ux[0] * len, y, f.cz + f.ux[2] * len};
        float flick = 0.8f + 0.2f * (float) Math.sin(hAge * 2f);
        strip(vc, pose, a, b, 0.14f, VIOLET[0], VIOLET[1], VIOLET[2], c255(0.35f * vis));
        strip(vc, pose, a, b, 0.04f, WHITE[0], WHITE[1], WHITE[2], c255(0.9f * vis * flick));
        // khung chân bệ
        double ha = f.hw + 0.1 * s, hb = f.hd + 0.25 * s;
        double[][] c = {gp(f, -ha, -hb, y), gp(f, ha, -hb, y), gp(f, ha, hb, y), gp(f, -ha, hb, y)};
        for (int i = 0; i < 4; i++) {
            strip(vc, pose, c[i], c[(i + 1) % 4], 0.03f, PINK[0], PINK[1], PINK[2], c255(0.75f * vis));
        }
    }

    private static void groundRing(VertexConsumer vc, PoseStack.Pose pose, Fr f, double radius, float width,
                                   int[] col, float alpha) {
        int al = c255(alpha);
        if (al <= 0) return;
        double y = f.cy - f.hh + 0.04;
        for (int i = 0; i < 40; i++) {
            double a0 = TAU * i / 40.0, a1 = TAU * (i + 1) / 40.0;
            double[] p = {f.cx + Math.cos(a0) * radius, y, f.cz + Math.sin(a0) * radius};
            double[] q = {f.cx + Math.cos(a1) * radius, y, f.cz + Math.sin(a1) * radius};
            strip(vc, pose, p, q, width, col[0], col[1], col[2], al);
        }
    }

    /** Điểm trên mặt đất theo toạ độ (a dọc ux, b dọc fw) từ tâm màn hình; y cho trước. */
    private static double[] gp(Fr f, double a, double b, double y) {
        return new double[] {f.cx + f.ux[0] * a + f.fw[0] * b, y, f.cz + f.ux[2] * a + f.fw[2] * b};
    }

    /** Mảnh pixel cam-đào bay lên từ bệ. */
    private static void dataPixels(VertexConsumer vc, PoseStack.Pose pose, Fr f, float age, float vis, double iy) {
        for (int i = 0; i < 14; i++) {
            double a = (hash(i, 31) * 2 - 1) * f.hw * 0.9;
            double b = (hash(i, 32) * 2 - 1) * f.hd * 1.2;
            float phase = (age * 0.03f + hash(i, 33)) % 1f;
            double[] g = gp(f, a, b, iy + 0.08 + phase * f.hh * 1.8);
            float s = 0.045f * f.s * (1.2f - phase);
            int[] col = mix(AMBER, WHITE, hash(i, 34));
            float al = (float) Math.sin(phase * Math.PI) * 0.85f * vis;
            quad2(vc, pose, new double[][] {{g[0] - s, g[1], g[2] - s}, {g[0] + s, g[1], g[2] - s}, {g[0] + s, g[1], g[2] + s}, {g[0] - s, g[1], g[2] + s}},
                  col[0], col[1], col[2], c255(al));
            quad2(vc, pose, new double[][] {{g[0] - s, g[1] - s, g[2]}, {g[0] + s, g[1] - s, g[2]}, {g[0] + s, g[1] + s, g[2]}, {g[0] - s, g[1] + s, g[2]}},
                  col[0], col[1], col[2], c255(al));
            quad2(vc, pose, new double[][] {{g[0], g[1] - s, g[2] - s}, {g[0], g[1] - s, g[2] + s}, {g[0], g[1] + s, g[2] + s}, {g[0], g[1] + s, g[2] - s}},
                  col[0], col[1], col[2], c255(al));
        }
    }

    // ================================================================== vệt bắn ra từ vũ khí

    private static void trail(VertexConsumer vc, PoseStack.Pose pose, int id, long burstTick, float age,
                              double ix, double iy, double iz, float yawDeg, double px, double py, double pz) {
        float t = clamp01(age / SLAM);
        float vis = 1f - 0.3f * t;
        double[] hand = handPos(px, py, pz, yawDeg);
        double[] c = fistCenter(age, ix, iy, iz, yawDeg, px, py, pz);
        float s = fistScale(age);
        strip(vc, pose, hand, c, 0.16f * s, VIOLET[0], VIOLET[1], VIOLET[2], c255(0.4f * vis));
        strip(vc, pose, hand, c, 0.045f * s, WHITE[0], WHITE[1], WHITE[2], c255(0.9f * vis));
        long seed = (long) age;
        for (int k = 0; k < 3; k++) {
            Random rnd = new Random(id * 17L + burstTick * 3L + seed * 131L + k * 977L);
            double u0 = rnd.nextDouble() * 0.5, u1 = 0.5 + rnd.nextDouble() * 0.5;
            double[] a = {hand[0] + (c[0] - hand[0]) * u0, hand[1] + (c[1] - hand[1]) * u0, hand[2] + (c[2] - hand[2]) * u0};
            double[] b = {hand[0] + (c[0] - hand[0]) * u1 + (rnd.nextDouble() - 0.5) * 0.6,
                          hand[1] + (c[1] - hand[1]) * u1 + (rnd.nextDouble() - 0.5) * 0.6,
                          hand[2] + (c[2] - hand[2]) * u1 + (rnd.nextDouble() - 0.5) * 0.6};
            bolt(vc, pose, a, b, 0.03f, 0.85f * vis, rnd, 5, 0.35, PINK, WHITE);
        }
        if (age < 3f) { // chớp ở nòng
            float al = 1f - age / 3f;
            for (int k = 0; k < 6; k++) {
                Random rnd = new Random(id * 31L + burstTick + k * 7L);
                double[] e = {hand[0] + (rnd.nextDouble() - 0.5) * 0.9, hand[1] + (rnd.nextDouble() - 0.5) * 0.9,
                              hand[2] + (rnd.nextDouble() - 0.5) * 0.9};
                strip(vc, pose, hand, e, 0.04f, AMBER[0], AMBER[1], AMBER[2], c255(0.9f * al));
            }
        }
    }

    // ================================================================== hình học dùng chung

    /** Hình chữ nhật bo góc (toạ độ chuẩn hoá), ngược chiều kim đồng hồ; mỗi góc seg+1 điểm. */
    private static double[][] roundRect(double hu, double hv, double cu, double cv, int seg) {
        double[][] pts = new double[4 * (seg + 1)][];
        double[][] centers = {{hu - cu, hv - cv}, {-(hu - cu), hv - cv}, {-(hu - cu), -(hv - cv)}, {hu - cu, -(hv - cv)}};
        int n = 0;
        for (int c = 0; c < 4; c++) {
            for (int s = 0; s <= seg; s++) {
                double th = (c + (double) s / seg) * Math.PI / 2.0;
                pts[n++] = new double[] {centers[c][0] + cu * Math.cos(th), centers[c][1] + cv * Math.sin(th)};
            }
        }
        return pts;
    }

    /** Viền khép kín 2 lớp (quầng + lõi) qua các điểm (u,v) ở độ sâu w. width tính theo block khi scale = 1. */
    private static void loop(VertexConsumer vc, PoseStack.Pose pose, Fr f, double[][] pts, double w, float width,
                             int[] col, float alpha) {
        int al = c255(alpha);
        for (int i = 0; i < pts.length; i++) {
            double[] a = pts[i], b = pts[(i + 1) % pts.length];
            strip(vc, pose, f.at(a[0], a[1], w), f.at(b[0], b[1], w), width * f.s, col[0], col[1], col[2], al);
        }
    }

    /** Khung chữ nhật 2 lớp (quầng + lõi) trong toạ độ chuẩn hoá. */
    private static void rect(VertexConsumer vc, PoseStack.Pose pose, Fr f, double u0, double u1, double v0, double v1,
                             double w, int[] glow, int[] core, float vis) {
        rect(vc, pose, f, u0, u1, v0, v1, w, glow, core, vis, 1f);
    }

    private static void rect(VertexConsumer vc, PoseStack.Pose pose, Fr f, double u0, double u1, double v0, double v1,
                             double w, int[] glow, int[] core, float vis, float thin) {
        double[][] p = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (int i = 0; i < 4; i++) {
            double[] a = p[i], b = p[(i + 1) % 4];
            strip(vc, pose, f.at(a[0], a[1], w), f.at(b[0], b[1], w), 0.08f * thin * f.s, glow[0], glow[1], glow[2], c255(0.3f * vis));
            strip(vc, pose, f.at(a[0], a[1], w), f.at(b[0], b[1], w), 0.028f * thin * f.s, core[0], core[1], core[2], c255(0.95f * vis));
        }
    }

    // ================================================================== helper vẽ

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

    /** Tia sét gấp khúc 2 lớp: quầng màu + lõi. */
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
            int a8 = c255(al * (pass == 0 ? 0.55f : 1f));
            for (int i = 0; i < segs; i++) strip(vc, pose, pts[i], pts[i + 1], w, col[0], col[1], col[2], a8);
        }
    }

    private static float hash(int i, int k) {
        int n = i * 374761393 + k * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= n >>> 16;
        return (n & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float easeOut(float x) { float t = 1f - x; return 1f - t * t * t; }

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