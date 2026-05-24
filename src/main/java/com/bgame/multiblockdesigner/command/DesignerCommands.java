package com.bgame.multiblockdesigner.command;

import com.bgame.multiblockdesigner.data.DefinitionSavedData;
import com.bgame.multiblockdesigner.definition.MultiblockDefinition;
import com.bgame.multiblockdesigner.export.DefinitionExporter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Optional;

/**
 * Command tree: /gtcmbd
 *   /gtcmbd list — lists all saved definitions
 *   /gtcmbd info id — shows details about a definition
 *   /gtcmbd export id — exports definition to a KubeJS file
 */
public class DesignerCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("gtcmbd")
                        .requires(src -> src.hasPermission(2))

                        .then(Commands.literal("list")
                                .executes(DesignerCommands::cmdList))

                        .then(Commands.literal("info")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            DefinitionSavedData.get(ctx.getSource().getLevel())
                                                    .all().forEach(def -> builder.suggest(def.id));
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> cmdInfo(ctx,
                                                StringArgumentType.getString(ctx, "id")))))

                        .then(Commands.literal("export")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            DefinitionSavedData.get(ctx.getSource().getLevel())
                                                    .all().forEach(def -> builder.suggest(def.id));
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> cmdExport(ctx,
                                                StringArgumentType.getString(ctx, "id")))))
        );
    }

    // /gtcmbd list
    private static int cmdList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Collection<MultiblockDefinition> all = DefinitionSavedData.get(src.getLevel()).all();

        if (all.isEmpty()) {
            src.sendSuccess(() -> Component.literal("[Designer] No definitions saved yet."), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal(
                "[Designer] Saved definitions (%d):".formatted(all.size())), false);
        for (MultiblockDefinition def : all) {
            src.sendSuccess(() -> Component.literal(
                            "  • %s  id=%s  blocks=%d  size=%dx%dx%d"
                                    .formatted(def.displayName, def.id, def.blocks.size(),
                                            def.size().getX(), def.size().getY(), def.size().getZ())),
                    false);
        }
        return all.size();
    }

    // gtcmbd info <id>
    private static int cmdInfo(CommandContext<CommandSourceStack> ctx, String id) {
        CommandSourceStack src = ctx.getSource();
        Optional<MultiblockDefinition> defOpt = DefinitionSavedData.get(src.getLevel()).get(id);

        if (defOpt.isEmpty()) {
            src.sendFailure(Component.literal("[Designer] No definition with id '%s'.".formatted(id)));
            return 0;
        }
        MultiblockDefinition def = defOpt.get();
        src.sendSuccess(() -> Component.literal("[Designer] " + def.displayName), false);
        src.sendSuccess(() -> Component.literal("  id    : " + def.id), false);
        src.sendSuccess(() -> Component.literal("  blocks: " + def.blocks.size()), false);
        src.sendSuccess(() -> Component.literal(
                "  size  : %dx%dx%d".formatted(
                        def.size().getX(), def.size().getY(), def.size().getZ())), false);
        def.blocks.stream()
                .collect(java.util.stream.Collectors.groupingBy(b -> b.role,
                        java.util.stream.Collectors.counting()))
                .forEach((role, count) ->
                        src.sendSuccess(() -> Component.literal(
                                "  %-14s: %d".formatted(role.name(), count)), false));
        return 1;
    }

    // /gtcmbd export <id>
    private static int cmdExport(CommandContext<CommandSourceStack> ctx, String id) {
        CommandSourceStack src = ctx.getSource();
        Optional<MultiblockDefinition> defOpt = DefinitionSavedData.get(src.getLevel()).get(id);

        if (defOpt.isEmpty()) {
            src.sendFailure(Component.literal("[Designer] No definition with id '%s'.".formatted(id)));
            return 0;
        }
        MultiblockDefinition def = defOpt.get();
        // Default to KubeJS for now via command (false = not Java)
        DefinitionExporter.ExportResult result = DefinitionExporter.export(src.getServer(), def, false);

        if (result.success) {
            src.sendSuccess(() -> Component.literal("[Designer] Exported '%s':".formatted(def.displayName)), false);
            src.sendSuccess(() -> Component.literal("  JS: " + result.jsPath), false);
            return 1;
        } else {
            src.sendFailure(Component.literal("[Designer] Export failed: " + result.error));
            return 0;
        }
    }
}