package tor_tik96.create_em.power;

import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Проходной узел с крошечным буфером — основа мини-счётчика и АВР.
 *
 * <p>Почему не «передать сразу в том же вызове»: тогда потребителю за тик приходит несколько
 * предложений (источник толкнул, мы потянули, провод сделал второй проход), а некоторые машины
 * ограничивают приём за вызов и берут лишнее. Почему не большой буфер: после отключения
 * питания он ещё долго кормит потребителя.
 *
 * <p>Поэтому: источники складывают энергию в буфер не больше, чем потребитель берёт за пару тиков,
 * а потребителю мы предлагаем ровно один раз за тик — как аккумулятор. После отключения
 * запаса хватает максимум на пару тиков.
 */
public final class Relay {
    /** Нижняя граница буфера, чтобы поток мог разогнаться (удваивается каждый тик, пока всё забирают). */
    private static final int MIN = 256;

    private int buffer;
    /** Сколько потребитель взял за прошлый тик. */
    private int lastDelivered;

    public int buffer() {
        return buffer;
    }

    /** Сколько можно держать в буфере: пара тиков потребления, но не больше свободного места у потребителя. */
    public int cap(@Nullable IEnergyStorage target, int max) {
        if (target == null || !target.canReceive()) return 0;
        long byRate = Math.max(MIN, 2L * lastDelivered);
        int room = target.getMaxEnergyStored() > 0
                ? Math.max(0, target.getMaxEnergyStored() - target.getEnergyStored()) + lastDelivered
                : Integer.MAX_VALUE;
        return (int) Math.min(Math.min(byRate, room), max);
    }

    /** Источник толкает в нас. */
    public int accept(int amount, boolean simulate, @Nullable IEnergyStorage target, int max) {
        int accepted = Math.max(0, Math.min(amount, cap(target, max) - buffer));
        if (!simulate) buffer += accepted;
        return accepted;
    }

    /** Источник, который сам не толкает: берём из него сами, сколько влезет. */
    public void pull(@Nullable IEnergyStorage source, @Nullable IEnergyStorage target, int max) {
        if (source == null || !source.canExtract()) return;
        int want = cap(target, max) - buffer;
        if (want <= 0) return;
        buffer += source.extractEnergy(want, false);
    }

    /**
     * Раз в тик: одно предложение потребителю. Возвращает, сколько он взял.
     * Сначала отбрасываем всё сверх нормы — например, огромный запас из старого сохранения,
     * который иначе ещё долго «кормил» бы потребителя после отключения питания.
     */
    public int push(@Nullable IEnergyStorage target, int max) {
        buffer = Math.min(buffer, Math.max(MIN, cap(target, max)));
        int moved = 0;
        if (target != null && target.canReceive() && buffer > 0)
            moved = target.receiveEnergy(buffer, false);
        buffer -= moved;
        lastDelivered = moved;
        return moved;
    }

    /** Потребитель сам забирает с выхода. */
    public int take(int amount, boolean simulate) {
        int given = Math.max(0, Math.min(amount, buffer));
        if (!simulate) {
            buffer -= given;
            lastDelivered += given;
        }
        return given;
    }

    public void clear() {
        buffer = 0;
    }

}
