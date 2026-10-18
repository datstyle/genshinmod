package com.dat.genshinmod.client.fx;

import com.dat.genshinmod.item.weapon.FracturedHaloItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Random;
public final class HaloBurstFx {
    private HaloBurstFx() {}

    /** Tick nắm đấm chạm đất (khớp FracturedHaloItem.BURST_DELAY). */
    public static final float SLAM = FracturedHaloItem.BURST_DELAY;
    /** Thời gian bay từ vũ khí tới điểm chạm (tick). */
    private static final float FLIGHT = 15f;
    /**
     * Tick BẮN RA = SLAM - FLIGHT. Với BURST_DELAY = 45 (2.25 s) thì bắn ra ở 1.5 s: khớp cú nhấn đầu của halo_burst.ogg (1.50 s),
     * còn chạm đất ở 2.25 s khớp cú nhấn cuối (2.22–2.27 s) và tiếng halo_impact.ogg vang lên đúng lúc đó.
     */
    public static final float CHARGE = Math.max(1f, SLAM - FLIGHT);
    /** Hướng nhìn hiện tại của người dùng (để quả cầu tụ lực bám theo tay khi họ xoay người). */
    private static float HY;
    /** Tick nắm đấm biến mất = BURST_DELAY (8) + BURST_PARALYZE_TICKS (50). Đổi số này để ở lâu/ngắn hơn. */
    public static final float FIST_END = SLAM + 20f; // duy trì 1 giây sau khi chạm đất
    private static final float FADE_TICKS = 6f;

    /** Phóng to toàn bộ vật thể. */
    public static final float FIST_SCALE = 1.1f;
    /** Kích thước ở trạng thái enchant. */
    public static final float FIST_SCALE_ENCH = 1.7f;

    /** Cờ/thời gian của lần render hiện tại (render chạy tuần tự trên 1 luồng nên dùng biến tĩnh cho gọn). */
    private static boolean E;
    private static float T;
    /** Nửa rộng / nửa cao / nửa dày của thân màn hình (block, khi scale = 1). */
    private static final float HW = 1.5f, HH = 1.2f, HD = 0.45f;
    /** Bán kính và chiều dài trụ bên hông. */
    private static final float POD_R = 0.5f, POD_LEN = 0.55f;
    /** Vệt điện kéo từ vũ khí lúc bắn ra. */
    private static final boolean TRAIL = true;

    private static final float TAU = (float) (Math.PI * 2.0);
    // Bảng màu bám theo ảnh tham khảo: tím điện + magenta + hồng; "WHITE" là lõi sáng ngả hồng (không phải trắng tinh)
    private static final int[] PINK = {255, 110, 230};
    private static final int[] VIOLET = {150, 70, 255};
    private static final int[] WHITE = {255, 215, 250};
    private static final int[] MAGENTA = {235, 70, 230};
    private static final int[] BLUE = {100, 130, 255};
    private static final int[] CYAN = {110, 220, 255};
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
     * @param handYawDeg hướng nhìn HIỆN TẠI của người dùng (p.getYRot()) — quả cầu tụ lực bám theo tay
     * @param enchanted true = hiệu ứng enchant (tag "BurstEnch" ghi lúc cast)
     */
    public static void render(VertexConsumer vc, PoseStack.Pose pose, int id, long burstTick, float age,
                              double ix, double iy, double iz, float yawDeg, double px, double py, double pz, float handYawDeg, boolean enchanted) {
        HY = handYawDeg;
        E = enchanted;
        T = age;
        if (age < 0f || age >= FIST_END) return;
        float alpha = fistAlpha(age);
        if (alpha <= 0.01f) return;

        float s = fistScale(age);
        double yaw = Math.toRadians(yawDeg);
        double[] fw = {-Math.sin(yaw), 0.0, Math.cos(yaw)};
        double[] ax = {Math.cos(yaw), 0.0, Math.sin(yaw)};
        double rho = Math.toRadians(spinDeg(age)); // xoay quanh trục bắn: tăng dần lúc tụ lực, về 0 khi chạm
        double cr = Math.cos(rho), sr = Math.sin(rho);
        double[] ux = {ax[0] * cr, sr, ax[2] * cr};
        double[] vy = {-ax[0] * sr, cr, -ax[2] * sr};
        double[] c = fistCenter(age, ix, iy, iz, yawDeg, px, py, pz);
        Fr f = new Fr(c, ux, vy, fw, HW * s, HH * s, HD * s, s);

        boolean landed = age >= SLAM;
        float hAge = age - SLAM;
        float flick = age >= SLAM + 8f ? 0.82f + 0.18f * hash((int) (age * 2f), id) : 1f;
        float vis = alpha * flick;

        if (E) enchBack(vc, pose, f, vis, age); // lớp ở phía sau vẽ trước
        body(vc, pose, f, vis, age);
        screen(vc, pose, f, vis, age, landed ? hAge : 99f);
        pods(vc, pose, f, vis, age);
        chase(vc, pose, f, vis, age);
        if (!E) glitch(vc, pose, f, vis, age, id);
        if (landed) {
            crtFlash(vc, pose, f, hAge, vis);
            lightning(vc, pose, f, id, burstTick, hAge, vis, iy);
            ground(vc, pose, f, hAge, vis, iy, id);
            if (!E) dataPixels(vc, pose, f, age, vis, iy);
            if (E) enchFront(vc, pose, f, hAge, vis, iy);
            mist(vc, pose, f, id, burstTick, hAge, vis, iy); // vẽ CUỐI CÙNG: lớp mờ không che các nét phía sau
        }
        if (TRAIL && age < SLAM + 1f) trail(vc, pose, id, burstTick, age, ix, iy, iz, yawDeg, px, py, pz);
        if (age < CHARGE) chargeFx(vc, pose, id, burstTick, age, f);
    }

