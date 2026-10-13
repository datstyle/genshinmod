package com.dat.genshinmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;

/** Rung góc nhìn (camera) khi có chấn động gần người chơi; xa tâm chấn thì rung nhẹ hơn. */
public class CameraShake {
    // Nhân toàn bộ độ mạnh của mọi cú rung: 1.0 là mặc định, 0.5 là một nửa, 0 là tắt hẳn
    private static final float SHAKE_SCALE = 0.6F;

    private static float intensity;   // góc lắc lớn nhất (độ)
    private static int duration;
    private static int age = 0;

    public static void start(float i, int d, double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double dist = mc.player.position().distanceTo(new Vec3(x, y, z));
        float scaled = i * (float) Math.max(0.0, 1.0 - dist / 48.0);   // xa thì yếu dần
        if (scaled < 0.05F) return;

        float current = age < duration ? intensity * (1F - (float) age / duration) : 0F;
        if (scaled >= current) {   // chỉ thay khi mạnh hơn cú rung đang chạy
            intensity = scaled;
            duration = d;
            age = 0;
        }
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (age < duration && !Minecraft.getInstance().isPaused()) age++;
    }

    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (age >= duration) return;
        double t = age + e.getPartialTick();
        float decay = 1F - (float) (t / duration);
        if (decay <= 0F) return;
        float amp = intensity * decay * decay * SHAKE_SCALE;   // yếu dần về cuối

        e.setPitch(e.getPitch() + (float) Math.sin(t * 1.1) * amp);
        e.setYaw(e.getYaw() + (float) Math.sin(t * 0.8 + 1.7) * amp * 0.5F);
        e.setRoll(e.getRoll() + (float) Math.sin(t * 1.4 + 0.6) * amp * 0.6F);
    }
}