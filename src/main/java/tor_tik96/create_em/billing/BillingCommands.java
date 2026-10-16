package tor_tik96.create_em.billing;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import tor_tik96.create_em.gov.GovData;

import java.util.Collection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import tor_tik96.create_em.Create_em;

/**
 * /create_em bill      — выставить квитанции своим абонентам (как кнопка в терминале).
 * /create_em bill all  — всем абонентам на сервере (только оператор).
 * /create_em gov allow|deny <игрок>, /create_em gov list — список доступа к госэлектричеству (оператор).
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
                                })))
                .then(Commands.literal("gov")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("allow").then(Commands.argument("players", GameProfileArgument.gameProfile())
                                .executes(ctx -> govChange(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "players"), true))))
                        .then(Commands.literal("deny").then(Commands.argument("players", GameProfileArgument.gameProfile())
                                .executes(ctx -> govChange(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "players"), false))))
                        .then(Commands.literal("list").executes(ctx -> {
                            var list = GovData.get(ctx.getSource().getServer()).list();
                            String names = list.isEmpty() ? "—" : String.join(", ", list.values());
                            ctx.getSource().sendSuccess(() -> Component.translatable("create_em.gov.list", list.size(), names), false);
                            return list.size();
                        }))));
    }

    private static int govChange(CommandSourceStack src, Collection<GameProfile> profiles, boolean allow) {
        GovData d = GovData.get(src.getServer());
        int n = 0;
        for (GameProfile gp : profiles) {
            boolean changed = allow ? d.allow(gp.getId(), gp.getName()) : d.deny(gp.getId());
            if (changed) n++;
            String name = gp.getName();
            src.sendSuccess(() -> Component.translatable(allow ? "create_em.gov.allowed" : "create_em.gov.denied", name), true);
        }
        return n;
    }

    private BillingCommands() {}
}
