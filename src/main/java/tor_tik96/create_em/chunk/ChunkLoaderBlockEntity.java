package tor_tik96.create_em.chunk;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.energy.IEnergyStorage;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.EMFormat;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;

import static tor_tik96.create_em.meter.EnergyMeterBlockEntity.line;

/**
 * Загрузчик чанков: держит загруженными чанки вокруг себя (1×1, 3×3, 5×5), пока на него подаётся FE.
 * Расход — за каждый чанк. Без энергии отпускает всё, включая свой чанк.
 */
public class ChunkLoaderBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private ScrollValueBehaviour radius;
    private int energy;
    /** Какой радиус сейчас загружен (-1 — ничего). Сохраняется, чтобы после перезапуска знать, что отпускать. */
    private int loaded = -1;
    private boolean powered; // синхронизируется

    private final IEnergyStorage input = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) {
            int got = Math.max(0, Math.min(max, capacity() - energy));
            if (!simulate && got > 0) { energy += got; setChanged(); }
            return got;
        }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return capacity(); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    public ChunkLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.CHUNK_LOADER_BE.get(), pos, state);
    }

    public IEnergyStorage getEnergy() {
        return input;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        radius = new ScrollValueBehaviour(Component.translatable("create_em.chunk_loader.area"), this,
                new CenteredSideValueBoxTransform((s, d) -> d.getAxis().isHorizontal())) {
            @Override
            public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
                return new ValueSettingsBoard(label, max, 1, List.of(Component.translatable("create_em.chunk_loader.area_row")),
                        new ValueSettingsFormatter(v -> Component.literal(side(v.value()) + "×" + side(v.value()))));
            }
        }.between(0, maxRadius());
        radius.value = Math.min(1, maxRadius());
        behaviours.add(radius);
    }

    private static int maxRadius() {
        return Config.safe(Config.CHUNK_LOADER_MAX_RADIUS, 2);
    }

    private static int side(int r) {
        return 2 * r + 1;
    }

    private int wantedRadius() {
        return Math.max(0, Math.min(radius.getValue(), maxRadius()));
    }

    private static int costFor(int r) {
        return side(r) * side(r) * Config.safe(Config.CHUNK_LOADER_FE_PER_CHUNK, 32);
    }

    /** Запас — на секунду работы при самом большом радиусе: впрок не копим. */
    private int capacity() {
        return costFor(wantedRadius()) * 20;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel sl)) return;
        int r = wantedRadius();
        int cost = costFor(r);
        boolean ok = energy >= cost;
        if (ok) {
            energy -= cost;
            setChanged();
        }
        energy = Math.min(energy, capacity());
        if (ok && loaded != r) {
            release(sl);
            force(sl, r, true);
            loaded = r;
            setChanged();
        } else if (!ok && loaded >= 0) {
            release(sl);
        }
        if (ok != powered) {
            powered = ok;
            sendData();
        }
    }

    private void force(ServerLevel sl, int r, boolean add) {
        ChunkPos c = new ChunkPos(worldPosition);
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                ChunkLoading.TICKETS.forceChunk(sl, worldPosition, c.x + dx, c.z + dz, add, true);
    }

    private void release(ServerLevel sl) {
        if (loaded < 0) return;
        force(sl, loaded, false);
        loaded = -1;
        setChanged();
    }

    /** Блок убрали — отпускаем чанки. */
    void onRemoved() {
        if (level instanceof ServerLevel sl) release(sl);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        int r = wantedRadius();
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(1, Component.translatable("create_em.chunk_loader.tooltip.area").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(side(r) + "×" + side(r) + " (" + side(r) * side(r) + ")").withStyle(ChatFormatting.AQUA))));
        tooltip.add(line(1, Component.translatable("create_em.chunk_loader.tooltip.usage").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(EMFormat.fe(costFor(r)) + "/t").withStyle(ChatFormatting.AQUA))));
        tooltip.add(line(1, Component.translatable(powered ? "create_em.chunk_loader.on" : "create_em.chunk_loader.off")
                .withStyle(powered ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.write(tag, reg, clientPacket);
        tag.putInt("Energy", energy);
        tag.putInt("Loaded", loaded);
        tag.putBoolean("Powered", powered);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.read(tag, reg, clientPacket);
        energy = tag.getInt("Energy");
        loaded = tag.contains("Loaded") ? tag.getInt("Loaded") : -1;
        powered = tag.getBoolean("Powered");
    }
}
