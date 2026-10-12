package tor_tik96.create_em.meter;

import tor_tik96.create_em.Config;

/** Уровень счётчика: от него зависит коридор лимита, модель и рендер. */
public enum MeterTier {
    /** Бытовой: андезитовый корпус, стрелка. */
    ANDESITE(10, 50),
    /** Промышленный: латунный корпус, никси-лампы. */
    BRASS(100, 1000);

    /** Малый и большой шаг кнопок в меню лимита. */
    public final int smallStep, largeStep;

    MeterTier(int smallStep, int largeStep) {
        this.smallStep = smallStep;
        this.largeStep = largeStep;
    }

    /** Нижняя граница лимита, FE/t. Из серверного конфига — вызывать только на сервере. */
    public int minLimit() {
        int min = switch (this) {
            case ANDESITE -> Config.ANDESITE_MIN_LIMIT.get();
            case BRASS -> Config.BRASS_MIN_LIMIT.get();
        };
        return Math.min(min, maxLimit());
    }

    /** Верхняя граница лимита и лимит по умолчанию, FE/t. */
    public int maxLimit() {
        return switch (this) {
            case ANDESITE -> Config.ANDESITE_LIMIT.get();
            case BRASS -> Config.BRASS_LIMIT.get();
        };
    }

    /** Буфер с запасом в два тика при максимальном лимите. */
    public int bufferSize() {
        return Math.max(1000, maxLimit() * 2);
    }
}
