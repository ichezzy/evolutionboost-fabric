package com.ichezzy.evolutionboost.hud;

import com.ichezzy.evolutionboost.EvolutionBoost;
import com.ichezzy.evolutionboost.boost.BoostType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * S2C-Payload: schickt pro BoostType genau einen dimensionalen Multiplikator
 * (für die aktuelle Dimension des Spielers).
 */
public record DimBoostHudPayload(double[] multipliers) implements CustomPacketPayload {

    // evolutionboost:dim_boost_hud
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(EvolutionBoost.MOD_ID, "dim_boost_hud");

    public static final Type<DimBoostHudPayload> TYPE = new Type<>(ID);

    // Codec: schreibt/liest genau BoostType.values().length Doubles
    public static final StreamCodec<RegistryFriendlyByteBuf, DimBoostHudPayload> CODEC =
            new StreamCodec<>() {
                @Override
                public DimBoostHudPayload decode(RegistryFriendlyByteBuf buf) {
                    double[] values = new double[BoostType.values().length];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = buf.readDouble();
                    }
                    return new DimBoostHudPayload(values);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, DimBoostHudPayload payload) {
                    double[] values = payload.multipliers();
                    int len = BoostType.values().length;
                    for (int i = 0; i < len; i++) {
                        double v = (i < values.length) ? values[i] : 1.0D;
                        buf.writeDouble(v);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
