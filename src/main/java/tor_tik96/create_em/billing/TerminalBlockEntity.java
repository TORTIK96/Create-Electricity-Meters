package tor_tik96.create_em.billing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.UUID;

/**
 * Используется только слот 1 — оплата. Слоты 0 и 2..10 остались от старой кассы:
 * при загрузке их содержимое переносится в хранилище поставщика.
 */
public class TerminalBlockEntity extends BlockEntity {
    public static final int SLOT_SAMPLE = 0, SLOT_PAY = 1, CASH_FIRST = 2, CASH_COUNT = 9, SIZE = 11;

    private final SimpleContainer container = new SimpleContainer(SIZE) {
        @Override public void setChanged() { super.setChanged(); TerminalBlockEntity.this.setChanged(); }
    };
    @Nullable private UUID owner;
    private String ownerName = "";

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.TERMINAL_BE.get(), pos, state);
    }

    public SimpleContainer getContainer() { return container; }

    /** Перенести содержимое старой кассы и образец валюты в хранилище поставщика. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide || owner == null || level.getServer() == null) return;
        var bd = tor_tik96.create_em.billing.BillingData.get(level.getServer());
        boolean moved = false;
        for (int i = 0; i < SIZE; i++) {
            if (i == SLOT_PAY || container.getItem(i).isEmpty()) continue;
            bd.addToVault(owner, container.removeItemNoUpdate(i));
            moved = true;
        }
        if (moved) setChanged();
    }
    @Nullable public UUID getOwner() { return owner; }
    public String getOwnerName() { return ownerName; }

    public void setOwner(UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        ContainerHelper.saveAllItems(tag, container.getItems(), reg); // со слотами, чтобы образец остался в слоте 0
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName", ownerName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        container.getItems().clear();
        ContainerHelper.loadAllItems(tag, container.getItems(), reg);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
    }
}
