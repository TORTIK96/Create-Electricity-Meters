package tor_tik96.create_em;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Серверный конфиг: config/create_em-server.toml в папке мира. */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue ANDESITE_LIMIT = BUILDER
            .comment("Максимальный поток андезитового (бытового) счётчика, FE/t")
            .defineInRange("andesiteLimit", 500, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue BRASS_LIMIT = BUILDER
            .comment("Максимальный поток латунного (промышленного) счётчика, FE/t")
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
