package tor_tik96.create_em.power;

import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.function.IntSupplier;

/**
 * Одна «клемма» блока: что она умеет — принимать, отдавать или и то и другое.
 * Блоки с разными сторонами (аккумулятор, АВР) отдают на каждую грань свою клемму.
 */
public final class EnergyPort implements IEnergyStorage {
    public interface Mover {
        int move(int amount, boolean simulate);
    }

    private static final Mover NONE = (a, s) -> 0;

    private final Mover receiver, extractor;
    private final boolean canReceive, canExtract;
    private final IntSupplier stored, capacity;

    private EnergyPort(Mover receiver, Mover extractor, boolean canReceive, boolean canExtract,
                       IntSupplier stored, IntSupplier capacity) {
        this.receiver = receiver;
        this.extractor = extractor;
        this.canReceive = canReceive;
        this.canExtract = canExtract;
        this.stored = stored;
        this.capacity = capacity;
    }

    public static EnergyPort input(Mover receiver, IntSupplier stored, IntSupplier capacity) {
        return new EnergyPort(receiver, NONE, true, false, stored, capacity);
    }

    public static EnergyPort output(Mover extractor, IntSupplier stored, IntSupplier capacity) {
        return new EnergyPort(NONE, extractor, false, true, stored, capacity);
    }

    @Override public int receiveEnergy(int toReceive, boolean simulate) { return toReceive <= 0 ? 0 : receiver.move(toReceive, simulate); }
    @Override public int extractEnergy(int toExtract, boolean simulate) { return toExtract <= 0 ? 0 : extractor.move(toExtract, simulate); }
    @Override public int getEnergyStored() { return stored.getAsInt(); }
    @Override public int getMaxEnergyStored() { return capacity.getAsInt(); }
    @Override public boolean canExtract() { return canExtract; }
    @Override public boolean canReceive() { return canReceive; }
}
