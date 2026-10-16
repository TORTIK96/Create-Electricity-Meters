package tor_tik96.create_em.line;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.power.EnergyPort;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static tor_tik96.create_em.meter.EnergyMeterBlockEntity.line;

/**
 * Обратный преобразователь: берёт энергию с сервисной линии и отдаёт обычную FE с боков и снизу —
 * без счётчика и без расчётов. Трансформатор кормит его, только если у них один хозяин:
 * так поставщик пользуется своей энергией сам, а чужой преобразователь с его линии ничего не получит.
 */
public class LineConverterBlockEntity extends BlockEntity implements LineNode, LineSink, IHaveGoggleInformation {
    private static final int WINDOW = 20;
    private static final Direction[] OUTPUTS = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    private final LineLinks links = new LineLinks(this);
    @Nullable private UUID owner;
    private String ownerName = "";
    private int buffer;
    /** Сколько потребители взяли за прошлый тик. */
    private int lastSent;
    private final Map<Direction, BlockCapabilityCache<IEnergyStorage, Direction>> caches = new EnumMap<>(Direction.class);
    private int next;

    private long windowFe;
    private int windowTicks;
    private float outPerTick; // синхронизируется

    /** Кабели могут и сами забирать — для маркировки и для модов, которые только тянут. */
    private final EnergyPort output = EnergyPort.output((a, sim) -> {
        int given = Math.max(0, Math.min(a, buffer));
        if (!sim && given > 0) { buffer -= given; lastSent += given; setChanged(); }
        return given;
    }, () -> buffer, this::bufferCap);

    public LineConverterBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.LINE_CONVERTER_BE.get(), pos, state);
    }

    @Nullable
    public IEnergyStorage port(@Nullable Direction side) {
        return side == Direction.UP ? null : output;
    }

    /** Пара тиков реальной раздачи, как у счётчика: после обрыва линии долго «из ниоткуда» не кормит. */
    private int bufferCap() {
        return (int) Math.min(Config.TRANSFORMER_MAX_TRANSFER.get() * 2L, Math.max(256L, 2L * lastSent));
    }

    @Override
    public int acceptFromLine(int amount) {
        int accepted = Math.max(0, Math.min(amount, bufferCap() - buffer));
        if (accepted > 0) { buffer += accepted; setChanged(); }
        return accepted;
    }

    /** Кормит ли его трансформатор этого хозяина. */
    public boolean belongsTo(@Nullable UUID supplier) {
        return supplier != null && supplier.equals(owner);
    }

    void serverTick() {
        if (!(level instanceof ServerLevel sl)) return;
        buffer = Math.min(buffer, bufferCap());
        int sent = lastSent; // то, что кабели сами забрали через output
        lastSent = 0;
        // по кругу, чтобы ни одна сторона не забирала всё всегда первой
        for (int i = 0; i < OUTPUTS.length && buffer > 0; i++) {
            Direction d = OUTPUTS[(next + i) % OUTPUTS.length];
            IEnergyStorage t = caches.computeIfAbsent(d, k -> BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, sl,
                    worldPosition.relative(k), k.getOpposite())).getCapability();
            if (t == null || !t.canReceive()) continue;
            int moved = t.receiveEnergy(buffer, false);
            buffer -= moved;
            sent += moved;
        }
        next = (next + 1) % OUTPUTS.length;
        lastSent = sent;
        if (sent > 0) setChanged();

        windowFe += sent;
        if (++windowTicks >= WINDOW) {
            float out = windowFe / (float) WINDOW;
            boolean changed = out != outPerTick;
            outPerTick = out;
            windowFe = 0;
            windowTicks = 0;
            if (changed) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void setOwnerName(String name) {
        ownerName = name;
        setChanged();
    }

    // ---------- LineNode ----------
    @Override public LineLinks getLinks() { return links; }
    @Override public Vec3 getWireAnchor() { return new Vec3(0.5, 15.5 / 16, 0.5); }
    @Override public int getMaxLinks() { return 1; }
    @Override public @Nullable UUID getOwner() { return owner; }
    @Override public void setOwner(@Nullable UUID owner) { this.owner = owner; setChanged(); }

    // ---------- очки ----------
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(1, Component.translatable("create_em.converter.owner").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(ownerName.isEmpty() ? "?" : ownerName).withStyle(ChatFormatting.AQUA))));
        tooltip.add(line(1, Component.translatable("create_em.converter.output").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("%,.0f FE/t", outPerTick)).withStyle(ChatFormatting.AQUA))));
        tooltip.add(line(1, Component.translatable(links.size() > 0 ? "create_em.converter.linked" : "create_em.converter.no_line")
                .withStyle(links.size() > 0 ? ChatFormatting.GRAY : ChatFormatting.GOLD)));
        return true;
    }

    // ---------- NBT ----------
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        links.save(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        links.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        if (tag.contains("Out")) outPerTick = tag.getFloat("Out");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag tag = new CompoundTag();
        links.save(tag);
        tag.putString("OwnerName", ownerName);
        tag.putFloat("Out", outPerTick);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