    // ================================================================== chuyển động bắn ra

    private static double[] handPos(double px, double py, double pz, float yawDeg) {
        double yaw = Math.toRadians(HY); // bám theo hướng nhìn hiện tại, không phải hướng lúc cast
        double fwx = -Math.sin(yaw), fwz = Math.cos(yaw);
        double ax = Math.cos(yaw), az = Math.sin(yaw);
        return new double[] {px + fwx * 0.6 - ax * 0.35, py + 1.2, pz + fwz * 0.6 - az * 0.35};
    }

    /** Tâm vật thể: bay từ tay tới điểm chạm, rồi đứng yên (đáy chạm đất). */
    private static double[] fistCenter(float age, double ix, double iy, double iz, float yawDeg,
                                       double px, double py, double pz) {
        float t = flightT(age);
        float e = t * t * (3f - 2f * t); // smoothstep; đổi thành t*t nếu muốn lao nhanh hơn ở cuối
        double[] hand = handPos(px, py, pz, yawDeg);
        if (age < CHARGE) hand[1] += 0.06 * Math.sin(age * 0.7); // lơ lửng nhẹ lúc tụ lực
        double ty = iy + HH * (E ? FIST_SCALE_ENCH : FIST_SCALE) + 0.03;
        return new double[] {hand[0] + (ix - hand[0]) * e, hand[1] + (ty - hand[1]) * e, hand[2] + (iz - hand[2]) * e};
    }

    /** 0 trong lúc tụ lực, chạy 0 -> 1 trong lúc bay (từ tick CHARGE tới SLAM). */
    private static float flightT(float age) {
        return clamp01((age - CHARGE) / (SLAM - CHARGE));
    }

    /** Góc xoay quanh trục bắn (độ): 0 -> 540 khi tụ lực (nhanh dần), rồi 540 -> 0 khi bay. Liền mạch tại tick CHARGE. */
    private static float spinDeg(float age) {
        if (age < CHARGE) { float u = clamp01(age / CHARGE); return 540f * u * u; }
        float tf = flightT(age);
        return (1f - tf) * (1f - tf) * 540f;
    }

    private static float fistScale(float age) {
        float s;
        if (age < CHARGE) {
            s = 0.12f + 0.23f * easeOut(clamp01(age / CHARGE)); // hình thành ở vũ khí: 0.12 -> 0.35
        } else {
            s = 0.35f + 0.65f * easeOut(clamp01(flightT(age) / 0.7f));
        }
        float pop = (age >= SLAM && age < SLAM + 3f) ? 1f + 0.06f * (float) Math.sin(Math.PI * (age - SLAM) / 3.0) : 1f;
        return (E ? FIST_SCALE_ENCH : FIST_SCALE) * s * pop;
    }

