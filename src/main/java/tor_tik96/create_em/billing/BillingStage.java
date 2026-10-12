package tor_tik96.create_em.billing;

/** Стадия лицевого счёта по возрасту долга. Пороги — в серверном конфиге. */
public enum BillingStage {
    /** Долга нет. */
    NORMAL,
    /** Долг появился: счётчик работает, абонента предупреждают. */
    WARNING,
    /** Долг висит долго: мощность урезана. */
    LIMITED,
    /** Отключение: энергия не идёт. */
    CUT;

    public String key() { return "create_em.stage." + name().toLowerCase(); }
}
