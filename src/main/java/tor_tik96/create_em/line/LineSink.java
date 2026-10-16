package tor_tik96.create_em.line;

/** То, что получает энергию по сервисной линии от трансформатора: счётчик или обратный преобразователь. */
public interface LineSink {
    /** Сколько влезло. */
    int acceptFromLine(int amount);

    boolean isRemoved();
}
