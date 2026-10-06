package com.iamkaf.battlepumpkins.network;

import com.iamkaf.amber.api.networking.v1.NetworkChannel;
import com.iamkaf.amber.util.Env;
import com.iamkaf.amber.util.EnvExecutor;
import com.iamkaf.amber.api.networking.v1.Packet;
import com.iamkaf.amber.api.networking.v1.PacketContext;
import com.iamkaf.battlepumpkins.Constants;
import com.iamkaf.battlepumpkins.client.CarveScreen;
import com.iamkaf.battlepumpkins.pumpkin.BattlePumpkin;
import com.iamkaf.battlepumpkins.pumpkin.Face;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * The server opens the carving screen; the screen sends back a carved face or a request to take out the light. The
 * server checks ownership and distance in {@link BattlePumpkin}.
 */
public final class BattlePumpkinsNetwork {
    public static final NetworkChannel CHANNEL = NetworkChannel.create(Constants.id("main"));

    private BattlePumpkinsNetwork() {
    }

    public static void init() {
        CHANNEL.register(OpenCarveScreen.class, OpenCarveScreen::encode, OpenCarveScreen::decode,
                (packet, context) -> context.execute(() -> EnvExecutor.runInEnv(Env.CLIENT, () -> () -> CarveScreen.open(packet.entityId()))));
        CHANNEL.register(Carve.class, Carve::encode, Carve::decode,
                (packet, context) -> onPumpkin(context, packet.entityId(), pumpkin -> pumpkin.carve(context.getPlayer(), packet.face())));
        CHANNEL.register(TakeOutLight.class, TakeOutLight::encode, TakeOutLight::decode,
                (packet, context) -> onPumpkin(context, packet.entityId(), pumpkin -> pumpkin.takeOutLight(context.getPlayer())));
    }

    private static void onPumpkin(PacketContext context, int entityId, Consumer<BattlePumpkin> action) {
        context.execute(() -> {
            ServerPlayer player = context.getServerPlayer();
            if (player != null && player.level().getEntity(entityId) instanceof BattlePumpkin pumpkin) {
                action.accept(pumpkin);
            }
        });
    }

    public record OpenCarveScreen(int entityId) implements Packet<OpenCarveScreen> {
        static void encode(OpenCarveScreen packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
        }

        static OpenCarveScreen decode(FriendlyByteBuf buffer) {
            return new OpenCarveScreen(buffer.readVarInt());
        }
    }

    public record Carve(int entityId, Face face) implements Packet<Carve> {
        static void encode(Carve packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
            buffer.writeFixedSizeLongArray(packet.face.words());
        }

        static Carve decode(FriendlyByteBuf buffer) {
            int entityId = buffer.readVarInt();
            return new Carve(entityId, Face.of(buffer.readFixedSizeLongArray(new long[4])));
        }
    }

    public record TakeOutLight(int entityId) implements Packet<TakeOutLight> {
        static void encode(TakeOutLight packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.entityId);
        }

        static TakeOutLight decode(FriendlyByteBuf buffer) {
            return new TakeOutLight(buffer.readVarInt());
        }
    }
}
