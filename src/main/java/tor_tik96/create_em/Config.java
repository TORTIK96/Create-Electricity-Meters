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

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
