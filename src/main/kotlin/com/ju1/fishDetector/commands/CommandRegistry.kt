package com.ju1.fishDetector.commands

import com.ju1.fishDetector.FishDetector
import com.ju1.fishDetector.utils.MessageUtils
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import kotlin.math.ceil
import kotlin.math.min

class CommandRegistry(private val plugin: FishDetector) {

    fun register() {
        val manager = plugin.lifecycleManager
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val root = Commands.literal("fishdetector").requires { it.sender.hasPermission("fishdetector.admin") }
                .then(toggleNode()).then(reloadNode()).then(listNode()).then(checkNode()).then(resetNode()).build()

            event.registrar().register(root, "Main command for FishDetector", listOf("fd"))
        }
    }

    private fun reloadNode() = Commands.literal("reload").executes { ctx ->
        plugin.configManager.load()
        plugin.updateTask()
        ctx.source.sender.sendRichMessage("<green>Config reloaded and task updated.")
        1
    }

    private fun toggleNode() = Commands.literal("toggle").executes { ctx -> toggle(ctx, null) }
        .then(Commands.argument("state", BoolArgumentType.bool()).executes { ctx ->
            toggle(ctx, BoolArgumentType.getBool(ctx, "state"))
        })

    private fun checkNode() =
        Commands.literal("check").then(Commands.argument("target", StringArgumentType.word()).suggests { _, builder ->
            Bukkit.getOnlinePlayers().forEach { builder.suggest(it.name) }
            builder.buildFuture()
        }.executes { ctx ->
            val target = resolveTarget(ctx) ?: return@executes 1
            val count = plugin.fishingManager.getPunishmentCount(target)

            val resolver = MessageUtils.getPlayerResolver(target)
            ctx.source.sender.sendRichMessage("<yellow><player> has been punished $count times.", resolver)
            1
        })

    private fun resetNode() =
        Commands.literal("reset").then(Commands.argument("target", StringArgumentType.word()).suggests { _, builder ->
            Bukkit.getOnlinePlayers().forEach { builder.suggest(it.name) }
            builder.buildFuture()
        }.executes { ctx ->
            val target = resolveTarget(ctx) ?: return@executes 1
            plugin.fishingManager.resetPunishmentCount(target)

            val resolver = MessageUtils.getPlayerResolver(target)
            ctx.source.sender.sendRichMessage(
                "<yellow><player> has had their punishment count reset.", resolver
            )
            1
        })

    private fun listNode() =
        Commands.literal("list").then(
            Commands.argument("type", StringArgumentType.word()).suggests { _, builder ->
            listOf("punished", "fishing").forEach { builder.suggest(it) } // "test" in listOf
            builder.buildFuture()
        }.executes { ctx -> executeList(ctx, 1) }.then(
            Commands.argument("page", IntegerArgumentType.integer(1)).executes { ctx ->
                executeList(ctx, IntegerArgumentType.getInteger(ctx, "page"))
            })
        )

    private fun toggle(ctx: CommandContext<CommandSourceStack>, state: Boolean?): Int {
        val current = plugin.configManager.isEnabled
        val newState = state ?: !current

        if (newState != current) {
            plugin.configManager.toggleState()
        }

        val status = if (plugin.configManager.isEnabled) "<green>ON" else "<red>OFF"
        ctx.source.sender.sendRichMessage("<gold>FishDetector: $status")
        return 1
    }

    private fun resolveTarget(ctx: CommandContext<CommandSourceStack>): OfflinePlayer? {
        val targetName = StringArgumentType.getString(ctx, "target")
        val target = Bukkit.getOfflinePlayer(targetName)

        if (target.hasPlayedBefore() || target.isOnline) {
            return target
        }

        ctx.source.sender.sendRichMessage("<red>Player '$targetName' not found.")
        return null
    }

    private fun executeList(ctx: CommandContext<CommandSourceStack>, page: Int): Int {
        val type = StringArgumentType.getString(ctx, "type").lowercase()

        // Generate the list based on type
        val resultList: List<Pair<String, String>> = when (type) {
            "punished" -> {
                plugin.dataManager.getAllPunishedPlayers().toList().sortedByDescending { it.second }
                    .map { it.first to "${it.second} times" }
            }

            "fishing" -> plugin.fishingManager.getActiveFishers().map { (p, info) -> p.name to info }

            // "test" -> (1..55).map { "TestPlayer_$it" to "Value: ${56 - it}" }

            else -> {
                ctx.source.sender.sendRichMessage("<red>Invalid list type.")
                return 0
            }
        }

        val prType = type.replaceFirstChar { it.uppercase() }
        if (resultList.isEmpty()) {
            ctx.source.sender.sendRichMessage("<yellow>No results found for '$prType'.")
            return 1
        }

        // Pagination Logic
        val pageSize = plugin.configManager.pageSize
        val totalPages = ceil(resultList.size / pageSize.toDouble()).toInt().coerceAtLeast(1)
        val actualPage = page.coerceIn(1, totalPages)

        val startIndex = (actualPage - 1) * pageSize
        val endIndex = min(startIndex + pageSize, resultList.size)

        // Header
        val prevButton = if (actualPage > 1) {
            Component.text("«", NamedTextColor.GREEN).hoverEvent(HoverEvent.showText(Component.text("Previous Page")))
                .clickEvent(ClickEvent.runCommand("/fd list $prType ${actualPage - 1}"))
        } else {
            Component.text("«", NamedTextColor.GRAY)
        }

        val nextButton = if (actualPage < totalPages) {
            Component.text("»", NamedTextColor.GREEN).hoverEvent(HoverEvent.showText(Component.text("Next Page")))
                .clickEvent(ClickEvent.runCommand("/fd list $prType ${actualPage + 1}"))
        } else {
            Component.text("»", NamedTextColor.GRAY)
        }

        val header = Component.text().append(Component.text("--- ", NamedTextColor.GOLD)).append(prevButton)
            .append(Component.text(" $prType List ($actualPage/$totalPages) ", NamedTextColor.GOLD)).append(nextButton)
            .append(Component.text(" ---", NamedTextColor.GOLD)).build()

        ctx.source.sender.sendMessage(header)

        // List Items
        for (i in startIndex until endIndex) {
            val (name, info) = resultList[i]
            val nameComponent = Component.text(name, NamedTextColor.WHITE)
                .hoverEvent(HoverEvent.showText(Component.text("Click to teleport to $name", NamedTextColor.AQUA)))
                .clickEvent(ClickEvent.runCommand("/tp $name"))

            ctx.source.sender.sendMessage(
                Component.text("${i + 1}. ", NamedTextColor.GRAY).append(nameComponent)
                    .append(Component.text(" - $info", NamedTextColor.YELLOW))
            )
        }
        return 1
    }
}