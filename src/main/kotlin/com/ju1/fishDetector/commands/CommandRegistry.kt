package com.ju1.fishDetector.commands

import com.ju1.fishDetector.FishDetector
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.PlayerProfileListResolver
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import kotlin.math.ceil
import kotlin.math.min

class CommandRegistry(private val plugin: FishDetector) {

    fun register() {
        val manager = plugin.lifecycleManager
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()

            val root = Commands.literal("fishdetector")
                .requires { it.sender.hasPermission("fishdetector.admin") }
                .then(reloadNode())
                .then(toggleNode())
                .then(checkNode())
                .then(listNode())
                .build()

            commands.register(root, "Main command for FishDetector", listOf("fd"))
        }
    }

    private fun reloadNode() = Commands.literal("reload").executes { ctx ->
        plugin.configManager.load()
        ctx.source.sender.sendMessage(Component.text("Config reloaded.", NamedTextColor.GREEN))
        1
    }

    private fun toggleNode() = Commands.literal("toggle")
        .executes { ctx -> toggle(ctx, null) }
        .then(Commands.argument("state", BoolArgumentType.bool()).executes { ctx ->
            toggle(ctx, BoolArgumentType.getBool(ctx, "state"))
        })

    private fun toggle(ctx: CommandContext<CommandSourceStack>, state: Boolean?): Int {
        val current = plugin.configManager.isEnabled
        val newState = state ?: !current

        if (newState != current) {
            plugin.configManager.toggleState()
        }

        val status = if (plugin.configManager.isEnabled) "ON" else "OFF"
        val color = if (plugin.configManager.isEnabled) NamedTextColor.GREEN else NamedTextColor.RED

        ctx.source.sender.sendMessage(
            Component.text("FishDetector: ", NamedTextColor.GOLD)
                .append(Component.text(status, color))
        )
        return 1
    }

    private fun checkNode() = Commands.literal("check")
        .then(Commands.argument("target", ArgumentTypes.player()).executes { ctx ->
            val players = ctx.getArgument("target", PlayerProfileListResolver::class.java)
                .resolve(ctx.source)

            val target = players.firstOrNull() as? Player

            if (target != null) {
                val count = plugin.fishingManager.getPunishmentCount(target)
                ctx.source.sender.sendMessage(Component.text("${target.name} has been punished $count times.", NamedTextColor.YELLOW))
            } else {
                ctx.source.sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
            }
            1
        })

    private fun listNode() = Commands.literal("list")
        .then(
            Commands.argument("type", StringArgumentType.word())
            .suggests { _, builder ->
                builder.suggest("punished")
                builder.suggest("fishing")
                builder.buildFuture()
            }
            .executes { ctx -> executeList(ctx, 1) }
            .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes { ctx ->
                executeList(ctx, IntegerArgumentType.getInteger(ctx, "page"))
            })
        )

    private fun executeList(ctx: CommandContext<CommandSourceStack>, page: Int): Int {
        val type = StringArgumentType.getString(ctx, "type").lowercase()
        val resultList: List<Pair<String, String>> = when (type) {
            "punished" -> Bukkit.getOnlinePlayers()
                .map { it to plugin.fishingManager.getPunishmentCount(it) }
                .sortedByDescending { it.second }
                .map { it.first.name to "${it.second} times" }
            "fishing" -> plugin.fishingManager.getActiveFishers().map { (p, info) -> p.name to info }
            else -> return 0
        }

        val pageSize = plugin.configManager.pageSize
        val totalPages = ceil(resultList.size / pageSize.toDouble()).toInt().coerceAtLeast(1)
        val actualPage = min(page, totalPages)

        if (resultList.isEmpty()) {
            ctx.source.sender.sendMessage(Component.text("No results found.", NamedTextColor.YELLOW))
            return 1
        }

        ctx.source.sender.sendMessage(Component.text("--- $type List ($actualPage/$totalPages) ---", NamedTextColor.GOLD))

        val startIndex = (actualPage - 1) * pageSize
        val endIndex = min(startIndex + pageSize, resultList.size)

        for (i in startIndex until endIndex) {
            val (name, info) = resultList[i]
            ctx.source.sender.sendMessage(
                Component.text("${i + 1}. ", NamedTextColor.GRAY)
                    .append(Component.text(name, NamedTextColor.WHITE))
                    .append(Component.text(" - $info", NamedTextColor.YELLOW))
            )
        }
        return 1
    }
}