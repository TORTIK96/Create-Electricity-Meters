package tor_tik96.create_em.line;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Узел сервисной линии: трансформатор, коннектор или счётчик.
 * Узлы соединяются только проводом линии, обычные провода FE к ним не подключаются.
 */
public interface LineNode {
    LineLinks getLinks();

    /** Точка крепления провода внутри блока, в долях блока (0..1). */
    Vec3 getWireAnchor();

    /** Сколько проводов можно подключить к узлу. */
    int getMaxLinks();

    /** Конечный узел (счётчик): через него сеть дальше не идёт, к нему подключается любой владелец линии. */
    default boolean isTerminal() { return false; }

    @Nullable UUID getOwner();

    void setOwner(@Nullable UUID owner);
}
