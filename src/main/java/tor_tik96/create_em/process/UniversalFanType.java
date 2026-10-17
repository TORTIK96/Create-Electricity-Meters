package tor_tik96.create_em.process;

import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import tor_tik96.create_em.Config;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.List;

/**
 * Универсальная обработка: электрокатализатор в этом режиме сам выбирает для каждого предмета,
 * что с ним делать — коптить, промывать, наполнять духами или плавить (порядок — в конфиге).
 *
 * <p>Это свой тип обработки вентилятором Create: Create помечает предмет на ленте этим типом,
 * поэтому каждый предмет обрабатывается ровно один раз и сортировщик обработки работает как обычно.
 * В отличие от лавы, предметы без рецепта и еда здесь <b>не сгорают</b> — просто пролетают.
 */
public final class UniversalFanType implements FanProcessingType {
    @Override
    public boolean isValidAt(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.is(EMRegistry.CATALYST.get()) && s.getValue(ElectricCatalystBlock.PROCESS) == CatalystProcess.UNIVERSAL;
    }

    /** Проверяется раньше встроенных (у них 100–400). */
    @Override
    public int getPriority() {
        return 1000;
    }

    @Override
    public boolean canProcess(ItemStack stack, Level level) {
        return pick(stack, level) != null;
    }

    @Override
    @Nullable
    public List<ItemStack> process(ItemStack stack, Level level) {
        FanProcessingType t = pick(stack, level);
        return t == null ? null : t.process(stack, level);
    }

    /** Первая подходящая обработка по порядку из конфига. */
    @Nullable
    private static FanProcessingType pick(ItemStack stack, Level level) {
        for (String name : order()) {
            FanProcessingType t = switch (name) {
                case "smoking" -> AllFanProcessingTypes.SMOKING;
                case "splashing" -> AllFanProcessingTypes.SPLASHING;
                case "haunting" -> AllFanProcessingTypes.HAUNTING;
                case "blasting" -> AllFanProcessingTypes.BLASTING;
                default -> null;
            };
            if (t == null) continue;
            if (t == AllFanProcessingTypes.BLASTING ? canBlast(stack, level) : t.canProcess(stack, level)) return t;
        }
        return null;
    }

    private static final List<String> DEFAULT_ORDER = List.of("smoking", "blasting", "splashing", "haunting");

    /** Конфиг сервера; на клиенте до синхронизации его может не быть — тогда порядок по умолчанию. */
    private static List<? extends String> order() {
        try {
            return Config.CATALYST_UNIVERSAL_ORDER.get();
        } catch (IllegalStateException e) {
            return DEFAULT_ORDER;
        }
    }

    /**
     * Плавка Create «может» обработать почти всё — и сжигает то, на что нет рецепта, а еду сжигает
     * намеренно. Здесь берём только то, что действительно плавится и не является едой для копчения.
     */
    private static boolean canBlast(ItemStack stack, Level level) {
        SingleRecipeInput in = new SingleRecipeInput(stack);
        var rm = level.getRecipeManager();
        if (rm.getRecipeFor(RecipeType.SMOKING, in, level).isPresent()) return false;
        return rm.getRecipeFor(RecipeType.SMELTING, in, level).isPresent()
                || rm.getRecipeFor(RecipeType.BLASTING, in, level).isPresent();
    }

    @Override
    public void spawnProcessingParticles(Level level, Vec3 pos) {
        if (level.random.nextInt(8) != 0) return;
        var p = switch (level.random.nextInt(4)) {
            case 0 -> ParticleTypes.SPLASH;
            case 1 -> ParticleTypes.SMOKE;
            case 2 -> ParticleTypes.FLAME;
            default -> ParticleTypes.SOUL_FIRE_FLAME;
        };
        level.addParticle(p, pos.x, pos.y + .25f, pos.z, 0, 1 / 32f, 0);
    }

    private static final int[] COLORS = {0x4499FF, 0x555555, 0xFF4400, 0x126568};

    @Override
    public void morphAirFlow(AirFlowParticleAccess access, RandomSource random) {
        access.setColor(COLORS[random.nextInt(COLORS.length)]);
        access.setAlpha(.6f);
        if (random.nextFloat() < 1 / 64f) access.spawnExtraParticle(ParticleTypes.SMOKE, .125f);
        if (random.nextFloat() < 1 / 128f) access.spawnExtraParticle(ParticleTypes.FLAME, .25f);
    }

    /** Мобов и игроков не трогает: это машина для предметов. */
    @Override
    public void affectEntity(Entity entity, Level level) {}
}
