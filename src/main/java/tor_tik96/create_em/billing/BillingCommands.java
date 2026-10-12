package tor_tik96.create_em.billing;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import tor_tik96.create_em.Create_em;

/**
 * /create_em bill      — выставить квитанции своим абонентам (как кнопка в терминале).
 * /create_em bill all  — всем абонентам на сервере (только оператор).
 */
@EventBusSubscriber(modid = Create_em.MODID)
public final class BillingCommands {
    @SubscribeEvent
    static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("create_em")
                .then(Commands.literal("bill")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            int n = BillingData.get(ctx.getSource().getServer()).billNow(ctx.getSource().getServer(), p.getUUID());
                            ctx.getSource().sendSuccess(() -> Component.translatable("create_em.bill.manual", n), false);
                            return n;
                        })
                        .then(Commands.literal("all")
                                .requires(s -> s.hasPermission(2))
                                .executes(ctx -> {
                                    int n = BillingData.get(ctx.getSource().getServer()).billNow(ctx.getSource().getServer(), null);
                                    ctx.getSource().sendSuccess(() -> Component.translatable("create_em.bill.manual", n), true);
                                    return n;
                                }))));
    }

    private BillingCommands() {}
}
