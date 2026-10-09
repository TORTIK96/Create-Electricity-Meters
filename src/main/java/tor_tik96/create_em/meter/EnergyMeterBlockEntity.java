package tor_tik96.create_em.meter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.registry.EMRegistry;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

/**
 * Энергия заходит сверху в небольшой буфер, каждый тик счётчик сам толкает её в выходы
 * (сзади и снизу) и учитывает ТОЛЬКО то, что потребитель реально принял.
 * Вытянуть энергию из счётчика нельзя ни с какой стороны.
 */
public class EnergyMeterBlockEntity extends BlockEntity {
    private static final int WINDOW = 20; // усреднение потока за секунду

    private int buffer;
    private long totalFe;
    private long windowFe;
    private int windowTicks;
    private float flowPerTick; // синхронизируется на клиент

    // только клиент: стрелка андезитового счётчика
    private float needleAngle, prevNeedleAngle, needleSpeed;

    @Nullable private BlockCapabilityCache<IEnergyStorage, Direction> backCache, bottomCache;

    private final IEnergyStorage input = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) {
            int accepted = Math.max(0, Math.min(max, getMaxEnergyStored() - buffer));
            if (!simulate && accepted > 0) { buffer += accepted; setChanged(); }
            return accepted;
        }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return buffer; }
        @Override public int getMaxEnergyStored() { return getTier().bufferSize(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    public EnergyMeterBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.METER_BE.get(), pos, state);
    }

    public IEnergyStorage getInput() { return input; }

    public MeterTier getTier() {
        return getBlockState().getBlock() instanceof EnergyMeterBlock b ? b.getTier() : MeterTier.ANDESITE;
    }

    // ---------------- сервер ----------------
    void serverTick() {
        if (backCache == null) initCaches();

        int budget = getTier().limitPerTick();       // лимит счётчика на тик
        int sent = push(backCache, budget);
        sent += push(bottomCache, budget - sent);

        totalFe += sent;
        windowFe += sent;
        if (sent > 0) setChanged();

        if (++windowTicks >= WINDOW) {
            float newFlow = windowFe / (float) WINDOW;
            boolean changed = Math.abs(newFlow - flowPerTick) > 0.5f || (newFlow == 0) != (flowPerTick == 0);
            flowPerTick = newFlow;
            windowFe = 0;
            windowTicks = 0;
            if (changed && level != null)
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private int push(@Nullable BlockCapabilityCache<IEnergyStorage, Direction> cache, int budget) {
        if (cache == null || buffer <= 0 || budget <= 0) return 0;
        IEnergyStorage target = cache.getCapability();
        if (target == null || !target.canReceive()) return 0;
        int moved = target.receiveEnergy(Math.min(buffer, budget), false);
        buffer -= moved;
        return moved;
    }

    private void initCaches() {
        if (!(level instanceof ServerLevel sl)) return;
        Direction back = getBlockState().getValue(FACING).getOpposite();
        backCache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, sl,
                worldPosition.relative(back), back.getOpposite());
        bottomCache = BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, sl,
                worldPosition.below(), Direction.UP);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        backCache = bottomCache = null; // поворот -> пересоздать кеши
    }

    // ---------------- клиент ----------------
    /** Скорость стрелки, град/тик: 10 FE/t ≈ 0.95 об/с, 1000 FE/t ≈ 2.7 об/с, предел 3.6 об/с. */
    public static float targetDegPerTick(float flow) {
        return flow <= 0 ? 0 : Math.min(72f, 18f * (float) Math.log10(1 + flow));
    }

    void clientTick() {
        needleSpeed += (targetDegPerTick(flowPerTick) - needleSpeed) * 0.08f;
        prevNeedleAngle = needleAngle;
        needleAngle += needleSpeed;
        if (needleAngle >= 360f) { needleAngle -= 360f; prevNeedleAngle -= 360f; }
    }

    public float getNeedleAngle(float partialTick) {
        return prevNeedleAngle + (needleAngle - prevNeedleAngle) * partialTick;
    }

    // ---------------- показания ----------------
    public long getTotalFe() { return totalFe; }
    public double getTotalKwh() { return totalFe / (double) Config.FE_PER_KWH.get(); }
    public float getFlowPerTick() { return flowPerTick; }

    // ---------------- сохранение и синхронизация ----------------
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("Buffer", buffer);
        tag.putLong("TotalFe", totalFe);
        tag.putFloat("Flow", flowPerTick);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        buffer = tag.getInt("Buffer");
        totalFe = tag.getLong("TotalFe");
        flowPerTick = tag.getFloat("Flow");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("TotalFe", totalFe);
        tag.putFloat("Flow", flowPerTick);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
