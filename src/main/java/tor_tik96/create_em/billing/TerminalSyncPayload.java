package tor_tik96.create_em.billing;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import tor_tik96.create_em.Create_em;

import java.util.ArrayList;
import java.util.List;

/** Сервер → клиент: имена поставщиков игрока для вкладок терминала и выбранная вкладка. */
public record TerminalSyncPayload(int containerId, List<String> names, int selected) implements CustomPacketPayload {
    public static final Type<TerminalSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Create_em.MODID, "terminal_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalSyncPayload> CODEC = StreamCodec.ofMember(
            (p, buf) -> {
                buf.writeVarInt(p.containerId);
                buf.writeVarInt(p.names.size());
                for (String n : p.names) buf.writeUtf(n, 64);
                buf.writeVarInt(p.selected);
            },
            buf -> {
                int id = buf.readVarInt();
                int n = buf.readVarInt();
                List<String> names = new ArrayList<>(n);
                for (int i = 0; i < n; i++) names.add(buf.readUtf(64));
                return new TerminalSyncPayload(id, names, buf.readVarInt());
            });

    @Override public Type<TerminalSyncPayload> type() { return TYPE; }

    public static void handle(TerminalSyncPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof TerminalMenu m && m.containerId == p.containerId())
                m.setTabs(p.names(), p.selected());
        });
    }
}
