package tor_tik96.create_em;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Серверный конфиг: config/create_em-server.toml в папке мира. */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue ANDESITE_MIN_LIMIT = BUILDER
            .comment("Наименьший лимит, который можно выставить андезитовому (бытовому) счётчику, FE/t")
            .defineInRange("andesiteMinLimit", 50, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue ANDESITE_LIMIT = BUILDER
            .comment("Наибольший лимит андезитового счётчика (и лимит по умолчанию), FE/t")
            .defineInRange("andesiteLimit", 500, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue BRASS_MIN_LIMIT = BUILDER
            .comment("Наименьший лимит латунного (промышленного) счётчика, FE/t")
            .defineInRange("brassMinLimit", 500, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue BRASS_LIMIT = BUILDER
            .comment("Наибольший лимит латунного счётчика (и лимит по умолчанию), FE/t")
            .defineInRange("brassLimit", 8192, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.LongValue FE_PER_KWH = BUILDER
            .comment("Сколько FE считается за 1 кВт·ч")
            .defineInRange("fePerKwh", 1_000_000L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.IntValue TRANSFORMER_MAX_TRANSFER = BUILDER
            .comment("Сколько FE/t трансформатор может отдать в сервисную линию")
            .defineInRange("transformerMaxTransfer", 16384, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue MAX_WIRE_LENGTH = BUILDER
            .comment("Максимальная длина провода сервисной линии, блоков")
            .defineInRange("maxWireLength", 24, 2, 256);

    public static final ModConfigSpec.IntValue BILLING_PERIOD_DAYS = BUILDER
            .comment("По умолчанию для новых поставщиков: раз в сколько игровых дней выставляются квитанции (каждый меняет у себя в панели)")
            .defineInRange("billingPeriodDays", 3, 1, 365);

    public static final ModConfigSpec.IntValue LIMIT_AFTER_DAYS = BUILDER
            .comment("По умолчанию для новых поставщиков: через сколько игровых дней долга мощность урезается (-1 — никогда)")
            .defineInRange("limitAfterDays", 2, -1, 365);

    public static final ModConfigSpec.IntValue CUT_AFTER_DAYS = BUILDER
            .comment("По умолчанию для новых поставщиков: через сколько игровых дней долга счётчик отключается (-1 — никогда)")
            .defineInRange("cutAfterDays", 4, -1, 365);

    public static final ModConfigSpec.IntValue LIMITED_PERCENT = BUILDER
            .comment("По умолчанию для новых поставщиков: сколько процентов лимита остаётся при урезании за долг")
            .defineInRange("limitedPercent", 20, 0, 100);

    // ---------- электрокатализатор ----------
    public static final ModConfigSpec.IntValue CATALYST_CAPACITY = BUILDER
            .comment("Электрокатализатор: ёмкость буфера, FE")
            .defineInRange("catalystCapacity", 20000, 100, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CATALYST_SPLASHING = BUILDER
            .comment("Электрокатализатор: расход в режиме промывки, FE/t")
            .defineInRange("catalystSplashingFe", 16, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CATALYST_SMOKING = BUILDER
            .comment("Электрокатализатор: расход в режиме копчения, FE/t")
            .defineInRange("catalystSmokingFe", 32, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CATALYST_BLASTING = BUILDER
            .comment("Электрокатализатор: расход в режиме плавки, FE/t")
            .defineInRange("catalystBlastingFe", 64, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CATALYST_HAUNTING = BUILDER
            .comment("Электрокатализатор: расход в режиме призрачного огня, FE/t")
            .defineInRange("catalystHauntingFe", 48, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CATALYST_UNIVERSAL = BUILDER
            .comment("Электрокатализатор: расход в универсальном режиме, FE/t")
            .defineInRange("catalystUniversalFe", 128, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> CATALYST_UNIVERSAL_ORDER = BUILDER
            .comment("Электрокатализатор, универсальный режим: в каком порядке пробовать обработки для предмета.",
                    "smoking — копчение, splashing — промывка, haunting — призрачный огонь, blasting — плавка.",
                    "Например, чтобы дроблёную руду плавить, а не промывать, поставьте blasting раньше splashing.")
            .defineListAllowEmpty("catalystUniversalOrder", java.util.List.of("smoking", "splashing", "haunting", "blasting"),
                    () -> "smoking", o -> o instanceof String str && java.util.Set.of("smoking", "splashing", "haunting", "blasting").contains(str));

    // ---------- аккумулятор и АВР ----------
    public static final ModConfigSpec.IntValue BATTERY_CAPACITY = BUILDER
            .comment("Аккумулятор: ёмкость, FE")
            .defineInRange("batteryCapacity", 2_000_000, 1000, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue BATTERY_MAX_IO = BUILDER
            .comment("Аккумулятор: наибольший приём и отдача, FE/t")
            .defineInRange("batteryMaxIo", 4096, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue ATS_MAX_TRANSFER = BUILDER
            .comment("АВР: сколько FE/t пропускает")
            .defineInRange("atsMaxTransfer", 16384, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue ATS_RETURN_DELAY = BUILDER
            .comment("АВР: через сколько тиков стабильного основного ввода вернуться с резерва (20 = 1 с)")
            .defineInRange("atsReturnDelay", 60, 0, 72000);

    public static final ModConfigSpec.IntValue MINI_METER_MAX_TRANSFER = BUILDER
            .comment("Мини-счётчик: сколько FE/t пропускает")
            .defineInRange("miniMeterMaxTransfer", 262144, 1, Integer.MAX_VALUE);

    // ---------- конденсатор, кабель, электризатор ----------
    public static final ModConfigSpec.IntValue CAPACITOR_CAPACITY = BUILDER
            .comment("Конденсатор: ёмкость, FE")
            .defineInRange("capacitorCapacity", 500_000, 100, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CAPACITOR_CHARGE_RATE = BUILDER
            .comment("Конденсатор: скорость зарядки, FE/t (разряд — без ограничения)")
            .defineInRange("capacitorChargeRate", 4096, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CABLE_MAX_TRANSFER = BUILDER
            .comment("Силовой кабель: сколько FE за тик может пройти через одну сеть кабелей")
            .defineInRange("powerCableMaxTransfer", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue ELECTRIFIER_FE_PER_DAMAGE = BUILDER
            .comment("Электризатор: сколько FE нужно на 1 единицу урона (полсердца)")
            .defineInRange("electrifierFePerDamage", 200, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue ELECTRIFIER_MAX_DAMAGE = BUILDER
            .comment("Электризатор: наибольший урон за один разряд")
            .defineInRange("electrifierMaxDamage", 100, 1, 10000);

    public static final ModConfigSpec.IntValue ELECTRIFIER_RADIUS = BUILDER
            .comment("Электризатор: наибольший радиус, который можно выставить на блоке, блоков")
            .defineInRange("electrifierRadius", 8, 1, 16);

    public static final ModConfigSpec.IntValue ELECTRIFIER_MAX_INPUT = BUILDER
            .comment("Электризатор: наибольший приём, FE/t")
            .defineInRange("electrifierMaxInput", 10_000_000, 1, Integer.MAX_VALUE);

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}

    /** Значение конфига или запасное, если конфиг ещё не загружен (подсказки предметов в меню и т.п.). */
    public static int safe(ModConfigSpec.IntValue v, int fallback) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }
}
