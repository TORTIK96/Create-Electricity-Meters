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
import net.minecraft.world.phys.Vec3;
import tor_tik96.create_em.line.LineLinks;
import tor_tik96.create_em.line.LineNode;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation;
import tor_tik96.create_em.meter.client.ClientAccess;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.registry.EMRegistry;
import tor_tik96.create_em.seal.SealRegistry;
import tor_tik96.create_em.billing.BillingData;
import tor_tik96.create_em.billing.BillingStage;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

import static net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING;

/**
 * Конец сервисной линии. Энергия приходит ТОЛЬКО по проводу линии от трансформатора
 * (клемма сверху) в небольшой буфер; каждый тик счётчик сам толкает её в выходы
 * (сзади и снизу) и учитывает только то, что потребитель реально принял.
 * Обычной FE счётчик не принимает и не отдаёт ни с какой стороны.
 */
public class EnergyMeterBlockEntity extends BlockEntity implements LineNode, IHaveGoggleInformation, IHaveHoveringInformation {
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

    private final LineLinks links = new LineLinks(this);
    @Nullable private UUID owner;

    // ---- пломба (правда — в SealRegistry, здесь кеш) ----
    @Nullable private UUID sealId;
    @Nullable private UUID supplier;
    private String supplierName = "";   // синхронизируется; пусто = пломбы нет
    private int sealCheckTimer;

    // ---- лимит ----
    private int limit;                  // 0 = по умолчанию (максимум уровня)

    // ---- расчёты (правда — в BillingData, здесь кеш для подсказок; синхронизируется) ----
    private String ownerName = "";
    private long acctSinceFe, acctPaidAt = -1, acctBalance;
    private BillingStage stage = BillingStage.NORMAL;
    /** Андезитовый счётчик при отключении за долг «выбивает»: сам не включится, нужно взвести. */
    private boolean tripped;
    /** Поставщик отключил счётчик из панели / ограничил мощность (0 — без ограничения). */
    private boolean remoteOff;
    private int powerCap;

