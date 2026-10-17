package tor_tik96.create_em.chunk;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import tor_tik96.create_em.Create_em;
import tor_tik96.create_em.registry.EMRegistry;

import java.util.ArrayList;

/** Билеты принудительной загрузки чанков для загрузчиков. */
public final class ChunkLoading {
    /**
     * NeoForge хранит билеты между перезапусками. При загрузке мира оставляем только те,
     * у которых на месте ещё стоит загрузчик (иначе чанк остался бы загружен навсегда).
     */
    public static final TicketController TICKETS = new TicketController(
            ResourceLocation.fromNamespaceAndPath(Create_em.MODID, "chunk_loader"),
            (level, helper) -> {
                for (var owner : new ArrayList<>(helper.getBlockTickets().keySet()))
                    if (!level.getBlockState(owner).is(EMRegistry.CHUNK_LOADER.get())) helper.removeAllTickets(owner);
            });

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterTicketControllersEvent e) -> e.register(TICKETS));
    }

    private ChunkLoading() {}
}