    // ================================================================== thân màn hình

    private static void body(VertexConsumer vc, PoseStack.Pose pose, Fr f, float vis, float age) {
        // viền trước: quầng hồng + lõi đào sáng; viền sau: mờ hơn
        loop(vc, pose, f, OUT, -1.0, 0.12f, PINK, 0.5f * vis);
        loop(vc, pose, f, OUT, -1.0, 0.045f, mix(PINK, WHITE, 0.55f), 0.98f * vis);
        loop(vc, pose, f, OUT, 1.0, 0.06f, VIOLET, 0.75f * vis);
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
        int dashes = E ? 4 : 2;
        for (int k = 0; k < dashes; k++) {
            float pos = (age * 0.8f + k * n / (float) dashes) % n;
            int i0 = (int) pos;
            for (int j = 0; j < 3; j++) {
                double[] a = OUT[(i0 + j) % n], b = OUT[(i0 + j + 1) % n];
                float fall = 1f - j / 3f;
                int[] gc = E ? hue(k * 0.25f + T * 0.02f + j * 0.04f) : PINK;
                strip(vc, pose, f.at(a[0], a[1], -1.04), f.at(b[0], b[1], -1.04), 0.2f * f.s, gc[0], gc[1], gc[2], c255(0.3f * fall * vis));
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
            circle(vc, pose, f, side, x1, r, 0.035f, mix(PINK, WHITE, 0.55f), 0.95f * vis, 24);
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
            // 2 tia lửa vàng quay quanh trụ (bỏ ở enchant)
            for (int k = 0; !E && k < 2; k++) {
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
            int[] cc = E ? mix(hue(i / (float) segs + T * 0.015f + (side > 0 ? 0f : 0.5f)), WHITE, width < 0.05f ? 0.25f : 0.05f) : col;
            strip(vc, pose, pc(f, side, x, a0, rr), pc(f, side, x, a1, rr), width * f.s, cc[0], cc[1], cc[2], al);
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
            for (int k = 0; k < (E ? 4 : 2); k++) {
                Random rnd = new Random(id * 131L + burstTick * 13L + side * 977L + k * 31L + seed * 7919L);
                double[] a = f.abs(side * x1, 0, 0);
                double len = (2.0 + rnd.nextDouble() * 2.5) * grow * (E ? 1.5 : 1.0);
                double[] b = {a[0] + f.ux[0] * side * len + f.fw[0] * (rnd.nextDouble() - 0.5) * 2.4,
                              iy + 0.1 + rnd.nextDouble() * 0.3,
                              a[2] + f.ux[2] * side * len + f.fw[2] * (rnd.nextDouble() - 0.5) * 2.4};
                bolt(vc, pose, a, b, E ? 0.055f : 0.035f, vis * (0.65f + 0.35f * rnd.nextFloat()), rnd, 8, 0.5, VIOLET, WHITE);
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
        strip(vc, pose, a, b, 0.16f, VIOLET[0], VIOLET[1], VIOLET[2], c255(0.5f * vis));
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
        float t = flightT(age);
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
        if (age >= CHARGE && age < CHARGE + 3f) { // chớp ở nòng đúng lúc bắn ra
            float al = 1f - (age - CHARGE) / 3f;
            for (int k = 0; k < 6; k++) {
                Random rnd = new Random(id * 31L + burstTick + k * 7L);
                double[] e = {hand[0] + (rnd.nextDouble() - 0.5) * 0.9, hand[1] + (rnd.nextDouble() - 0.5) * 0.9,
                              hand[2] + (rnd.nextDouble() - 0.5) * 0.9};
                strip(vc, pose, hand, e, 0.04f, AMBER[0], AMBER[1], AMBER[2], c255(0.9f * al));
            }
        }
    }

    // ================================================================== trạng thái ENCHANT

    /** Dải màu enchant: tím -> xanh -> lam nhạt -> tím -> magenta -> hồng -> vàng đào -> hồng (tím chiếm ưu thế). h tuần hoàn 0..1. */
    private static final int[][] PAL = {VIOLET, BLUE, CYAN, BLUE, VIOLET, MAGENTA, PINK, GOLD, PINK, MAGENTA};

    private static int[] hue(float h) {
        h = h - (float) Math.floor(h);
        float x = h * PAL.length;
        int i = (int) x;
        return mix(PAL[i % PAL.length], PAL[(i + 1) % PAL.length], x - i);
    }

    /** Lớp phía SAU màn hình (vẽ trước để không bị che): tia sáng toả ra + vòng Hào Quang vỡ. */
    private static void enchBack(VertexConsumer vc, PoseStack.Pose pose, Fr f, float vis, float age) {
        float s = f.s;
        double zb = f.hd * 1.3;
        // 16 tia sáng toả ra, đan xen màu, xoay chậm
        for (int k = 0; k < 16; k++) {
            float ang = TAU * k / 16f + age * 0.012f;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            double r0 = f.hh * 0.85, r1 = f.hh * (2.4 + 0.6 * Math.sin(age * 0.2 + k));
            double w0 = 0.16 * s, w1 = 0.01 * s;
            double[][] q = {
                f.abs(ca * r0 - sa * w0, sa * r0 + ca * w0, zb), f.abs(ca * r0 + sa * w0, sa * r0 - ca * w0, zb),
                f.abs(ca * r1 + sa * w1, sa * r1 - ca * w1, zb), f.abs(ca * r1 - sa * w1, sa * r1 + ca * w1, zb)};
            int[] col = hue(k / 16f + age * 0.01f);
            quad2(vc, pose, q, col[0], col[1], col[2], c255((k % 2 == 0 ? 0.3f : 0.18f) * vis));
        }
        // vòng Hào Quang vỡ: 8 cung lệch bán kính/độ sâu, mỗi cung một màu, có vạch vỡ ở hai đầu
        double rh = f.hh * 1.95;
        for (int k = 0; k < 8; k++) {
            float a0 = age * 0.03f + TAU * k / 8f;
            float sweep = 0.52f;
            double rad = rh + 0.18 * s * Math.sin(age * 0.15 + k * 1.7);
            double depth = f.hd * (1.8 + 0.4 * Math.sin(age * 0.1 + k));
            int[] col = hue(k / 8f + age * 0.01f);
            for (int j = 0; j < 8; j++) {
                double t0 = a0 + sweep * j / 8.0, t1 = a0 + sweep * (j + 1) / 8.0;
                for (int pass = 0; pass < 2; pass++) {
                    double wd = (pass == 0 ? 0.22 : 0.09) * s;
                    double in = rad - wd, out = rad + wd;
                    double[][] q = {
                        f.abs(Math.cos(t0) * in, Math.sin(t0) * in, depth), f.abs(Math.cos(t0) * out, Math.sin(t0) * out, depth),
                        f.abs(Math.cos(t1) * out, Math.sin(t1) * out, depth), f.abs(Math.cos(t1) * in, Math.sin(t1) * in, depth)};
                    int[] cc = pass == 0 ? col : mix(col, WHITE, 0.35f);
                    quad2(vc, pose, q, cc[0], cc[1], cc[2], c255((pass == 0 ? 0.22f : 0.8f) * vis));
                }
            }
            for (int e = 0; e < 2; e++) {
                double t = e == 0 ? a0 : a0 + sweep;
                strip(vc, pose, f.abs(Math.cos(t) * (rad - 0.35 * s), Math.sin(t) * (rad - 0.35 * s), depth),
                      f.abs(Math.cos(t) * (rad + 0.35 * s), Math.sin(t) * (rad + 0.35 * s), depth),
                      0.025f * s, WHITE[0], WHITE[1], WHITE[2], c255(0.9f * vis));
            }
        }
    }

    /** Lớp phía TRƯỚC / trên mặt đất: trận đồ màu, sóng xung kích, tia xoè lúc chạm, 2 cột sáng, sao 8 cánh. */
    private static void enchFront(VertexConsumer vc, PoseStack.Pose pose, Fr f, float hAge, float vis, double iy) {
        float s = f.s;
        float age = hAge + SLAM;
        double y = iy + 0.06;
        double rg = 5.0;

        // trận đồ: vòng ngoài đứt đoạn, vòng trong quay ngược, 12 nan, vòng mảnh ngoài cùng
        for (int i = 0; i < 48; i++) {
            if (i % 4 == 3) continue;
            double a0 = TAU * i / 48.0 + age * 0.02, a1 = TAU * (i + 1) / 48.0 + age * 0.02;
            int[] col = hue(i / 48f + T * 0.01f);
            strip(vc, pose, gp(f, Math.cos(a0) * rg, Math.sin(a0) * rg, y), gp(f, Math.cos(a1) * rg, Math.sin(a1) * rg, y),
                  0.09f, col[0], col[1], col[2], c255(0.85f * vis));
        }
        for (int i = 0; i < 36; i++) {
            if (i % 3 == 2) continue;
            double a0 = -TAU * i / 36.0 - age * 0.03, a1 = -TAU * (i + 1) / 36.0 - age * 0.03;
            int[] col = hue(0.5f + i / 36f + T * 0.01f);
            strip(vc, pose, gp(f, Math.cos(a0) * rg * 0.72, Math.sin(a0) * rg * 0.72, y), gp(f, Math.cos(a1) * rg * 0.72, Math.sin(a1) * rg * 0.72, y),
                  0.06f, col[0], col[1], col[2], c255(0.7f * vis));
        }
        for (int k = 0; k < 12; k++) {
            double a = TAU * k / 12.0 + age * 0.01;
            int[] col = hue(k / 12f + T * 0.01f);
            strip(vc, pose, gp(f, Math.cos(a) * rg * 0.72, Math.sin(a) * rg * 0.72, y), gp(f, Math.cos(a) * rg, Math.sin(a) * rg, y),
                  0.04f, col[0], col[1], col[2], c255(0.5f * vis));
        }
        for (int i = 0; i < 64; i++) {
            double a0 = TAU * i / 64.0, a1 = TAU * (i + 1) / 64.0;
            int[] col = hue(i / 64f - T * 0.01f);
            strip(vc, pose, gp(f, Math.cos(a0) * (rg + 0.15), Math.sin(a0) * (rg + 0.15), y), gp(f, Math.cos(a1) * (rg + 0.15), Math.sin(a1) * (rg + 0.15), y),
                  0.03f, col[0], col[1], col[2], c255(0.45f * vis));
        }

        // 3 sóng xung kích màu, nối đuôi nhau
        for (int r = 0; r < 3; r++) {
            float t = clamp01((hAge - r * 2f) / 8f);
            if (t <= 0f || t >= 1f) continue;
            groundRing(vc, pose, f, t * rg * 1.5, 0.12f, hue(r * 0.33f + 0.05f), (1f - t) * vis);
        }
        // tia xoè lúc chạm
        if (hAge < 5f) {
            float k5 = 1f - hAge / 5f;
            for (int k = 0; k < 16; k++) {
                double a = TAU * k / 16.0;
                double len = (1.5 + (k % 2) * 1.5) * (0.5 + 1.2 * (1f - k5)) * s * 0.6;
                int[] col = hue(k / 16f);
                strip(vc, pose, gp(f, 0, 0, y + 0.02), gp(f, Math.cos(a) * len, Math.sin(a) * len, y + 0.02), 0.1f, col[0], col[1], col[2], c255(0.9f * k5 * vis));
            }
        }

        // 2 cột sáng từ nắp trụ bốc thẳng lên
        double x1 = f.hw + POD_LEN * s;
        float flick = 0.8f + 0.2f * (float) Math.sin(age * 0.5);
        for (int side = -1; side <= 1; side += 2) {
            double[] base = f.abs(side * x1, 0, 0);
            double top = f.cy + 4.2 * s;
            int[] col = hue((side < 0 ? 0.55f : 0.0f) + T * 0.01f);
            int pieces = 10;
            for (int j = 0; j < pieces; j++) {
                double y0 = iy + 0.05 + (top - iy - 0.05) * j / pieces, y1 = iy + 0.05 + (top - iy - 0.05) * (j + 1) / pieces;
                float g = (float) Math.pow(1.0 - (double) j / pieces, 1.3) * flick * vis;
                strip(vc, pose, new double[] {base[0], y0, base[2]}, new double[] {base[0], y1, base[2]}, 0.45f, col[0], col[1], col[2], c255(0.22f * g));
                strip(vc, pose, new double[] {base[0], y0, base[2]}, new double[] {base[0], y1, base[2]}, 0.09f, WHITE[0], WHITE[1], WHITE[2], c255(0.8f * g));
            }
        }

        // sao 8 cánh trên đỉnh màn hình
        double[] st = f.at(0.0, 1.34, -1.0);
        for (int k = 0; k < 8; k++) {
            float ang = TAU * k / 8f + age * 0.05f;
            float len = (k % 2 == 0 ? 0.8f : 0.45f) * s * (0.7f + 0.3f * (float) Math.sin(age * 0.4 + k));
            int[] col = hue(k / 8f + T * 0.02f);
            strip(vc, pose, st, f.abs0(st, Math.cos(ang) * len, Math.sin(ang) * len), 0.035f * s, col[0], col[1], col[2], c255(vis));
        }
    }

    // ================================================================== sương điện

    /**
     * Sương điện: các đám sương tím mềm (đĩa gradient) trôi sát mặt đất, vài dòng sương cong quét quanh màn hình,
     * và tia điện nhỏ nhảy giữa các đám kèm quầng sáng ở đầu tia. Không phải hạt bụi: toàn bộ là mảng sáng mềm.
     * Thứ tự: lớp thấp -> cao, trong mỗi lớp y tăng dần theo từng đám để các đĩa không z-fight nhau.
     */
    private static void mist(VertexConsumer vc, PoseStack.Pose pose, Fr f, int id, long burstTick,
                             float hAge, float vis, double iy) {
        float age = hAge + SLAM;
        float v = vis * easeOut(clamp01(hAge / 5f));
        if (v <= 0.01f) return;
        int puffs = E ? 8 : 5;
        double rm = E ? 4.2 : 3.0;
        float k0 = E ? 1.35f : 1f;

        for (int layer = 0; layer < 2; layer++) {
            for (int k = 0; k < puffs; k++) {
                double ang = TAU * k / puffs + age * (0.018 + 0.004 * (k % 3)) * (k % 2 == 0 ? 1 : -1);
                double rad = rm * (0.3 + 0.7 * hash(k, 41 + layer)) * (0.9 + 0.1 * Math.sin(age * 0.08 + k));
                double cx = f.cx + Math.cos(ang) * rad, cz = f.cz + Math.sin(ang) * rad;
                double size = (1.5 + hash(k, 43)) * k0 * (layer == 0 ? 1.0 : 0.7);
                double y = iy + 0.10 + layer * 0.55 + k * 0.004;
                int[] col = E ? hue(k / (float) puffs + T * 0.01f) : mix(VIOLET, PINK, hash(k, 44) * 0.6f);
                float breathe = 0.75f + 0.25f * (float) Math.sin(age * 0.1 + k * 1.3);
                softDisc(vc, pose, cx, y, cz, size, col, 0.3f * breathe * v * (layer == 0 ? 1f : 0.75f));
            }
        }

        // dòng sương cong quét quanh màn hình sát đất
        int streams = E ? 4 : 2;
        for (int k = 0; k < streams; k++) {
            double dir = k % 2 == 0 ? 1.0 : -1.0;
            double base = age * 0.03 * dir + k * TAU / streams;
            int[] col = E ? hue(k * 0.25f + T * 0.01f) : (k % 2 == 0 ? VIOLET : PINK);
            double[] prev = null;
            for (int j = 0; j <= 12; j++) {
                double a = base + j * 0.12 * dir;
                double rr = rm * 0.75 + 0.5 * Math.sin(j * 0.7 + age * 0.1);
                double[] p = {f.cx + Math.cos(a) * rr, iy + 0.25 + 0.35 * Math.sin(j * 0.5 + age * 0.08 + k), f.cz + Math.sin(a) * rr};
                if (prev != null) {
                    float fa = (float) Math.sin(Math.PI * j / 12.0);
                    strip(vc, pose, prev, p, 0.5f * k0, col[0], col[1], col[2], c255(0.24f * fa * v));
                    strip(vc, pose, prev, p, 0.05f, WHITE[0], WHITE[1], WHITE[2], c255(0.45f * fa * v));
                }
                prev = p;
            }
        }

        // tia điện nhỏ nhảy trong sương + quầng sáng ở đầu tia
        long seed = (long) (age / 2f);
        int arcs = E ? 7 : 4;
        for (int k = 0; k < arcs; k++) {
            Random rnd = new Random(id * 211L + burstTick * 17L + seed * 331L + k * 53L);
            double ang = rnd.nextDouble() * TAU, dist = Math.sqrt(rnd.nextDouble()) * rm;
            double sx = f.cx + Math.cos(ang) * dist, sz = f.cz + Math.sin(ang) * dist;
            double sy = iy + 0.15 + rnd.nextDouble() * 0.9;
            double da = rnd.nextDouble() * TAU, len = 0.7 + rnd.nextDouble() * 0.8;
            double[] a = {sx, sy, sz};
            double[] b = {sx + Math.cos(da) * len, sy + (rnd.nextDouble() - 0.5) * 0.6, sz + Math.sin(da) * len};
            int[] col = E ? hue(rnd.nextFloat()) : VIOLET;
            bolt(vc, pose, a, b, E ? 0.035f : 0.025f, 0.9f * v, rnd, 5, 0.22, VIOLET, WHITE);
            softDisc(vc, pose, b[0], b[1], b[2], 0.35, col, 0.45f * v);
        }
    }

    /** Đĩa sáng mềm nằm ngang: các vòng đồng tâm giảm dần độ đậm từ tâm ra rìa. */
    private static void softDisc(VertexConsumer vc, PoseStack.Pose pose, double cx, double y, double cz,
                                 double radius, int[] col, float alpha) {
        final int rings = 4, segs = 12;
        for (int i = 0; i < rings; i++) {
            double r0 = radius * i / rings, r1 = radius * (i + 1) / rings;
            float a = alpha * (float) Math.pow(1.0 - (i + 0.5) / rings, 1.1);
            int al = c255(a);
            if (al <= 0) continue;
            for (int sIdx = 0; sIdx < segs; sIdx++) {
                double a0 = TAU * sIdx / segs, a1 = TAU * (sIdx + 1) / segs;
                double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
                quad2(vc, pose, new double[][] {
                    {cx + r0 * c0, y, cz + r0 * s0}, {cx + r1 * c0, y, cz + r1 * s0},
                    {cx + r1 * c1, y, cz + r1 * s1}, {cx + r0 * c1, y, cz + r0 * s1}}, col[0], col[1], col[2], al);
            }
        }
    }

    // ================================================================== tụ lực ở vũ khí

    /** Tia điện toả ra quanh quả cầu đang tụ lực, mạnh dần tới lúc bắn. */
    private static void chargeFx(VertexConsumer vc, PoseStack.Pose pose, int id, long burstTick, float age, Fr f) {
        float prog = clamp01(age / CHARGE);
        long seed = (long) (age / 2f);
        int n = 2 + (int) (6 * prog);
        double len = (0.5 + 1.2 * prog) * (E ? 1.3 : 1.0) * f.s / 0.35;
        for (int k = 0; k < n; k++) {
            Random rnd = new Random(id * 97L + burstTick * 11L + seed * 211L + k * 53L);
            double a = rnd.nextDouble() * TAU, b = (rnd.nextDouble() - 0.5) * Math.PI;
            double l = len * (0.5 + 0.5 * rnd.nextDouble()) * 0.5;
            double[] p = {f.cx, f.cy, f.cz};
            double[] q = {f.cx + Math.cos(a) * Math.cos(b) * l, f.cy + Math.sin(b) * l, f.cz + Math.sin(a) * Math.cos(b) * l};
            bolt(vc, pose, p, q, 0.025f + 0.02f * prog, (0.5f + 0.5f * prog), rnd, 4, 0.2, VIOLET, WHITE);
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
            int[] cc = E ? mix(hue(i / (float) pts.length + T * 0.015f), WHITE, width < 0.06f ? 0.28f : 0.05f) : col;
            strip(vc, pose, f.at(a[0], a[1], w), f.at(b[0], b[1], w), width * f.s, cc[0], cc[1], cc[2], al);
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
        if (E) { // mỗi ô một màu
            int[] hc = hue((float) (u0 * 0.35 + 0.5) + T * 0.012f);
            glow = hc;
            core = mix(hc, WHITE, 0.35f);
        }
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
        if (E) { // mỗi tia sét một màu
            glow = hue(rnd.nextFloat());
            core = mix(glow, WHITE, 0.6f);
        }
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
            int a8 = c255(al * (pass == 0 ? 0.75f : 1f));
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