package tor_tik96.create_em.line;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;
import java.util.UUID;

import static tor_tik96.create_em.meter.EnergyMeterBlockEntity.line;

public class LineConnectorBlockEntity extends BlockEntity implements LineNode, IHaveGoggleInformation {
    private final LineLinks links = new LineLinks(this);
    @Nullable private UUID owner;

    public LineConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.LINE_CONNECTOR_BE.get(), pos, state);
    }

    @Override public LineLinks getLinks() { return links; }
    @Override public int getMaxLinks() { return 8; }
    @Override public @Nullable UUID getOwner() { return owner; }
    @Override public void setOwner(@Nullable UUID owner) { this.owner = owner; setChanged(); }

    /** Медная клемма на верхушке изолятора: 7.5 px от опоры. */
    @Override
    public Vec3 getWireAnchor() {
        Direction f = getBlockState().getValue(DirectionalBlock.FACING);
        double off = 7.5 / 16.0 - 0.5; // середина медной клеммы
        return new Vec3(0.5 + f.getStepX() * off, 0.5 + f.getStepY() * off, 0.5 + f.getStepZ() * off);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(1, Component.translatable("create_em.tooltip.line_wires").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(links.size() + " / " + getMaxLinks()).withStyle(ChatFormatting.AQUA))));
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        links.save(tag);
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        links.load(tag);
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag tag = new CompoundTag();
        links.save(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
