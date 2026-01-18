package com.ichezzy.evolutionboost.hud;

import com.ichezzy.evolutionboost.EvolutionBoost;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C Payload um dem Client mitzuteilen, dass er sein HUD togglen soll.
 * 
 * @param action 0 = toggle, 1 = on, 2 = off, 3 = status query
 */
public record HudTogglePayload(int action) implements CustomPacketPayload {

    public static final int ACTION_TOGGLE = 0;
    public static final int ACTION_ON = 1;
    public static final int ACTION_OFF = 2;
    public static final int ACTION_STATUS = 3;

    public static final CustomPacketPayload.Type<HudTogglePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EvolutionBoost.MOD_ID, "hud_toggle"));

    public static final StreamCodec<FriendlyByteBuf, HudTogglePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeVarInt(payload.action),
                    buf -> new HudTogglePayload(buf.readVarInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
