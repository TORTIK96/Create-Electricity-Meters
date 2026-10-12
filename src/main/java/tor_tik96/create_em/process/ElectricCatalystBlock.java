package tor_tik96.create_em.process;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.content.processing.burner.LitBlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.LitBlazeBurnerBlock.FlameType;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import tor_tik96.create_em.registry.EMRegistry;

/**
 * Электрокатализатор: один блок вместо воды, костра, лавы и призрачного огня перед вентилятором Create.
 *
 * <p>Create сам определяет тип обработки по блоку в потоке воздуха
 * ({@code FanProcessingType.isValidAt}). Мы не регистрируем свои типы, а выставляем
 * те же признаки, что у горелки ифрита:
 * <ul>
 *   <li>промывка — блок отдаёт состояние «вода» ({@link #getFluidState});</li>
 *   <li>копчение — тег smoking + {@code blaze=smouldering};</li>
 *   <li>плавка — тег blasting + {@code blaze=kindled};</li>
 *   <li>призрачный огонь — тег haunting + {@code flame_type=soul};</li>
 *   <li>выключен — {@code blaze=none}, {@code flame_type=regular}: ни одна проверка не проходит.</li>
 * </ul>
 * Блок в теге {@code create:fan_transparent}, поэтому воздух проходит сквозь него.
 */
public class ElectricCatalystBlock extends Block implements IBE<ElectricCatalystBlockEntity>, IWrenchable {
    public static final EnumProperty<CatalystProcess> PROCESS = EnumProperty.create("process", CatalystProcess.class);

    public ElectricCatalystBlock(Properties properties) {
        super(properties.lightLevel(s -> s.getValue(PROCESS).light));
        registerDefaultState(withProcess(defaultBlockState(), CatalystProcess.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(PROCESS, BlazeBurnerBlock.HEAT_LEVEL, LitBlazeBurnerBlock.FLAME_TYPE);
    }

    /** Состояние блока для режима: свои свойства + признаки, которые проверяет Create. */
    public static BlockState withProcess(BlockState state, CatalystProcess p) {
        HeatLevel heat = switch (p) {
            case SMOKING -> HeatLevel.SMOULDERING;
            case BLASTING -> HeatLevel.KINDLED;
            default -> HeatLevel.NONE;
        };
        FlameType flame = p == CatalystProcess.HAUNTING ? FlameType.SOUL : FlameType.REGULAR;
        return state.setValue(PROCESS, p)
                .setValue(BlazeBurnerBlock.HEAT_LEVEL, heat)
                .setValue(LitBlazeBurnerBlock.FLAME_TYPE, flame);
    }

    /**
     * В режиме промывки блок «содержит воду» — так Create видит катализатор промывки.
     * Блок полный, поэтому вода не рисуется и не растекается: тиков жидкости мы не планируем,
     * а соседняя вода сквозь полный блок не проходит.
     */
    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(PROCESS) == CatalystProcess.SPLASHING
                ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public Class<ElectricCatalystBlockEntity> getBlockEntityClass() {
        return ElectricCatalystBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ElectricCatalystBlockEntity> getBlockEntityType() {
        return EMRegistry.CATALYST_BE.get();
    }
}
