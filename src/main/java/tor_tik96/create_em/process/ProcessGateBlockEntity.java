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

/** Шлюз обработки: висит на сундуке/бочке и сообщает диспетчеру о себе раз в секунду. */
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

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide) {
            flaps.tick();
            return;
        }
        if (level == null || level.getGameTime() % 20 != 0) return;
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
