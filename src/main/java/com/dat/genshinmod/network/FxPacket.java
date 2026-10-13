package com.dat.genshinmod.network;

import com.dat.genshinmod.client.SlamFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server báo cho máy người chơi ở gần dựng hiệu ứng hình ảnh thật (không dùng hạt). */
public class FxPacket {
    public static final byte RISE = 0;   // bay lên + lơ lửng (đi theo người dùng chiêu)
    public static final byte LAND = 1;   // chạm đất: vòng xung, nứt đất, sét, cung vàng...

    private final byte kind;
    private final int entityId;
    private final double x, y, z;
    private final long seed;
    private final boolean enchanted;

    public FxPacket(byte kind, int entityId, double x, double y, double z, long seed, boolean enchanted) {
        this.kind = kind;
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.seed = seed;
        this.enchanted = enchanted;
    }

    public static void encode(FxPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.kind);
        buf.writeInt(m.entityId);
        buf.writeDouble(m.x);
        buf.writeDouble(m.y);
        buf.writeDouble(m.z);
        buf.writeLong(m.seed);
        buf.writeBoolean(m.enchanted);
    }

    public static FxPacket decode(FriendlyByteBuf buf) {
        return new FxPacket(buf.readByte(), buf.readInt(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readLong(), buf.readBoolean());
    }

    public static void handle(FxPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        SlamFx.add(m.kind, m.entityId, m.x, m.y, m.z, m.seed, m.enchanted)));
        ctx.get().setPacketHandled(true);
    }
}