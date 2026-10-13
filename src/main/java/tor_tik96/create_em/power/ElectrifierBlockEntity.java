package tor_tik96.create_em.power;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.Create_em;
import tor_tik96.create_em.EMFormat;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;

/**
 * Энергию не копит: всё, что пришло, разряжается в воздух. Разряд — раз в {@link #PERIOD} тиков
 * (чаще всё равно нельзя: после удара у существ неуязвимость на полсекунды), урон зависит от того,
 * сколько FE пришло за это время. Мощный импульс (разряд конденсатора) бьёт сразу, не дожидаясь периода.
 */
public class ElectrifierBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final ResourceKey<DamageType> ELECTROCUTION = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Create_em.MODID, "electrocution"));
    private static final int PERIOD = 10;
    private static final int FLASH_TICKS = 3;

    private ScrollOptionBehaviour<ElectrifierTarget> target;
    private ScrollValueBehaviour radius;
    private long pending;
    private int sinceZap = PERIOD;
    private int flash;
    private float lastDamage;
    private int lastFe;

    private final EnergyPort input = EnergyPort.input(this::receive, () -> 0, () -> 0);

    public ElectrifierBlockEntity(BlockPos pos, BlockState state) {
        super(EMRegistry.ELECTRIFIER_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        target = new ScrollOptionBehaviour<>(ElectrifierTarget.class, Component.translatable("create_em.electrifier.target"), this,
                new CenteredSideValueBoxTransform((s, d) -> d.getAxis().isHorizontal())) {
            /** Старое сохранение могло положить сюда значение другой рамки (например, 100) — не даём выйти за список режимов. */
            @Override
            public void read(net.minecraft.nbt.CompoundTag nbt, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
                super.read(nbt, registries, clientPacket);
                if (value < 0 || value > max) value = 0;
            }

            @Override
            public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
                return new ValueSettingsBoard(label, max, 1, List.of(Component.translatable("create_em.electrifier.target_row")),
                        new ValueSettingsFormatter.ScrollOptionSettingsFormatter(ElectrifierTarget.values()));
            }
        };
        behaviours.add(target);

        // радиус — рамка сверху и снизу (электризатор обычно в полу или потолке)
        radius = new ExtraScrollValueBehaviour(Component.translatable("create_em.electrifier.radius"), this,
                new CenteredSideValueBoxTransform((s, d) -> d.getAxis().isVertical()), "Radius") {
            @Override
            public ValueSettingsBoard createBoard(Player player, BlockHitResult hit) {
                return new ValueSettingsBoard(label, max, 1, List.of(Component.translatable("create_em.electrifier.radius_row")),
                        new ValueSettingsFormatter(v -> Component.translatable("create_em.electrifier.radius_value", v.value())));
            }
        }.between(1, Config.safe(Config.ELECTRIFIER_RADIUS, 8));
        radius.value = 3;
        behaviours.add(radius);
    }

    @Nullable
    public IEnergyStorage port(@Nullable Direction side) {
        return input;
    }

    private int receive(int amount, boolean simulate) {
        int max = Config.safe(Config.ELECTRIFIER_MAX_INPUT, 10_000_000);
        int accepted = (int) Math.max(0, Math.min(amount, max - pending));
        if (!simulate && accepted > 0) pending += accepted;
        return accepted;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel sl)) return;
        if (sinceZap < PERIOD) sinceZap++;

        int fePerDmg = Config.safe(Config.ELECTRIFIER_FE_PER_DAMAGE, 200);
        boolean strong = pending >= (long) fePerDmg * 10; // импульс на 5 сердец и больше — бьём сразу
        if (pending > 0 && (sinceZap >= PERIOD || strong)) zap(sl, fePerDmg);

        if (flash > 0 && --flash == 0 && getBlockState().getValue(ElectrifierBlock.ZAP))
            level.setBlock(worldPosition, getBlockState().setValue(ElectrifierBlock.ZAP, false), Block.UPDATE_CLIENTS);
    }

    private void zap(ServerLevel sl, int fePerDmg) {
        long fe = pending;
        pending = 0;
        sinceZap = 0;
        float damage = Math.min(Config.safe(Config.ELECTRIFIER_MAX_DAMAGE, 100), fe / (float) fePerDmg);
        lastFe = (int) Math.min(Integer.MAX_VALUE, fe);
        lastDamage = damage;

        int r = Math.max(1, Math.min(radius.getValue(), Config.safe(Config.ELECTRIFIER_RADIUS, 8)));
        AABB area = new AABB(worldPosition).inflate(r);
        int hit = 0;
        if (damage >= 0.5f) {
            DamageSource src = sl.damageSources().source(ELECTROCUTION);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && target.get().matches(e))) {
                if (e.hurt(src, damage)) {
                    hit++;
                    sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(),
                            6, e.getBbWidth() / 2, e.getBbHeight() / 3, e.getBbWidth() / 2, .2);
                }
            }
        }

        // искры вокруг блока: больше энергии — больше искр
        int sparks = (int) Math.min(40, 3 + fe / Math.max(1, fePerDmg * 2L));
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, worldPosition.getX() + .5, worldPosition.getY() + .5,
                worldPosition.getZ() + .5, sparks, .6, .6, .6, .4);
        float vol = Math.min(1.2f, .2f + damage / 20f);
        if (hit > 0 || damage >= 2)
            sl.playSound(null, worldPosition, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, vol, 1.7f);
        else
            sl.playSound(null, worldPosition, SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.BLOCKS, .3f, 1.8f);

        flash = FLASH_TICKS;
        if (!getBlockState().getValue(ElectrifierBlock.ZAP))
            level.setBlock(worldPosition, getBlockState().setValue(ElectrifierBlock.ZAP, true), Block.UPDATE_CLIENTS);
        sendData();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("LastDamage", lastDamage);
        tag.putInt("LastFe", lastFe);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        lastDamage = tag.getFloat("LastDamage");
        lastFe = tag.getInt("LastFe");
    }

    private static MutableComponent line(int indent, Component c) {
        return Component.literal(" ".repeat(4 + indent * 2)).append(c);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(line(0, Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.WHITE)));
        tooltip.add(line(1, Component.translatable("create_em.electrifier.tooltip.target").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(target.get().getTranslationKey()).withStyle(ChatFormatting.AQUA))));
        tooltip.add(line(1, Component.translatable("create_em.electrifier.tooltip.radius").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(radius.getValue())).withStyle(ChatFormatting.AQUA))));
        tooltip.add(line(1, Component.translatable("create_em.electrifier.tooltip.last").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(EMFormat.fe(lastFe) + " → " + String.format("%.1f", lastDamage / 2f) + " ❤")
                        .withStyle(ChatFormatting.GOLD))));
        tooltip.add(line(1, Component.translatable("create_em.electrifier.tooltip.rate").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(EMFormat.fe(Config.safe(Config.ELECTRIFIER_FE_PER_DAMAGE, 200) * 2L) + " = 1 ❤")
                        .withStyle(ChatFormatting.AQUA))));
        return true;
    }
}