    public EnergyMeterBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.METER_BE.get(), pos, state);
    }

    /** Приём энергии с линии: сколько влезло в буфер. */
    public int acceptFromLine(int amount) {
        int accepted = Math.max(0, Math.min(amount, getTier().bufferSize() - buffer));
        if (accepted > 0) { buffer += accepted; setChanged(); }
        return accepted;
    }

    // ---------------- LineNode ----------------
    @Override public LineLinks getLinks() { return links; }
    @Override public Vec3 getWireAnchor() { return new Vec3(0.5, 15.1 / 16, 0.5); } // клемма на крышке
    @Override public int getMaxLinks() { return 1; }
    @Override public boolean isTerminal() { return true; }
    @Override public @Nullable UUID getOwner() { return owner; }
    @Override public void setOwner(@Nullable UUID owner) { this.owner = owner; setChanged(); }

    public MeterTier getTier() {
        return getBlockState().getBlock() instanceof EnergyMeterBlock b ? b.getTier() : MeterTier.ANDESITE;
    }

    // ---------------- сервер ----------------
    void serverTick() {
        if (backCache == null) initCaches();

        if (--sealCheckTimer <= 0) { sealCheckTimer = 40; validateSeal(); }

        int budget = getLimit();                     // лимит счётчика на тик
        if (powerCap > 0) budget = Math.min(budget, powerCap);
        if (tripped || remoteOff || stage == BillingStage.CUT) budget = 0;
        else if (stage == BillingStage.LIMITED) budget = budget * Config.LIMITED_PERCENT.get() / 100;
        int sent = push(backCache, budget);
        sent += push(bottomCache, budget - sent);

        totalFe += sent;
        windowFe += sent;
        if (sent > 0) setChanged();

        if (++windowTicks >= WINDOW) {
            boolean billingChanged = updateBilling(windowFe);
            float newFlow = windowFe / (float) WINDOW;
            // пока идёт энергия — синхронизируем раз в секунду, чтобы показания в подсказке шли вживую
            boolean changed = billingChanged || windowFe > 0 || newFlow != flowPerTick || syncedLimit != getLimit();
            syncedLimit = getLimit();
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

    // ---------------- расчёты ----------------
    /** Раз в секунду: сообщить потребление на лицевой счёт и забрать оттуда баланс и стадию. */
    private boolean updateBilling(long fe) {
        if (level == null || level.getServer() == null || supplier == null || owner == null) {
            boolean changed = stage != BillingStage.NORMAL || remoteOff || powerCap != 0;
            stage = BillingStage.NORMAL;
            remoteOff = false;
            powerCap = 0;
            return changed;
        }
        BillingData bd = BillingData.get(level.getServer());
        bd.report(supplier, owner, ownerName, fe);
        BillingData.Account a = bd.account(supplier, owner);
        BillingStage newStage = a.stage(level.getGameTime());
        boolean changed = newStage != stage || a.balance != acctBalance || a.paidAt != acctPaidAt
                || a.disabled != remoteOff || a.powerCap != powerCap;
        remoteOff = a.disabled;
        powerCap = a.powerCap;
        stage = newStage;
        acctSinceFe = a.feSincePayment;
        acctPaidAt = a.paidAt;
        acctBalance = a.balance;
        if (stage == BillingStage.CUT && getTier() == MeterTier.ANDESITE && !tripped) {
            tripped = true;
            changed = true;
            level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_CLOSE,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.6f);
        }
        return changed;
    }

    public boolean isTripped() { return tripped; }
    public BillingStage getStage() { return stage; }

    /** Можно ли взвести расцепитель: долг закрыт (не CUT), взводит поставщик или хозяин счётчика. */
    public boolean canRearm(Player p) {
        if (!tripped || stage == BillingStage.CUT) return false;
        return p.isCreative() || isSupplier(p) || (owner != null && owner.equals(p.getUUID()));
    }

    public boolean rearm(Player p) {
        if (!canRearm(p)) return false;
        tripped = false;
        if (level != null) level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN,
                net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1.2f);
        sync();
        return true;
    }

    public void setOwnerName(String name) { ownerName = name; setChanged(); }

    // ---------------- лимит ----------------
    /** Текущий лимит, FE/t, всегда в коридоре уровня (конфиг мог поменяться). Только сервер. */
    public int getLimit() {
        MeterTier t = getTier();
        int v = limit <= 0 ? t.maxLimit() : limit;
        return Math.max(t.minLimit(), Math.min(t.maxLimit(), v));
    }

    public void setLimit(int value) {
        MeterTier t = getTier();
        limit = Math.max(t.minLimit(), Math.min(t.maxLimit(), value));
        syncedLimit = limit;
        sync();
    }

    /** Опломбированный счётчик настраивает только поставщик; без пломбы — тот, кто поставил. */
    public boolean canEditLimit(Player player) {
        if (player.isCreative()) return true;
        if (isSealed()) return isSupplier(player);
        return owner == null || owner.equals(player.getUUID());
    }

    // ---------------- пломба ----------------
    public boolean isSealed() {
        return level != null && level.isClientSide ? !supplierName.isEmpty() : sealId != null;
    }

    /** Поставщик по действующей пломбе (сервер) или null. Трансформатор кормит счётчик, только если это его владелец. */
    @Nullable public UUID getSealSupplier() { return supplier; }
    public String getSupplierName() { return supplierName; }
    public boolean isSupplier(Player p) { return supplier != null && supplier.equals(p.getUUID()); }

    public void installSeal(Player player) {
        if (level == null || level.getServer() == null) return;
        SealRegistry.Seal seal = SealRegistry.get(level.getServer())
                .install(player.getUUID(), player.getGameProfile().getName(), level, worldPosition);
        sealId = seal.id();
        supplier = seal.supplier();
        supplierName = seal.supplierName();
        applySealState();
    }

    public void revokeSeal(String by) {
        if (sealId != null && level != null && level.getServer() != null)
            SealRegistry.get(level.getServer()).revoke(sealId, level.getServer(), by);
        clearSeal();
    }

    /** Срыв пломбы: by — имя игрока или пусто, если причина неизвестна (взрыв, контрапция...). */
    public void breakSeal(String by) {
        if (sealId != null && level != null && level.getServer() != null)
            SealRegistry.get(level.getServer()).breakSeal(sealId, level.getServer(), by);
        clearSeal();
    }

    /** Пломба могла пропасть, пока счётчик был выгружен, или её сняли через реестр. */
    private void validateSeal() {
        if (sealId == null || level == null || level.getServer() == null) return;
        if (SealRegistry.get(level.getServer()).getValid(sealId, level, worldPosition) == null) clearSeal();
    }

    private void clearSeal() {
        sealId = null;
        supplier = null;
        supplierName = "";
        applySealState();
    }

    private void applySealState() {
        setChanged();
        if (level == null) return;
        BlockState st = getBlockState();
        if (st.hasProperty(EnergyMeterBlock.SEALED) && st.getValue(EnergyMeterBlock.SEALED) != isSealed())
            level.setBlock(worldPosition, st.setValue(EnergyMeterBlock.SEALED, isSealed()), Block.UPDATE_ALL);
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
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
    /** С последней оплаты: по лицевому счёту, если счётчик опломбирован; иначе — по своей отметке. */
    public long getSincePaymentFe() { return isSealed() ? acctSinceFe : totalFe - paidFe; }
    private long lastPaidAt() { return isSealed() ? acctPaidAt : paidAtTick; }
    public float getFlowPerTick() { return flowPerTick; }

    /**
     * Отметить оплату: «с последней оплаты» начинает считаться заново.
     * Вызывается расчётной системой; пока её нет — из креатива (Shift-ПКМ пустой рукой).
     */
    public void markPaid() {
        if (supplier != null && owner != null && level != null && level.getServer() != null) {
            BillingData bd = BillingData.get(level.getServer());
            bd.markPaid(bd.account(supplier, owner), level.getGameTime());
            updateBilling(0);
        }
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
        addStageLines(tooltip, 0);
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

        tooltip.add(line(0, Component.translatable("create_em.tooltip.seal").withStyle(LABEL)));
        if (!supplierName.isEmpty())
            tooltip.add(line(1, Component.translatable("create_em.tooltip.supplier").withStyle(LABEL)
                    .append(Component.literal(supplierName).withStyle(VALUE))));
        else
            tooltip.add(line(1, Component.translatable("create_em.tooltip.no_seal").withStyle(ChatFormatting.RED)));
        if (!supplierName.isEmpty()) {
            tooltip.add(line(1, Component.translatable("create_em.tooltip.balance").withStyle(LABEL)
                    .append(Component.translatable("create_em.unit.credits", acctBalance).withStyle(acctBalance < 0 ? ChatFormatting.RED : VALUE))));
            tooltip.add(line(1, Component.translatable("create_em.tooltip.stage").withStyle(LABEL)
                    .append(Component.translatable(stage.key()).withStyle(stageColor()))));
        }
        if (tripped) tooltip.add(line(1, Component.translatable("create_em.tooltip.tripped").withStyle(ChatFormatting.RED)));
        if (remoteOff) tooltip.add(line(1, Component.translatable("create_em.tooltip.remote_off").withStyle(ChatFormatting.RED)));
        if (powerCap > 0) tooltip.add(line(1, Component.translatable("create_em.tooltip.power_cap").withStyle(LABEL)
                .append(Component.literal(String.format("%,d FE/t", powerCap)).withStyle(ChatFormatting.GOLD))));

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

    private ChatFormatting stageColor() {
        return switch (stage) { case NORMAL -> ChatFormatting.GREEN; case WARNING, LIMITED -> ChatFormatting.GOLD; case CUT -> ChatFormatting.RED; };
    }

    /** Без очков показываем только проблему: долг или сработавший расцепитель. */
    private void addStageLines(List<Component> tooltip, int indent) {
        if (stage != BillingStage.NORMAL)
            tooltip.add(line(indent, Component.translatable(stage.key()).withStyle(stageColor())));
        if (tripped)
            tooltip.add(line(indent, Component.translatable("create_em.tooltip.tripped").withStyle(ChatFormatting.RED)));
        if (remoteOff)
            tooltip.add(line(indent, Component.translatable("create_em.tooltip.remote_off").withStyle(ChatFormatting.RED)));
    }

    public static MutableComponent line(int indent, Component c) {
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
        long paidAtTick = lastPaidAt();
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
        links.save(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        if (sealId != null) tag.putUUID("Seal", sealId);
        if (supplier != null) tag.putUUID("Supplier", supplier);
        tag.putString("SupplierName", supplierName);
        tag.putInt("UserLimit", limit);
        tag.putString("OwnerName", ownerName);
        tag.putBoolean("Tripped", tripped);
        tag.putLong("AcctSince", acctSinceFe);
        tag.putLong("AcctPaidAt", acctPaidAt);
        tag.putLong("AcctBalance", acctBalance);
        tag.putInt("Stage", stage.ordinal());
        tag.putBoolean("RemoteOff", remoteOff);
        tag.putInt("PowerCap", powerCap);
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
        links.load(tag);
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
        sealId = tag.hasUUID("Seal") ? tag.getUUID("Seal") : null;
        supplier = tag.hasUUID("Supplier") ? tag.getUUID("Supplier") : null;
        supplierName = tag.getString("SupplierName");
        if (tag.contains("UserLimit")) limit = tag.getInt("UserLimit");
        if (tag.contains("OwnerName")) ownerName = tag.getString("OwnerName");
        tripped = tag.getBoolean("Tripped");
        acctSinceFe = tag.getLong("AcctSince");
        acctPaidAt = tag.contains("AcctPaidAt") ? tag.getLong("AcctPaidAt") : -1;
        acctBalance = tag.getLong("AcctBalance");
        stage = BillingStage.values()[Math.max(0, Math.min(3, tag.getInt("Stage")))];
        remoteOff = tag.getBoolean("RemoteOff");
        powerCap = tag.getInt("PowerCap");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("TotalFe", totalFe);
        tag.putFloat("Flow", flowPerTick);
        tag.putLong("PaidFe", paidFe);
        tag.putLong("PaidAt", paidAtTick);
        tag.putInt("Limit", getLimit());
        tag.putString("SupplierName", supplierName);
        tag.putBoolean("Tripped", tripped);
        tag.putLong("AcctSince", acctSinceFe);
        tag.putLong("AcctPaidAt", acctPaidAt);
        tag.putLong("AcctBalance", acctBalance);
        tag.putInt("Stage", stage.ordinal());
        tag.putBoolean("RemoteOff", remoteOff);
        tag.putInt("PowerCap", powerCap);
        links.save(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
