package tor_tik96.create_em.line;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.meter.EnergyMeterBlockEntity;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;
import java.util.UUID;

import static tor_tik96.create_em.meter.EnergyMeterBlockEntity.line;

/**
 * Принимает FE в буфер и каждый тик раздаёт её поровну всем счётчикам своей линии,
 * не больше transformerMaxTransfer за тик. Энергия по линии идёт только в счётчики.
 */
public class TransformerBlockEntity extends BlockEntity implements LineNode, IHaveGoggleInformation {
    private static final int WINDOW = 20;

    private final LineLinks links = new LineLinks(this);
    @Nullable private UUID owner;
    private int buffer;

    private List<EnergyMeterBlockEntity> meters = List.of();
    private int metersVersion = -1;
    private int rescanTimer;

    private long windowFe;
    private int windowTicks;
    private float outPerTick;  // синхронизируется
    private int meterCount;    // синхронизируется: всего счётчиков на линии
    private int sealedCount;   // синхронизируется: из них с пломбой этого поставщика
    private int syncedMax;     // синхронизируется

    private final IEnergyStorage input = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) {
            int accepted = Math.max(0, Math.min(max, getMaxEnergyStored() - buffer));
            if (!simulate && accepted > 0) { buffer += accepted; setChanged(); }
            return accepted;
        }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return buffer; }
        @Override public int getMaxEnergyStored() { return bufferCap(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    public TransformerBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.TRANSFORMER_BE.get(), pos, state);
    }

    public IEnergyStorage getInput() { return input; }

    /** Сколько счётчики взяли за прошлый тик. */
    private int lastSent;

    /** Запас — пара тиков реальной раздачи (минимум 256), иначе после отключения генератора линия ещё долго живёт. */
    private int bufferCap() {
        return (int) Math.min(Config.TRANSFORMER_MAX_TRANSFER.get() * 2L, Math.max(256L, 2L * lastSent));
    }

    /** Гул под нагрузкой — звук на клиенте, сам выключается, когда раздача прекратилась. */
    void clientTick() {
        tor_tik96.create_em.line.client.TransformerHum.tick(this);
    }

    public float getOutPerTick() { return outPerTick; }

    /** Насколько нагружен (0…1) — громкость гула. */
    public float getLoad() {
        return syncedMax <= 0 ? 0 : Math.min(1f, outPerTick / syncedMax);
    }

    void serverTick() {
        if (level == null) return;
        if (metersVersion != LineNetwork.version() || --rescanTimer <= 0) {
            meters = LineNetwork.findMeters(level, worldPosition);
            metersVersion = LineNetwork.version();
            rescanTimer = 100; // раз в 5 с — чтобы подхватить подгрузившиеся чанки
        }

        int sent = 0;
        int budget = Math.min(buffer, Config.TRANSFORMER_MAX_TRANSFER.get());
        // энергию получают только счётчики, опломбированные владельцем этого трансформатора
        List<EnergyMeterBlockEntity> sealed = new java.util.ArrayList<>();
        for (EnergyMeterBlockEntity m : meters)
            if (owner != null && !m.isRemoved() && owner.equals(m.getSealSupplier())) sealed.add(m);

        if (budget > 0 && !sealed.isEmpty()) {
            // поровну, а недобор одних счётчиков достаётся остальным
            List<EnergyMeterBlockEntity> open = new java.util.ArrayList<>(sealed);
            while (budget > 0 && !open.isEmpty()) {
                int share = Math.max(1, budget / open.size());
                var it = open.iterator();
                while (it.hasNext() && budget > 0) {
                    EnergyMeterBlockEntity m = it.next();
                    if (m.isRemoved()) { it.remove(); continue; }
                    int got = m.acceptFromLine(Math.min(share, budget));
                    if (got < Math.min(share, budget)) it.remove();
                    budget -= got;
                    sent += got;
                }
            }
            buffer -= sent;
            if (sent > 0) setChanged();
        }
        lastSent = sent;

        windowFe += sent;
        if (++windowTicks >= WINDOW) {
            float newOut = windowFe / (float) WINDOW;
            int max = Config.TRANSFORMER_MAX_TRANSFER.get();
            boolean changed = newOut != outPerTick || meterCount != meters.size() || sealedCount != sealed.size() || syncedMax != max;
            outPerTick = newOut;
            meterCount = meters.size();
            sealedCount = sealed.size();
            syncedMax = max;
            windowFe = 0;
            windowTicks = 0;
            if (changed) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---------- LineNode ----------
    @Override public LineLinks getLinks() { return links; }
    @Override public Vec3 getWireAnchor() { return new Vec3(0.5, 15.5 / 16, 0.5); }
    @Override public int getMaxLinks() { return 4; }
    @Override public @Nullable UUID getOwner() { return owner; }
    @Override public void setOwner(@Nullable UUID owner) { this.owner = owner; setChanged(); }

    // ---------- очки ----------
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(1, Component.translatable("create_em.tooltip.line_output").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("%,.0f / %,d FE/t", outPerTick, syncedMax)).withStyle(ChatFormatting.AQUA))));
        tooltip.add(line(1, Component.translatable("create_em.tooltip.line_meters").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(meterCount)).withStyle(ChatFormatting.AQUA))));
        tooltip.add(line(1, Component.translatable("create_em.tooltip.line_sealed").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(sealedCount)).withStyle(sealedCount < meterCount ? ChatFormatting.GOLD : ChatFormatting.AQUA))));
        tooltip.add(line(1, Component.translatable("create_em.tooltip.line_wires").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(links.size() + " / " + getMaxLinks()).withStyle(ChatFormatting.AQUA))));
        return true;
    }

    // ---------- NBT ----------
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        links.save(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Buffer", buffer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        links.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        buffer = tag.getInt("Buffer");
        if (tag.contains("Out")) {
            outPerTick = tag.getFloat("Out");
            meterCount = tag.getInt("Meters");
            sealedCount = tag.getInt("Sealed");
            syncedMax = tag.getInt("Max");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag tag = new CompoundTag();
        links.save(tag);
        tag.putFloat("Out", outPerTick);
        tag.putInt("Meters", meterCount);
        tag.putInt("Sealed", sealedCount);
        tag.putInt("Max", Config.TRANSFORMER_MAX_TRANSFER.get());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
