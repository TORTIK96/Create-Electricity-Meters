package tor_tik96.create_em.meter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import tor_tik96.create_em.meter.client.ClientAccess;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

/**
 * Энергия заходит сверху в небольшой буфер, каждый тик счётчик сам толкает её в выходы
 * (сзади и снизу) и учитывает ТОЛЬКО то, что потребитель реально принял.
 * Вытянуть энергию из счётчика нельзя ни с какой стороны.
 */
public class EnergyMeterBlockEntity extends BlockEntity implements IHaveGoggleInformation, IHaveHoveringInformation {
    private static final int WINDOW = 20; // усреднение потока за секунду

    private int buffer;
    private long totalFe;
    private long windowFe;
    private int windowTicks;
    private float flowPerTick; // синхронизируется на клиент
    private long paidFe;       // показание totalFe на момент последней оплаты
    private long paidAtTick = -1; // игровое время последней оплаты, -1 = ещё не было
    private int syncedLimit;   // лимит с сервера (конфиг на клиенте недоступен)

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
            // пока идёт энергия — синхронизируем раз в секунду, чтобы показания в подсказке шли вживую
            boolean changed = windowFe > 0 || newFlow != flowPerTick || syncedLimit != getTier().limitPerTick();
            syncedLimit = getTier().limitPerTick();
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
    public long getSincePaymentFe() { return totalFe - paidFe; }
    public float getFlowPerTick() { return flowPerTick; }

    /**
     * Отметить оплату: «с последней оплаты» начинает считаться заново.
     * Вызывается расчётной системой; пока её нет — из креатива (Shift-ПКМ пустой рукой).
     */
    public void markPaid() {
        paidFe = totalFe;
        paidAtTick = level != null ? level.getGameTime() : 0;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ---------------- подсказки Create ----------------
    private static final ChatFormatting LABEL = ChatFormatting.GRAY, VALUE = ChatFormatting.AQUA;

    /** Без очков: только сколько набежало. С очками ничего не добавляем — всё есть в подсказке очков. */
    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (ClientAccess.isWearingGoggles()) return false;
        tooltip.add(line(0, Component.translatable("create_em.tooltip.total").withStyle(LABEL)
                .append(kwh(totalFe))));
        tooltip.add(line(0, Component.translatable("create_em.tooltip.since_payment").withStyle(LABEL)
                .append(kwh(getSincePaymentFe()))));
        return true;
    }

    /** В очках инженера: все параметры. С Shift — ещё и сырые FE. */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));

        tooltip.add(line(0, Component.translatable("create_em.tooltip.readings").withStyle(LABEL)));
        tooltip.add(line(1, Component.translatable("create_em.tooltip.total").withStyle(LABEL).append(kwh(totalFe))));
        if (isPlayerSneaking) tooltip.add(line(2, fe(totalFe)));
        tooltip.add(line(1, Component.translatable("create_em.tooltip.since_payment").withStyle(LABEL).append(kwh(getSincePaymentFe()))));
        if (isPlayerSneaking) tooltip.add(line(2, fe(getSincePaymentFe())));
        tooltip.add(line(1, Component.translatable("create_em.tooltip.last_payment").withStyle(LABEL).append(lastPaymentText())));

        int limit = Math.max(1, syncedLimit);
        int loadPct = Math.round(flowPerTick * 100f / limit);
        ChatFormatting loadColor = loadPct >= 95 ? ChatFormatting.RED : loadPct >= 75 ? ChatFormatting.GOLD : ChatFormatting.GREEN;

        tooltip.add(line(0, Component.translatable("create_em.tooltip.power").withStyle(LABEL)));
        tooltip.add(line(1, Component.translatable("create_em.tooltip.flow").withStyle(LABEL)
                .append(Component.literal(String.format("%,.0f FE/t", flowPerTick)).withStyle(VALUE))));
        tooltip.add(line(1, Component.translatable("create_em.tooltip.limit").withStyle(LABEL)
                .append(Component.literal(String.format("%,d FE/t", syncedLimit)).withStyle(VALUE))));
        tooltip.add(line(1, Component.translatable("create_em.tooltip.load").withStyle(LABEL)
                .append(Component.literal(loadPct + "%").withStyle(loadColor))));
        return true;
    }

    private static MutableComponent line(int indent, Component c) {
        return Component.literal(" ".repeat(4 + indent * 2)).append(c); // отступы как у Create
    }

    private static MutableComponent kwh(long fe) {
        double v = fe / (double) Config.FE_PER_KWH.get();
        return Component.translatable("create_em.unit.kwh", String.format("%,.2f", v)).withStyle(VALUE);
    }

    private static MutableComponent fe(long fe) {
        return Component.literal(String.format("%,d FE", fe)).withStyle(ChatFormatting.DARK_GRAY);
    }

    private MutableComponent lastPaymentText() {
        if (paidAtTick < 0 || level == null)
            return Component.translatable("create_em.tooltip.never").withStyle(ChatFormatting.DARK_GRAY);
        long days = Math.max(0, (level.getGameTime() - paidAtTick) / 24000L);
        return Component.translatable("create_em.tooltip.days_ago", days).withStyle(VALUE);
    }

    // ---------------- сохранение и синхронизация ----------------
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("Buffer", buffer);
        tag.putLong("TotalFe", totalFe);
        tag.putFloat("Flow", flowPerTick);
        tag.putLong("PaidFe", paidFe);
        tag.putLong("PaidAt", paidAtTick);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        buffer = tag.getInt("Buffer");
        totalFe = tag.getLong("TotalFe");
        flowPerTick = tag.getFloat("Flow");
        paidFe = tag.getLong("PaidFe");
        paidAtTick = tag.contains("PaidAt") ? tag.getLong("PaidAt") : -1;
        if (tag.contains("Limit")) syncedLimit = tag.getInt("Limit"); // только в пакете синхронизации
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("TotalFe", totalFe);
        tag.putFloat("Flow", flowPerTick);
        tag.putLong("PaidFe", paidFe);
        tag.putLong("PaidAt", paidAtTick);
        tag.putInt("Limit", getTier().limitPerTick());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
