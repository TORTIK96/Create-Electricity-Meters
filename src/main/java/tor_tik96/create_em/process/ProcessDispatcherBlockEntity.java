package tor_tik96.create_em.process;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.*;

import static tor_tik96.create_em.meter.EnergyMeterBlockEntity.line;

/**
 * Диспетчер обработки. Работает со <b>шлюзами</b>: шлюзы на сундуках/бочках привязаны к диспетчеру,
 * у каждого своя роль — обработка (откуда брать) или выход (куда класть готовое).
 * <ul>
 *   <li>Выход на ленту — грань, от которой уходит лента (определяется сама).</li>
 *   <li>С любой другой грани принимает ленту: готовое (пометка Create) — в выходной шлюз,
 *       сырое — снова на ленту.</li>
 *   <li>Следующий шлюз — когда в активном не осталось того, что его режим умеет обработать,
 *       и на лентах от выхода нет ни одного предмета. Без таймеров.</li>
 * </ul>
 */
public class ProcessDispatcherBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    private static final int BATCH = 16;
    private static final int MAX_BELTS = 64;

    /** Только клиент: шторки на гранях с лентами, как у туннеля Create (индекс — Direction.ordinal). */
    public final Flaps flaps = new Flaps(6);

    private void flap(Direction side, boolean inward) {
        if (level != null && !level.isClientSide)
            level.blockEvent(worldPosition, getBlockState().getBlock(), side.ordinal(), inward ? 1 : 0);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id >= 0 && id < 6) {
            if (level != null && level.isClientSide) flaps.bump(id, param == 1);
            return true;
        }
        return super.triggerEvent(id, param);
    }

    /** Шлюзы, которые отметились (раз в секунду). Проверяются при каждом обращении. */
    private final Set<BlockPos> gates = new LinkedHashSet<>();
    @Nullable private BlockPos active;
    @Nullable private BlockPos lastActive;
    @Nullable private BlockPos catalyst;

    // синхронизируется для очков
    private int sourceGates, outputGates, pending;
    private boolean loopBusy, catalystFound, hasBelt;
    private int activeMode = -1;

    public ProcessDispatcherBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.PROCESS_DISPATCHER_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this)
                .onlyInsertWhen(movement -> {
                    if (movement == Direction.UP) return false;
                    Out o = output(); // с выхода не принимаем
                    return o == null || !o.pos().equals(worldPosition.relative(movement.getOpposite()));
                })
                .setInsertionHandler(this::returned));
    }

    void register(BlockPos gate) {
        if (gates.add(gate.immutable())) setChanged();
    }

    public void setCatalyst(@Nullable BlockPos pos) {
        catalyst = pos;
        setChanged();
    }

    @Nullable
    private ProcessGateBlockEntity gate(BlockPos p) {
        if (level == null || !level.isLoaded(p)) return null;
        if (level.getBlockEntity(p) instanceof ProcessGateBlockEntity g && worldPosition.equals(g.getDispatcher())
                && p.closerThan(worldPosition, ProcessGateBlockEntity.range())) return g;
        return null;
    }

    /** Живые шлюзы; пропавшие и перепривязанные выбрасываем. */
    private List<ProcessGateBlockEntity> liveGates() {
        List<ProcessGateBlockEntity> out = new ArrayList<>();
        for (Iterator<BlockPos> it = gates.iterator(); it.hasNext(); ) {
            BlockPos p = it.next();
            if (level != null && !level.isLoaded(p)) continue; // чанк выгружен — пусть останется в списке
            ProcessGateBlockEntity g = gate(p);
            if (g == null) { it.remove(); setChanged(); continue; }
            out.add(g);
        }
        return out;
    }

    @Nullable
    private ElectricCatalystBlockEntity catalystBE() {
        if (catalyst == null || level == null || !level.isLoaded(catalyst)) return null;
        return level.getBlockEntity(catalyst) instanceof ElectricCatalystBlockEntity c ? c : null;
    }

    /** Куда выпускать: лента, сторона вставки и грань диспетчера для шторки (null — лента под ним). */
    record Out(BlockPos pos, Direction side, @Nullable Direction face) {}

    /**
     * Куда выпускать, по порядку:
     * <ol>
     *   <li>лента прямо под диспетчером (он стоит на ней, как механизмы Create);</li>
     *   <li>лента вплотную сбоку или <b>на блок ниже сбоку</b> (как ленточная воронка Create), которая едет от него;</li>
     *   <li>такая же лента, идущая мимо, — кладём сбоку.</li>
     * </ol>
     * Ленты, которые едут к диспетчеру, — возвраты.
     */
    @Nullable
    Out output() {
        if (level == null) return null;
        BlockPos below = worldPosition.below();
        if (beltKindAt(below, Direction.DOWN) == BELT_UNDER) return new Out(below, Direction.DOWN, null);
        Out past = null;
        for (int lower = 0; lower <= 1; lower++)
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos p = worldPosition.relative(d).below(lower);
                int k = beltKindAt(p, d);
                if (k == BELT_AWAY) return new Out(p, d, d);
                if (k == BELT_PAST && past == null) past = new Out(p, d, d);
            }
        return past;
    }

    static final int BELT_NONE = 0, BELT_INTO = 1, BELT_AWAY = 2, BELT_PAST = 3, BELT_STOPPED = 4, BELT_UNDER = 5;
    /** Для очков: 4 стороны на своём уровне, снизу и 4 стороны на блок ниже. */
    private static final int SIDES = 9;

    private BlockPos sidePos(int i) {
        if (i < 4) return worldPosition.relative(Direction.from2DDataValue(i));
        if (i == 4) return worldPosition.below();
        return worldPosition.relative(Direction.from2DDataValue(i - 5)).below();
    }

    private static Direction sideDir(int i) {
        return i == 4 ? Direction.DOWN : Direction.from2DDataValue(i < 4 ? i : i - 5);
    }

    /** Что за лента в этой точке, если смотреть от диспетчера в сторону d. */
    private int beltKindAt(BlockPos p, Direction d) {
        if (level == null || !(level.getBlockEntity(p) instanceof BeltBlockEntity b)) return BELT_NONE;
        if (b.getDirectionAwareBeltMovementSpeed() == 0) return BELT_STOPPED;
        if (d == Direction.DOWN) return BELT_UNDER;
        Direction move = b.getMovementFacing();
        if (move == d) return BELT_AWAY;
        if (move == d.getOpposite()) return BELT_INTO;
        return BELT_PAST;
    }

    /** Для очков: ленты вокруг (синхронизируется). */
    private final int[] belts = new int[SIDES];

    private static int processable(ProcessGateBlockEntity g, net.minecraft.world.level.Level level) {
        CatalystProcess m = g.mode().process;
        IItemHandler h = g.inventory();
        if (m == null || h == null) return 0;
        int n = 0;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (!s.isEmpty() && m.canProcess(s, level)) n += s.getCount();
        }
        return n;
    }

    /** Есть ли что-нибудь на лентах: от выхода по цепочке, пока ленты не кончатся или не вернутся в диспетчер. */
    private boolean loopHasItems(@Nullable Out out) {
        if (level == null || out == null) return false;
        BlockPos pos = out.pos();
        Set<BlockPos> seen = new HashSet<>();
        for (int i = 0; i < MAX_BELTS; i++) {
            BeltBlockEntity belt = BeltHelper.getControllerBE(level, pos);
            if (belt == null || !seen.add(belt.getBlockPos())) return false;
            if (!belt.getInventory().getTransportedItems().isEmpty()) return true;
            float speed = belt.getDirectionAwareBeltMovementSpeed();
            if (speed == 0) return false;
            pos = BeltHelper.getPositionForOffset(belt, speed > 0 ? belt.beltLength : -1);
            if (pos.equals(worldPosition)) return false;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level != null && level.isClientSide) {
            flaps.tick();
            return;
        }
        if (level == null) return;

        Out out = output();
        boolean busy = loopHasItems(out) || lowerReturnsBusy();
        ProcessGateBlockEntity act = active == null ? null : gate(active);
        if (act != null && act.mode().process == null) act = null; // шлюз переключили в «выход»
        if (act == null || (processable(act, level) == 0 && !busy)) {
            if (!busy) act = pickNext();
        }
        active = act == null ? null : act.getBlockPos();
        CatalystProcess mode = act == null ? CatalystProcess.NONE : act.mode().process;

        ElectricCatalystBlockEntity cat = catalystBE();
        if (cat != null) cat.dispatch(mode);

        if (out != null && out.face() == null) collectBelow();
        collectLowerReturns(out);

        // партия — только когда катализатор уже работает в нужном режиме
        if (act != null && out != null && cat != null && cat.getBlockState().getValue(ElectricCatalystBlock.PROCESS) == mode)
            feed(act, out);

        if (level.getGameTime() % 10 == 0) {
            List<ProcessGateBlockEntity> live = liveGates();
            int src = 0, outs = 0;
            for (ProcessGateBlockEntity g : live) if (g.mode() == GateMode.OUTPUT) outs++; else src++;
            int pend = act == null ? 0 : processable(act, level);
            int am = act == null ? -1 : act.mode().ordinal();
            boolean changed = src != sourceGates || outs != outputGates || pend != pending || busy != loopBusy
                    || (cat != null) != catalystFound || (out != null) != hasBelt || am != activeMode;
            sourceGates = src;
            outputGates = outs;
            pending = pend;
            loopBusy = busy;
            catalystFound = cat != null;
            hasBelt = out != null;
            activeMode = am;
            for (int i = 0; i < SIDES; i++) {
                int k = beltKindAt(sidePos(i), sideDir(i));
                if (k != belts[i]) changed = true;
                belts[i] = k;
            }
            if (changed) sendData();
        }
    }

    /**
     * Диспетчер над лентой: снимаем с ленты под собой то, что уже обработано, и кладём в выходные шлюзы.
     * Недоделанное просто едет дальше — на следующий круг петли.
     */
    private void collectBelow() {
        if (level == null) return;
        var handler = BlockEntityBehaviour.get(level, worldPosition.below(),
                com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) return;
        handler.handleCenteredProcessingOnAllItems(.5f, t -> {
            if (!ProcessSorterBlockEntity.isDone(t))
                return TransportedResult.doNothing();
            ItemStack rest = toOutputs(t.stack, false);
            if (rest.getCount() == t.stack.getCount())
                return TransportedResult.doNothing();
            beltFlap(true);
            if (rest.isEmpty())
                return TransportedResult.removeItem();
            TransportedItemStack left = t.copy();
            left.stack = rest;
            return TransportedResult.convertTo(left);
        });
    }

    /** Шторка над лентой: въезд (inward) — со стороны, откуда едет лента, выезд — куда. */
    private void beltFlap(boolean inward) {
        if (level == null || !(level.getBlockEntity(worldPosition.below()) instanceof BeltBlockEntity b)) return;
        Direction move = b.getMovementFacing();
        flap(inward ? move.getOpposite() : move, inward);
    }

    /** В выходные шлюзы; возвращает то, что не влезло. */
    private ItemStack toOutputs(ItemStack stack, boolean simulate) {
        ItemStack rest = stack.copy();
        for (ProcessGateBlockEntity g : liveGates()) {
            if (g.mode() != GateMode.OUTPUT) continue;
            IItemHandler inv = g.inventory();
            if (inv == null) continue;
            int before = rest.getCount();
            rest = ItemHandlerHelper.insertItemStacked(inv, rest, simulate);
            if (!simulate && rest.getCount() < before) g.flap(true);
            if (rest.isEmpty()) break;
        }
        return rest;
    }

    /** Следующий по кругу шлюз-источник, в котором есть что обработать. */
    @Nullable
    private ProcessGateBlockEntity pickNext() {
        List<ProcessGateBlockEntity> src = new ArrayList<>();
        for (ProcessGateBlockEntity g : liveGates()) if (g.mode().process != null) src.add(g);
        if (src.isEmpty()) return null;
        src.sort(Comparator.comparingLong(g -> g.getBlockPos().asLong()));
        int start = 0;
        if (lastActive != null)
            for (int i = 0; i < src.size(); i++) if (src.get(i).getBlockPos().equals(lastActive)) start = i + 1;
        for (int k = 0; k < src.size(); k++) {
            ProcessGateBlockEntity g = src.get((start + k) % src.size());
            if (processable(g, level) > 0) {
                lastActive = g.getBlockPos();
                return g;
            }
        }
        return null;
    }

    private void feed(ProcessGateBlockEntity g, Out out) {
        IItemHandler h = g.inventory();
        CatalystProcess m = g.mode().process;
        if (h == null || m == null) return;
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty() || !m.canProcess(s, level)) continue;
            ItemStack batch = h.extractItem(i, BATCH, true);
            if (batch.isEmpty()) continue;
            ItemStack rest = toBelt(new TransportedItemStack(batch), out, true);
            int fits = batch.getCount() - rest.getCount();
            if (fits <= 0) return; // лента занята
            toBelt(new TransportedItemStack(h.extractItem(i, fits, false)), out, false);
            g.flap(false);   // выезжает из сундука
            if (out.face() == null) beltFlap(false);
            else flap(out.face(), false); // и из диспетчера на ленту
            return;
        }
    }

    private ItemStack toBelt(TransportedItemStack t, @Nullable Out out, boolean simulate) {
        if (level == null || out == null) return t.stack;
        DirectBeltInputBehaviour belt = BlockEntityBehaviour.get(level, out.pos(), DirectBeltInputBehaviour.TYPE);
        if (belt == null || !belt.canInsertFromSide(out.side())) return t.stack;
        return belt.handleInsertion(t.copy(), out.side(), simulate);
    }

    /** Возвраты на блок ниже (лента упирается в блок под диспетчером): есть ли на их концах предметы. */
    private boolean lowerReturnsBusy() {
        if (level == null) return false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = worldPosition.relative(d).below();
            if (beltKindAt(p, d) == BELT_INTO && level.getBlockEntity(p) instanceof BeltBlockEntity b
                    && !b.getInventory().getTransportedItems().isEmpty()) return true;
        }
        return false;
    }

    /**
     * Возвраты на блок ниже: лента упирается в блок под диспетчером, предметы стоят у её конца —
     * диспетчер забирает их «шторкой», как ленточная воронка Create: готовое — в выход, сырое — снова на выпуск.
     */
    private void collectLowerReturns(@Nullable Out out) {
        if (level == null) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos p = worldPosition.relative(d).below();
            if (beltKindAt(p, d) != BELT_INTO) continue;
            var handler = BlockEntityBehaviour.get(level, p,
                    com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TYPE);
            if (handler == null) continue;
            handler.handleCenteredProcessingOnAllItems(.5f, t -> {
                ItemStack rest;
                if (ProcessSorterBlockEntity.isDone(t)) {
                    rest = toOutputs(t.stack, false);
                } else {
                    if (out == null || out.pos().equals(p)) return TransportedResult.doNothing();
                    rest = toBelt(t, out, false);
                    if (rest.getCount() < t.stack.getCount()) {
                        if (out.face() == null) beltFlap(false);
                        else flap(out.face(), false);
                    }
                }
                if (rest.getCount() == t.stack.getCount()) return TransportedResult.doNothing();
                flap(d, true);
                if (rest.isEmpty()) return TransportedResult.removeItem();
                TransportedItemStack left = t.copy();
                left.stack = rest;
                return TransportedResult.convertTo(left);
            });
        }
    }

    /** Лента пришла в диспетчер: готовое — в выходные шлюзы, сырое — снова на ленту. */
    private ItemStack returned(TransportedItemStack t, Direction movement, boolean simulate) {
        if (level == null) return t.stack;
        Out out = output();
        ItemStack rest;
        if (!ProcessSorterBlockEntity.isDone(t)) {
            rest = toBelt(t, out, simulate);
            if (!simulate && rest.getCount() < t.stack.getCount() && out != null && out.face() != null) flap(out.face(), false);
        } else {
            rest = toOutputs(t.stack, simulate);
        }
        if (!simulate && rest.getCount() < t.stack.getCount()) flap(movement.getOpposite(), true); // въехало в диспетчер
        return rest;
    }

    // ---------- очки ----------
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(1, Component.translatable("create_em.dispatcher.gates", sourceGates, outputGates).withStyle(ChatFormatting.GRAY)));
        if (activeMode >= 0)
            tooltip.add(line(1, Component.translatable("create_em.dispatcher.active")
                    .append(Component.translatable(GateMode.values()[activeMode].translationKey()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" · " + pending)).withStyle(ChatFormatting.GRAY)));
        String status = !catalystFound ? "create_em.dispatcher.no_catalyst"
                : !hasBelt ? "create_em.dispatcher.no_belt"
                : outputGates == 0 ? "create_em.dispatcher.no_output"
                : activeMode >= 0 ? (loopBusy ? "create_em.dispatcher.working" : "create_em.dispatcher.starting")
                : loopBusy ? "create_em.dispatcher.draining" : "create_em.dispatcher.idle";
        var beltLine = Component.translatable("create_em.dispatcher.belts").withStyle(ChatFormatting.GRAY);
        boolean any = false;
        String[] sideKeys = {"south", "west", "north", "east", "down", "south_low", "west_low", "north_low", "east_low"};
        for (int i = 0; i < SIDES; i++) {
            if (belts[i] == BELT_NONE) continue;
            if (any) beltLine.append(Component.literal(", "));
            beltLine.append(Component.translatable("create_em.dispatcher.side." + sideKeys[i]))
                    .append(Component.literal(" — "))
                    .append(Component.translatable("create_em.dispatcher.belt." + belts[i])
                            .withStyle(belts[i] == BELT_AWAY || belts[i] == BELT_PAST || belts[i] == BELT_UNDER ? ChatFormatting.GREEN
                                    : belts[i] == BELT_INTO ? ChatFormatting.AQUA : ChatFormatting.RED));
            any = true;
        }
        if (!any) beltLine.append(Component.translatable("create_em.dispatcher.belt.0").withStyle(ChatFormatting.RED));
        tooltip.add(line(1, beltLine));
        boolean good = catalystFound && hasBelt && outputGates > 0;
        tooltip.add(line(1, Component.translatable(status).withStyle(good ? ChatFormatting.GREEN : ChatFormatting.GOLD)));
        return true;
    }

    // ---------- NBT ----------
    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.write(tag, reg, clientPacket);
        if (catalyst != null) tag.putLong("Catalyst", catalyst.asLong());
        if (active != null) tag.putLong("Active", active.asLong());
        if (lastActive != null) tag.putLong("LastActive", lastActive.asLong());
        long[] g = new long[gates.size()];
        int i = 0;
        for (BlockPos p : gates) g[i++] = p.asLong();
        tag.putLongArray("Gates", g);
        if (clientPacket) {
            tag.putInt("Src", sourceGates);
            tag.putInt("Outs", outputGates);
            tag.putInt("Pending", pending);
            tag.putBoolean("Busy", loopBusy);
            tag.putBoolean("CatOk", catalystFound);
            tag.putBoolean("Belt", hasBelt);
            tag.putInt("ActiveMode", activeMode);
            tag.putIntArray("Belts", belts);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider reg, boolean clientPacket) {
        super.read(tag, reg, clientPacket);
        catalyst = tag.contains("Catalyst") ? BlockPos.of(tag.getLong("Catalyst")) : null;
        active = tag.contains("Active") ? BlockPos.of(tag.getLong("Active")) : null;
        lastActive = tag.contains("LastActive") ? BlockPos.of(tag.getLong("LastActive")) : null;
        gates.clear();
        for (long l : tag.getLongArray("Gates")) gates.add(BlockPos.of(l));
        if (clientPacket) {
            sourceGates = tag.getInt("Src");
            outputGates = tag.getInt("Outs");
            pending = tag.getInt("Pending");
            loopBusy = tag.getBoolean("Busy");
            catalystFound = tag.getBoolean("CatOk");
            hasBelt = tag.getBoolean("Belt");
            activeMode = tag.contains("ActiveMode") ? tag.getInt("ActiveMode") : -1;
            int[] b = tag.getIntArray("Belts");
            for (int i = 0; i < belts.length && i < b.length; i++) belts[i] = b[i];
        }
    }
}
