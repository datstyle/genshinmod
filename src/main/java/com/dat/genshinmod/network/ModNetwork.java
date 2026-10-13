package com.dat.genshinmod.network;

import com.dat.genshinmod.genshinmod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(genshinmod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void register() {
        CHANNEL.registerMessage(0, ShakePacket.class,
                ShakePacket::encode, ShakePacket::decode, ShakePacket::handle);
        CHANNEL.registerMessage(1, FxPacket.class,
                FxPacket::encode, FxPacket::decode, FxPacket::handle);
    }

    /** Gửi gói tin cho mọi người chơi trong bán kính radius quanh vị trí pos. */
    public static void sendNear(ServerLevel level, Vec3 pos, double radius, Object msg) {
        for (ServerPlayer sp : level.players()) {
            if (sp.position().distanceToSqr(pos) <= radius * radius) {
                CHANNEL.sendTo(msg, sp.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            }
        }
    }
}