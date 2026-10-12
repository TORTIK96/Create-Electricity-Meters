package tor_tik96.create_em.line;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import tor_tik96.create_em.meter.EnergyMeterBlockEntity;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.*;

/** Операции над сервисной линией: соединение, разрыв, поиск счётчиков. */
public final class LineNetwork {
    /** Растёт при любом изменении соединений; трансформаторы по нему понимают, что пора пересчитать сеть. */
    private static int version;

    public static int version() { return version; }
    static void bump() { version++; }

    public static void link(LineNode a, BlockPos posA, LineNode b, BlockPos posB) {
        a.getLinks().add(posB);
        b.getLinks().add(posA);
    }

    /** Блок-узел поставлен: запоминаем владельца. */
    public static void onPlaced(Level level, BlockPos pos, LivingEntity placer) {
        if (!level.isClientSide && placer != null && level.getBlockEntity(pos) instanceof LineNode node)
            node.setOwner(placer.getUUID());
    }

    /**
     * Блок-узел убирают: разрываем все соединения и роняем по проводу на каждое.
     * Вызывать из onRemove ДО super, пока BlockEntity ещё на месте.
     */
    public static void onRemoved(Level level, BlockPos pos) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof LineNode node)) return;
        int wires = node.getLinks().size();
        for (BlockPos other : List.copyOf(node.getLinks().get())) {
            if (level.isLoaded(other) && level.getBlockEntity(other) instanceof LineNode o)
                o.getLinks().remove(pos);
        }
        node.getLinks().clear();
        if (wires > 0) Block.popResource(level, pos, new ItemStack(EMRegistry.LINE_WIRE.get(), wires));
    }

    /**
     * Все счётчики, до которых можно дойти от узла по проводам.
     * Незагруженные участки пропускаются; ссылки на исчезнувшие узлы удаляются.
     */
    public static List<EnergyMeterBlockEntity> findMeters(Level level, BlockPos start) {
        List<EnergyMeterBlockEntity> meters = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof LineNode node)) continue;

            if (be instanceof EnergyMeterBlockEntity meter && !pos.equals(start)) {
                meters.add(meter);
                continue; // счётчик — конец линии
            }
            for (BlockPos next : List.copyOf(node.getLinks().get())) {
                if (!level.isLoaded(next)) continue;
                if (!(level.getBlockEntity(next) instanceof LineNode)) {
                    node.getLinks().remove(next); // узел там пропал, пока чанк был выгружен
                    continue;
                }
                if (visited.add(next)) queue.add(next);
            }
        }
        return meters;
    }

    private LineNetwork() {}
}
