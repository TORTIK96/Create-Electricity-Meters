package tor_tik96.create_em.link;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkFrequencySlot;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Передатчик с выставленной силой сигнала.
 *
 * <ul>
 *   <li><b>Обычный режим</b>: есть хоть какой-то вход — передаёт выставленную силу, нет — 0.</li>
 *   <li><b>Селектор</b>: импульс на входе «защёлкивает» передатчик, и он передаёт силу, пока его
 *       не погасит другой селектор той же частоты. Так ряд кнопок работает как переключатель программ:
 *       нажал одну — остальные гаснут. Селектор с силой 0 — кнопка «выключить всё».</li>
 * </ul>
 * Приёмник берёт максимум по частоте, поэтому одновременно горит только один селектор.
 */
public class TunedLinkBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    /** Загруженные передатчики на сервере — чтобы селекторы гасили друг друга. */
    private static final Set<TunedLinkBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
    /** Порядок нажатий внутри одного тика. */
    private static long pressCounter;

    private ScrollValueBehaviour strength;
    private LinkBehaviour link;

    private int lastInput;
    private int transmitted;
    /** Селектор защёлкнут. */
    private boolean latched;
    /** Когда защёлкнут: игровое время × 1024 + счётчик. Новее — главнее. */
    private long latchedAt;

    public TunedLinkBlockEntity(BlockPos pos, BlockState state) {
        super(tor_tik96.create_em.registry.EMRegistry.TUNED_LINK_BE.get(), pos, state);
        setLazyTickRate(10);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        strength = new StrengthBehaviour(this);
        strength.value = 15;
        strength.withCallback(v -> refresh());
        behaviours.add(strength);

        // Частоту создаём сразу, а не в addBehavioursDeferred: тот вызывается только при чтении NBT,
        // а у только что поставленного блока NBT нет — слоты частоты не появились бы.
        link = LinkBehaviour.transmitter(this, ValueBoxTransform.Dual.makeSlots(RedstoneLinkFrequencySlot::new),
                this::getTransmitted);
        behaviours.add(link);
    }

    /** Ползунок силы: подписи на русском вместо «Value». */
    private static class StrengthBehaviour extends ScrollValueBehaviour {
        StrengthBehaviour(SmartBlockEntity be) {
            super(Component.translatable("create_em.tuned_link.strength"), be, new TunedLinkSlots.Strength());
            between(0, 15);
        }

        @Override
        public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
            return new ValueSettingsBoard(label, 15, 5,
                    List.of(Component.translatable("create_em.tuned_link.strength_row")),
                    new ValueSettingsFormatter(ValueSettingsBehaviour.ValueSettings::format));
        }
    }

    public int getTransmitted() {
        return transmitted;
    }

    public boolean selector() {
        return isSelector();
    }

    public ScrollValueBehaviour getStrengthBehaviour() {
        return strength;
    }

    public int getStrength() {
        return strength == null ? 0 : strength.getValue();
    }

    private boolean isSelector() {
        return getBlockState().getValue(TunedLinkBlock.SELECTOR);
    }

    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) LOADED.add(this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        LOADED.remove(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        int input = level.getBestNeighborSignal(worldPosition);
        if (isSelector() && input > 0 && lastInput == 0) press();
        if (input != lastInput) {
            lastInput = input;
            setChanged();
        }
        refresh();
    }

    /** После загрузки чанка мог проснуться старый селектор — гасим его, если есть нажатый позже. */
    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide || !latched || !isSelector()) return;
        for (TunedLinkBlockEntity peer : peers())
            if (peer.latched && peer.latchedAt > latchedAt) {
                unlatch();
                break;
            }
    }

    private void press() {
        latched = true;
        latchedAt = level.getGameTime() * 1024 + (pressCounter++ & 1023);
        for (TunedLinkBlockEntity peer : peers()) peer.unlatch();
        setChanged();
    }

    private void unlatch() {
        if (!latched) return;
        latched = false;
        setChanged();
        refresh();
    }

    /** Другие селекторы этого мира с той же частотой. */
    private List<TunedLinkBlockEntity> peers() {
        List<TunedLinkBlockEntity> out = new ArrayList<>();
        if (link == null) return out;
        for (TunedLinkBlockEntity be : LOADED) {
            if (be == this || be.isRemoved() || be.level != level || be.link == null) continue;
            if (!be.getBlockState().hasProperty(TunedLinkBlock.SELECTOR) || !be.isSelector()) continue;
            if (be.link.getNetworkKey().equals(link.getNetworkKey())) out.add(be);
        }
        return out;
    }

    void onModeChanged(boolean selector) {
        latched = false;
        refresh();
    }

    /** Пересчитать, что передаём, и сообщить сети, если изменилось. */
    private void refresh() {
        if (level == null || level.isClientSide || strength == null) return;
        boolean on = isSelector() ? latched : lastInput > 0;
        int next = on ? strength.getValue() : 0;
        if (next == transmitted) return;
        transmitted = next;
        if (link != null) link.notifySignalChange();
        BlockState state = getBlockState();
        if (state.hasProperty(TunedLinkBlock.POWERED) && state.getValue(TunedLinkBlock.POWERED) != (next > 0))
            level.setBlock(worldPosition, state.setValue(TunedLinkBlock.POWERED, next > 0), Block.UPDATE_CLIENTS);
        setChanged();
        sendData(); // для очков и окошка силы на клиенте
    }

    // ---------- сохранение ----------

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putInt("Input", lastInput);
        tag.putInt("Transmit", transmitted);
        tag.putBoolean("Latched", latched);
        tag.putLong("LatchedAt", latchedAt);
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        lastInput = tag.getInt("Input");
        transmitted = tag.getInt("Transmit");
        latched = tag.getBoolean("Latched");
        latchedAt = tag.getLong("LatchedAt");
    }

    // ---------- очки инженера ----------

    private static MutableComponent line(int indent, Component c) {
        return Component.literal(" ".repeat(4 + indent * 2)).append(c);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        boolean selector = isSelector();
        tooltip.add(line(1, Component.translatable("create_em.tuned_link.tooltip.mode").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(selector ? "create_em.tuned_link.selector" : "create_em.tuned_link.normal")
                        .withStyle(ChatFormatting.AQUA))));
        tooltip.add(line(1, Component.translatable("create_em.tuned_link.tooltip.strength").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(strength.getValue())).withStyle(ChatFormatting.AQUA))));
        tooltip.add(line(1, Component.translatable("create_em.tuned_link.tooltip.sending").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(transmitted))
                        .withStyle(transmitted > 0 ? ChatFormatting.RED : ChatFormatting.DARK_GRAY))));
        return true;
    }
}
