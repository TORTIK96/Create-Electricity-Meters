package tor_tik96.create_em.panel;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import tor_tik96.create_em.Create_em;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Сервер → клиент: список абонентов и хранилище для открытой панели поставщика. */
public record PanelSyncPayload(int containerId, List<AccountView> accounts, List<VaultView> vault,
                               long price, long perFe, int[] values, int nextBillSec) implements CustomPacketPayload {
    public static final Type<PanelSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Create_em.MODID, "panel_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PanelSyncPayload> CODEC = StreamCodec.ofMember(PanelSyncPayload::write, PanelSyncPayload::read);

    public record AccountView(UUID consumer, String name, long balance, int stage, boolean disabled, int cap,
                              long sinceFe, long unbilledFe) {}

    public record VaultView(ItemStack sample, long count) {}

    @Override public Type<PanelSyncPayload> type() { return TYPE; }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(accounts.size());
        for (AccountView a : accounts) {
            buf.writeUUID(a.consumer());
            buf.writeUtf(a.name(), 64);
            buf.writeLong(a.balance());
            buf.writeVarInt(a.stage());
            buf.writeBoolean(a.disabled());
            buf.writeVarInt(a.cap());
            buf.writeLong(a.sinceFe());
            buf.writeLong(a.unbilledFe());
        }
        buf.writeVarInt(vault.size());
        for (VaultView v : vault) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, v.sample());
            buf.writeLong(v.count());
        }
        buf.writeLong(price);
        buf.writeLong(perFe);
        buf.writeVarInt(values.length);
        for (int v : values) buf.writeVarInt(v);
        buf.writeVarInt(nextBillSec);
    }

    private static PanelSyncPayload read(RegistryFriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int n = buf.readVarInt();
        List<AccountView> acc = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            acc.add(new AccountView(buf.readUUID(), buf.readUtf(64), buf.readLong(), buf.readVarInt(), buf.readBoolean(),
                    buf.readVarInt(), buf.readLong(), buf.readLong()));
        int m = buf.readVarInt();
        List<VaultView> vault = new ArrayList<>(m);
        for (int i = 0; i < m; i++) vault.add(new VaultView(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readLong()));
        long price = buf.readLong(), perFe = buf.readLong();
        int[] values = new int[buf.readVarInt()];
        for (int i = 0; i < values.length; i++) values[i] = buf.readVarInt();
        return new PanelSyncPayload(id, acc, vault, price, perFe, values, buf.readVarInt());
    }

    /** Клиент: отдать данные открытой панели, если это она. */
    public static void handle(PanelSyncPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof SupplierPanelMenu m && m.containerId == p.containerId())
                m.setClientData(p);
        });
    }
}
