package com.dat.genshinmod.network;

import com.dat.genshinmod.client.CameraShake;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Gói tin server gửi cho máy người chơi ở gần để rung màn hình. */
public class ShakePacket {
    private final float intensity;   // góc lắc lớn nhất (độ)
    private final int duration;      // số tick
    private final double x, y, z;    // tâm chấn

    public ShakePacket(float intensity, int duration, double x, double y, double z) {
        this.intensity = intensity;
        this.duration = duration;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static void encode(ShakePacket m, FriendlyByteBuf buf) {
        buf.writeFloat(m.intensity);
        buf.writeInt(m.duration);
        buf.writeDouble(m.x);
        buf.writeDouble(m.y);
        buf.writeDouble(m.z);
    }

    public static ShakePacket decode(FriendlyByteBuf buf) {
        return new ShakePacket(buf.readFloat(), buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(ShakePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        CameraShake.start(m.intensity, m.duration, m.x, m.y, m.z)));
        ctx.get().setPacketHandled(true);
    }
}