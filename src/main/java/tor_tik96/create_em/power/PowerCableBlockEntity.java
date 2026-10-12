package tor_tik96.create_em.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.*;

/**
 * Узел силового кабеля. Сеть (все соединённые кабели и их «концы» — соседние FE-блоки)
 * строится обходом в ширину и кэшируется до любого изменения кабелей в мире.
 * Каждая грань кабеля — вход: что в неё толкнули, сразу делится между концами сети
 * (кроме того, откуда пришло).
 */
public class PowerCableBlockEntity extends BlockEntity {
    /** Меняется при любой перестройке кабелей — все кэши сетей сбрасываются. */
    private static int VERSION;

    private int builtVersion = -1;
    @Nullable private Network network;
    private final Map<Direction, IEnergyStorage> faces = new EnumMap<>(Direction.class);

    public PowerCableBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.POWER_CABLE_BE.get(), pos, state);
        for (Direction d : Direction.values()) faces.put(d, new Face(d));
    }

    static void invalidateNetworks() {
        VERSION++;
    }

    public IEnergyStorage port(@Nullable Direction side) {
        return faces.get(side == null ? Direction.UP : side);
    }

    @Nullable
    private Network network() {
        if (!(level instanceof ServerLevel sl)) return null;
        if (network == null || builtVersion != VERSION) {
            network = Network.build(sl, worldPosition);
            builtVersion = VERSION;
        }
        return network;
    }

    /** Конец сети: соседний не-кабель и грань, которой он к нам повёрнут. */
    private record End(BlockPos pos, BlockCapabilityCache<IEnergyStorage, Direction> cache) {}

    private static final class Network {
        final List<End> ends = new ArrayList<>();
        /** Защита от замыкания: конец сети в ответ сразу толкнул обратно в кабель. Общая на все сети. */
        static boolean busy;

        static Network build(ServerLevel level, BlockPos start) {
            Network net = new Network();
            Set<BlockPos> seen = new HashSet<>();
            Set<Long> endKeys = new HashSet<>();
            Deque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            seen.add(start);
            while (!queue.isEmpty() && seen.size() < 4096) {
                BlockPos p = queue.poll();
                BlockState st = level.getBlockState(p);
                if (!(st.getBlock() instanceof PowerCableBlock)) continue;
                for (Direction d : Direction.values()) {
                    if (!st.getValue(PowerCableBlock.PROPS.get(d))) continue;
                    BlockPos np = p.relative(d);
                    if (level.getBlockState(np).getBlock() instanceof PowerCableBlock) {
                        if (seen.add(np)) queue.add(np);
                    } else if (endKeys.add(np.asLong() * 6 + d.ordinal())) {
                        net.ends.add(new End(np, BlockCapabilityCache.create(Capabilities.EnergyStorage.BLOCK, level,
                                np, d.getOpposite())));
                    }
                }
            }
            return net;
        }

        /** Делим поровну, остаток — кто сколько возьмёт. */
        int distribute(int amount, boolean simulate, BlockPos from) {
            if (busy || amount <= 0) return 0;
            busy = true;
            try {
                List<IEnergyStorage> targets = new ArrayList<>();
                for (End e : ends) {
                    if (e.pos.equals(from)) continue;
                    IEnergyStorage t = e.cache.getCapability();
                    if (t != null && t.canReceive()) targets.add(t);
                }
                if (targets.isEmpty()) return 0;
                int limit = Math.min(amount, Config.safe(Config.CABLE_MAX_TRANSFER, Integer.MAX_VALUE));
                int left = limit;
                for (int i = 0; i < targets.size() && left > 0; i++) {
                    int share = Math.max(1, left / (targets.size() - i));
                    left -= targets.get(i).receiveEnergy(Math.min(share, left), simulate);
                }
                for (IEnergyStorage t : targets) {
                    if (left <= 0) break;
                    left -= t.receiveEnergy(left, simulate);
                }
                return limit - left;
            } finally {
                busy = false;
            }
        }
    }

    /** Грань кабеля: принимает и тут же раздаёт. */
    private final class Face implements IEnergyStorage {
        private final Direction side;

        Face(Direction side) {
            this.side = side;
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            Network net = network();
            return net == null ? 0 : net.distribute(amount, simulate, worldPosition.relative(side));
        }

        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return 0; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }
}
