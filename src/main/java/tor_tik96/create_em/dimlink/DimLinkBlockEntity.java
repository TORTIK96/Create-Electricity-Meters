package tor_tik96.create_em.dimlink;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.EMFormat;
import tor_tik96.create_em.power.EnergyPort;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static tor_tik96.create_em.meter.EnergyMeterBlockEntity.line;

/**
 * Межпространственный узел. Пара узлов одного хозяина передаёт FE между измерениями:
 * «приём» берёт энергию с кабелей и шлёт её второму концу, «отдача» раздаёт её соседям.
 * Баланс: потери в процентах, плата за держание канала, предел мощности, и оба конца
 * должны быть загружены — узел сам чанки не грузит.
 */
public class DimLinkBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public enum Status { NOT_PAIRED, SAME_DIMENSION, PARTNER_UNLOADED, PARTNER_MISSING, SAME_MODE, NO_POWER, OK, WAITING }

    private static final int WINDOW = 20;

    @Nullable private UUID owner;
    private String ownerName = "";
    private int buffer;
    private int lastPulled;
    private int next;
    private final Map<Direction, BlockCapabilityCache<IEnergyStorage, Direction>> caches = new EnumMap<>(Direction.class);

    private long window;
    private int windowTicks;
    /** Когда сюда последний раз пришла энергия (для «отдачи»). */
    private long lastReceived = Long.MIN_VALUE;

    // синхронизируется
    private Status status = Status.NOT_PAIRED;
    private float rate;
    private String partnerText = "";

    private final EnergyPort input = EnergyPort.input((a, sim) -> {
        if (isOut()) return 0;
        int got = Math.max(0, Math.min(a, inCap() - buffer));
        if (!sim && got > 0) { buffer += got; setChanged(); }
        return got;
    }, () -> buffer, this::inCap);

    private final EnergyPort output = EnergyPort.output((a, sim) -> {
        if (!isOut()) return 0;
        int given = Math.max(0, Math.min(a, buffer));
        if (!sim && given > 0) { buffer -= given; lastPulled += given; setChanged(); }
        return given;
    }, () -> buffer, this::outCap);

    public DimLinkBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.DIM_LINK_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public boolean isOut() {
        return getBlockState().getValue(DimLinkBlock.OUT);
    }

    public IEnergyStorage port(@Nullable Direction side) {
        return isOut() ? output : input;
    }

    private static int maxTransfer() { return Config.safe(Config.DIM_LINK_MAX_TRANSFER, 2048); }
    private static int upkeep() { return Config.safe(Config.DIM_LINK_UPKEEP, 32); }
    private static int loss() { return Math.max(0, Math.min(90, Config.safe(Config.DIM_LINK_LOSS_PERCENT, 10))); }

    private int inCap() { return maxTransfer() * 2 + upkeep() * 20; }
    private int outCap() { return maxTransfer() * 2; }

    @Nullable public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }

    public void setOwner(UUID id, String name) {
        owner = id;
        ownerName = name;
        setChanged();
        sendData();
    }

    public GlobalPos self() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    /** Второй конец кладёт сюда энергию; возвращает, сколько поместилось. */
    int acceptFromPartner(int amount, long time) {
        int got = Math.max(0, Math.min(amount, outCap() - buffer));
        if (got > 0) { buffer += got; setChanged(); }
        lastReceived = time;
        return got;
    }

    int room() {
        return Math.max(0, outCap() - buffer);
    }

    void onModeChanged() {
        buffer = 0;
        setChanged();
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel sl)) return;
        DimLinks reg = DimLinks.get(sl.getServer());
        GlobalPos partner = reg.partner(self());
        String text = partner == null ? "" : partner.dimension().location() + " " + partner.pos().toShortString();
        int moved;
        Status st;
        if (isOut()) {
            moved = push(sl);
            st = partner == null ? Status.NOT_PAIRED
                    : sl.getGameTime() - lastReceived <= WINDOW ? Status.OK : Status.WAITING;
        } else {
            moved = 0;
            st = sendTo(sl, partner);
            if (st == Status.OK) moved = lastSentToPartner;
        }

        window += moved;
        boolean changed = st != status || !text.equals(partnerText);
        if (++windowTicks >= WINDOW) {
            float r = window / (float) WINDOW;
            changed |= r != rate;
            rate = r;
            window = 0;
            windowTicks = 0;
        }
        status = st;
        partnerText = text;
        if (changed) sendData();
    }

    private int lastSentToPartner;

    /** «Приём»: шлём второму концу. */
    private Status sendTo(ServerLevel sl, @Nullable GlobalPos partner) {
        lastSentToPartner = 0;
        if (partner == null) return Status.NOT_PAIRED;
        if (partner.dimension().equals(sl.dimension()) && !Config.DIM_LINK_SAME_DIMENSION.get())
            return Status.SAME_DIMENSION;
        ServerLevel pl = sl.getServer().getLevel(partner.dimension());
        if (pl == null || !pl.shouldTickBlocksAt(partner.pos())) return Status.PARTNER_UNLOADED;
        if (!(pl.getBlockEntity(partner.pos()) instanceof DimLinkBlockEntity other)) return Status.PARTNER_MISSING;
        if (!other.isOut()) return Status.SAME_MODE;
        int fee = upkeep();
        if (buffer < fee) return Status.NO_POWER;
        buffer -= fee;
        setChanged();

        int keep = 100 - loss();
        int send = Math.min(buffer, maxTransfer());
        int delivered = Math.min(send * keep / 100, other.room());
        if (delivered > 0) {
            int cost = Math.min(buffer, (delivered * 100 + keep - 1) / keep);
            buffer -= cost;
            other.acceptFromPartner(delivered, pl.getGameTime());
            lastSentToPartner = delivered;
        } else {
            other.acceptFromPartner(0, pl.getGameTime());
        }
        return Status.OK;
    }

    /** «Отдача»: раздаём соседям по кругу. */
    private int push(ServerLevel sl) {
        int sent = lastPulled;
        lastPulled = 0;
        Direction[] all = Direction.values();
        for (int i = 0; i < all.length && buffer > 0; i++) {
            Direction d = all[(next + i) % all.length];
            IEnergyStorage t = caches.computeIfAbsent(d, k -> BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, sl,
                    worldPosition.relative(k), k.getOpposite())).getCapability();
            if (t == null || !t.canReceive()) continue;
            int moved = t.receiveEnergy(buffer, false);
            buffer -= moved;
            sent += moved;
        }
        next = (next + 1) % all.length;
        if (sent > 0) setChanged();
        return sent;
    }

    // ---------- очки ----------
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(1, Component.translatable(isOut() ? "create_em.dim_link.mode_out" : "create_em.dim_link.mode_in")
                .withStyle(ChatFormatting.AQUA)));
        tooltip.add(line(1, Component.translatable("create_em.dim_link.owner").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(ownerName.isEmpty() ? "?" : ownerName).withStyle(ChatFormatting.AQUA))));
        if (!partnerText.isEmpty())
            tooltip.add(line(1, Component.translatable("create_em.dim_link.partner").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(partnerText).withStyle(ChatFormatting.AQUA))));
        tooltip.add(line(1, Component.translatable(isOut() ? "create_em.dim_link.received" : "create_em.dim_link.sent")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(EMFormat.fe(Math.round(rate)) + "/t").withStyle(ChatFormatting.AQUA))));
        if (!isOut())
            tooltip.add(line(1, Component.translatable("create_em.dim_link.costs", loss(), EMFormat.fe(upkeep()))
                    .withStyle(ChatFormatting.DARK_GRAY)));
        boolean good = status == Status.OK;
        tooltip.add(line(1, Component.translatable("create_em.dim_link.status." + status.name().toLowerCase())
                .withStyle(good ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
        return true;
    }

    // ---------- NBT ----------
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.write(tag, reg, clientPacket);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
        tag.putInt("Status", status.ordinal());
        tag.putFloat("Rate", rate);
        tag.putString("Partner", partnerText);
        if (!clientPacket) tag.putInt("Buffer", buffer);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.read(tag, reg, clientPacket);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        int s = tag.getInt("Status");
        status = s >= 0 && s < Status.values().length ? Status.values()[s] : Status.NOT_PAIRED;
        rate = tag.getFloat("Rate");
        partnerText = tag.getString("Partner");
        if (!clientPacket) buffer = tag.getInt("Buffer");
    }
}
