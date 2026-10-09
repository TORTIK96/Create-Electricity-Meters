package tor_tik96.create_em.meter;

import tor_tik96.create_em.Config;

/** Уровень счётчика: от него зависит лимит, модель и рендер. */
public enum MeterTier {
    /** Бытовой: андезитовый корпус, стрелка. */
    ANDESITE,
    /** Промышленный: латунный корпус, никси-лампы. */
    BRASS;

    /** Максимальный поток, FE/t. Читается из серверного конфига, поэтому вызывать только на сервере. */
    public int limitPerTick() {
        return switch (this) {
            case ANDESITE -> Config.ANDESITE_LIMIT.get();
            case BRASS -> Config.BRASS_LIMIT.get();
        };
    }

    /** Буфер с запасом в два тика, чтобы любые провода могли в нас залить. */
    public int bufferSize() {
        return Math.max(1000, limitPerTick() * 2);
    }
}
