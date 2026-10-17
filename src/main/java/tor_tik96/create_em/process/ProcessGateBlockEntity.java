package tor_tik96.create_em.process;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;

import static tor_tik96.create_em.meter.EnergyMeterBlockEntity.line;

/**
 * Шлюз обработки — воронка над лентой на сундуке/бочке.
 * Источник выкладывает партию на ленту под собой (когда диспетчер даёт очередь), помечая предметы «сырое».
 * «Выход» снимает с ленты под собой только готовое — обработанное и без метки.
 */
public class ProcessGateBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    @Nullable private BlockPos dispatcher;
    /** Только клиент: заслонки как у воронки Create. */
    public final Flaps flaps = new Flaps(1);
    private boolean linked; // синхронизируется: диспетчер на месте и в пределах дальности

    public ProcessGateBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.PROCESS_GATE_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public GateMode mode() {
        return getBlockState().getValue(ProcessGateBlock.MODE);
    }

    @Nullable
    public BlockPos getDispatcher() {
        return dispatcher;
    }

    void setDispatcher(@Nullable BlockPos pos) {
        dispatcher = pos;
        setChanged();
    }

    /** Инвентарь, на котором висит шлюз. */
    @Nullable
    public IItemHandler inventory() {
        if (level == null) return null;
        Direction d = getBlockState().getValue(ProcessGateBlock.FACING);
        return level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(d), d.getOpposite());
    }

    public static int range() {
        return Config.safe(Config.DISPATCHER_RANGE, 32);
    }

    /** Сервер: предмет прошёл через шлюз — заслонки дёргаются у всех, кто рядом. */
    void flap(boolean inward) {
        if (level != null && !level.isClientSide)
            level.blockEvent(worldPosition, getBlockState().getBlock(), 0, inward ? 1 : 0);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == 0) {
            if (level != null && level.isClientSide) flaps.bump(0, param == 1);
            return true;
        }
        return super.triggerEvent(id, param);
    }

    public boolean hasBelt() {
        return getBlockState().getValue(ProcessGateBlock.BELT) != GateBelt.NONE;
    }

    @Nullable
    private ProcessDispatcherBlockEntity dispatcherBE() {
        if (level == null || dispatcher == null || !level.isLoaded(dispatcher) || !dispatcher.closerThan(worldPosition, range())) return null;
        return level.getBlockEntity(dispatcher) instanceof ProcessDispatcherBlockEntity d ? d : null;
    }

    /** Источник: выложить партию на ленту под собой. */
    private void release(ProcessDispatcherBlockEntity d) {
        CatalystProcess m = mode().process;
        IItemHandler h = inventory();
        if (m == null || h == null || level == null) return;
        var belt = com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(level, worldPosition.below(),
                com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.TYPE);
        if (belt == null || !belt.canInsertFromSide(Direction.DOWN)) return;
        for (int i = 0; i < h.getSlots(); i++) {
            net.minecraft.world.item.ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty() || !m.canProcess(s, level)) continue;
            net.minecraft.world.item.ItemStack batch = h.extractItem(i, 16, true);
            if (batch.isEmpty()) continue;
            batch = batch.copy();
            batch.set(EMRegistry.RAW_BATCH.get(), m.ordinal()); // «сырое»: снимет только своя обработка
            var rest = belt.handleInsertion(new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(batch), Direction.DOWN, true);
            int fits = batch.getCount() - rest.getCount();
            if (fits <= 0) return; // лента под шлюзом занята
            net.minecraft.world.item.ItemStack real = h.extractItem(i, fits, false).copy();
            real.set(EMRegistry.RAW_BATCH.get(), m.ordinal());
            belt.handleInsertion(new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(real), Direction.DOWN, false);
            d.released();
            flap(false);
            return;
        }
    }

    /** Выход: снять с ленты под собой готовое (обработано и без метки «сырое»). */
    private void collect() {
        IItemHandler inv = inventory();
        if (inv == null || level == null) return;
        var handler = com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour.get(level, worldPosition.below(),
                com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) return;
        handler.handleCenteredProcessingOnAllItems(.5f, t -> {
            var R = com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult.doNothing();
            if (t.processedBy == null || t.stack.has(EMRegistry.RAW_BATCH.get())) return R; // сырое — пусть едет по кругу
            var rest = net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(inv, t.stack.copy(), false);
            if (rest.getCount() == t.stack.getCount()) return R;
            flap(true);
            if (rest.isEmpty())
                return com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult.removeItem();
            var left = t.copy();
            left.stack = rest;
            return com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult.convertTo(left);
        });
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide) {
            flaps.tick();
            return;
        }
        if (level == null) return;
        if (hasBelt()) {
            if (mode() == GateMode.OUTPUT) collect();
            else {
                ProcessDispatcherBlockEntity d = dispatcherBE();
                if (d != null && d.mayRelease(this)) release(d);
            }
        }
        if (level.getGameTime() % 20 != 0) return;
        boolean ok = false;
        if (dispatcher != null && level.isLoaded(dispatcher) && dispatcher.closerThan(worldPosition, range())
                && level.getBlockEntity(dispatcher) instanceof ProcessDispatcherBlockEntity d) {
            d.register(worldPosition);
            ok = true;
        }
        if (ok != linked) {
            linked = ok;
            sendData();
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(1, Component.translatable(mode().translationKey()).withStyle(ChatFormatting.AQUA)));
        if (!hasBelt())
            tooltip.add(line(1, Component.translatable("create_em.gate.no_belt").withStyle(ChatFormatting.GOLD)));
        tooltip.add(line(1, Component.translatable(linked ? "create_em.gate.linked" : dispatcher == null ? "create_em.gate.not_linked"
                : "create_em.gate.lost").withStyle(linked ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.write(tag, reg, clientPacket);
        if (dispatcher != null) tag.putLong("Dispatcher", dispatcher.asLong());
        if (clientPacket) tag.putBoolean("Linked", linked);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.read(tag, reg, clientPacket);
        dispatcher = tag.contains("Dispatcher") ? BlockPos.of(tag.getLong("Dispatcher")) : null;
        if (clientPacket) linked = tag.getBoolean("Linked");
    }
}
