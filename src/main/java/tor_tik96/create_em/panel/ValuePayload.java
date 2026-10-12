package tor_tik96.create_em.panel;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import tor_tik96.create_em.Create_em;

import java.util.UUID;

/**
 * Клиент → сервер: значение из поля ввода (баланс, потолок, тариф, цена валюты, лимит).
 * Права проверяет меню на сервере.
 */
public record ValuePayload(int containerId, int field, UUID target, long value) implements CustomPacketPayload {
    public static final UUID NONE = new UUID(0, 0);
    public static final Type<ValuePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Create_em.MODID, "set_value"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ValuePayload> CODEC = StreamCodec.ofMember(
            (p, buf) -> { buf.writeVarInt(p.containerId); buf.writeVarInt(p.field); buf.writeUUID(p.target); buf.writeLong(p.value); },
            buf -> new ValuePayload(buf.readVarInt(), buf.readVarInt(), buf.readUUID(), buf.readLong()));

    @Override public Type<ValuePayload> type() { return TYPE; }

    /** Меню, которое принимает значения из полей ввода. */
    public interface Receiver {
        void receiveValue(ServerPlayer player, int field, UUID target, long value);
    }

    public static void handle(ValuePayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && sp.containerMenu instanceof Receiver r
                    && sp.containerMenu.containerId == p.containerId())
                r.receiveValue(sp, p.field(), p.target(), p.value());
        });
    }
}
