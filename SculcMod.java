package pl.sculc;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class SculcMod implements ClientModInitializer {
    public static final Miner MINER = new Miner();

    private static final SuggestionProvider<FabricClientCommandSource> BLOCK_SUGGESTIONS =
            (ctx, builder) -> CommandSource.suggestIdentifiers(Registries.BLOCK.getIds(), builder);

    @Override
    public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(MINER::tick);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(literal("sculc-ustaw-pozycja-1").executes(ctx -> setPos(ctx, 1)));
            dispatcher.register(literal("sculc-ustaw-pozycja-2").executes(ctx -> setPos(ctx, 2)));
            dispatcher.register(literal("sculc-kop-1").executes(ctx -> setPos(ctx, 3)));
            dispatcher.register(literal("sculc-kop-2").executes(ctx -> setPos(ctx, 4)));

            dispatcher.register(literal("sculc-block")
                    .executes(ctx -> {
                        say(ctx, "Wybrane bloki: " + MINER.blockNames());
                        return 1;
                    })
                    .then(argument("block", IdentifierArgumentType.identifier())
                            .suggests(BLOCK_SUGGESTIONS)
                            .executes(ctx -> toggleBlock(ctx))));

            dispatcher.register(literal("sculc-block-wyczysc").executes(ctx -> {
                MINER.blocks.clear();
                say(ctx, "Wyczyszczono liste blokow.");
                return 1;
            }));

            dispatcher.register(literal("sculc-start").executes(ctx -> {
                MINER.start(MinecraftClient.getInstance());
                return 1;
            }));

            dispatcher.register(literal("sculc-stop").executes(ctx -> {
                MINER.stop(MinecraftClient.getInstance(), "Zatrzymano.");
                return 1;
            }));

            dispatcher.register(literal("sculc-status").executes(ctx -> {
                say(ctx, "Trasa: " + fmt(MINER.pos1) + " <-> " + fmt(MINER.pos2)
                        + " | Kopanie: " + fmt(MINER.kop1) + " / " + fmt(MINER.kop2)
                        + " | Bloki: " + MINER.blockNames()
                        + " | Dziala: " + (MINER.running ? "TAK" : "nie"));
                return 1;
            }));
        });
    }

    private static int setPos(CommandContext<FabricClientCommandSource> ctx, int which) {
        // blok, na ktorym stoisz (podloga)
        BlockPos p = ctx.getSource().getPlayer().getBlockPos().down();
        String name;
        switch (which) {
            case 1 -> { MINER.pos1 = p; name = "Trasa - pozycja 1"; }
            case 2 -> { MINER.pos2 = p; name = "Trasa - pozycja 2"; }
            case 3 -> { MINER.kop1 = p; name = "Kopanie - narozcznik 1"; }
            default -> { MINER.kop2 = p; name = "Kopanie - narozcznik 2"; }
        }
        say(ctx, name + " ustawiona: " + fmt(p));
        return 1;
    }

    private static int toggleBlock(CommandContext<FabricClientCommandSource> ctx) {
        Identifier id = ctx.getArgument("block", Identifier.class);
        if (!Registries.BLOCK.containsId(id)) {
            say(ctx, "Nie ma takiego bloku: " + id);
            return 0;
        }
        Block b = Registries.BLOCK.get(id);
        if (b == Blocks.AIR) {
            say(ctx, "Nie mozna wybrac powietrza.");
            return 0;
        }
        if (MINER.blocks.remove(b)) {
            say(ctx, "Usunieto z listy: " + id);
        } else {
            MINER.blocks.add(b);
            say(ctx, "Dodano do listy: " + id);
        }
        return 1;
    }

    static String fmt(BlockPos p) {
        return p == null ? "brak" : p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static void say(CommandContext<FabricClientCommandSource> ctx, String msg) {
        ctx.getSource().sendFeedback(Text.literal("[Sculc] " + msg));
    }
}
