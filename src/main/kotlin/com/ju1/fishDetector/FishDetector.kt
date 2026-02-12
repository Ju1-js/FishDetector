package com.ju1.fishDetector

import com.mojang.brigadier.arguments.BoolArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import java.time.Duration
import java.util.UUID
import kotlin.math.abs
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.title.Title
import com.ju1.fishDetector.LatencyMonitor
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

class FishDetector : JavaPlugin(), Listener {
    private class FisherSession(var lastActiveTime: Long, var lastYaw: Float, var lastPitch: Float)

    private var isEnabled: Boolean = false
    private var afkTimeThresholdMillis: Long = 0
    private var warningTimeThresholdMillis: Long = 0
    private var cleanupTimeThresholdMillis: Long = 0
    private var rotationSensitivity: Double = 0.0

    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    private val fishingSessions = mutableMapOf<UUID, FisherSession>()
    private val warnedPlayers = mutableSetOf<UUID>()
    private val pendingKicks = ArrayList<Pair<Player, UUID>>()

    override fun onEnable() {
        saveDefaultConfig()
        loadConfigValues()

        server.pluginManager.registerEvents(this, this)
        LatencyMonitor.init(this)

        registerCommands()

        if (isEnabled) {
            Bukkit.getOnlinePlayers().forEach { player ->
                if (player.fishHook != null) {
                    trackPlayer(player)
                }
            }
        }

        val interval = config.getInt("check-interval-ticks", 100).toLong()
        server.scheduler.runTaskTimer(this, Runnable { checkAfkFishers() }, interval, interval)

        logger.info("FishDetector enabled.")
    }

    override fun onDisable() {
        fishingSessions.clear()
        warnedPlayers.clear()
    }

    private fun registerCommands() {
        val manager = this.lifecycleManager
        manager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()

            val reloadNode =
                    Commands.literal("reload")
                            .executes { ctx ->
                                loadConfigValues()
                                ctx.source.sender.sendMessage(
                                        Component.text(
                                                "FishDetector config reloaded.",
                                                NamedTextColor.GREEN
                                        )
                                )
                                1
                            }
                            .build()
            val toggleNode =
                    Commands.literal("toggle")
                            .executes { ctx ->
                                handleToggle(ctx.source.sender, null)
                                1
                            }
                            .then(
                                    Commands.argument("state", BoolArgumentType.bool()).executes {
                                            ctx ->
                                        val state = BoolArgumentType.getBool(ctx, "state")
                                        handleToggle(ctx.source.sender, state)
                                        1
                                    }
                            )

            val fishDetectorCommand =
                    Commands.literal("fishdetector")
                            .requires { it.sender.hasPermission("fishdetector.admin") }
                            .then(reloadNode)
                            .then(toggleNode)
                            .build()

            commands.register(fishDetectorCommand, "Main command for FishDetector", listOf("fd"))
        }
    }

    private fun handleToggle(sender: CommandSender, explicitState: Boolean?) {
        val newState = explicitState ?: !isEnabled
        if (isEnabled != newState) {
            isEnabled = newState
            config.set("enabled", isEnabled)
            saveConfig()

            val color = if (isEnabled) NamedTextColor.GREEN else NamedTextColor.RED
            val statusText = if (isEnabled) "ON" else "OFF"

            sender.sendMessage(
                    Component.text("FishDetector global toggle: ", NamedTextColor.GOLD)
                            .append(Component.text(statusText, color))
            )

            if (!isEnabled) {
                fishingSessions.clear()
                warnedPlayers.clear()
            } else {
                Bukkit.getOnlinePlayers().forEach { if (it.fishHook != null) trackPlayer(it) }
            }
        } else {
            sender.sendMessage(
                    Component.text("FishDetector is already ", NamedTextColor.GOLD)
                            .append(
                                    Component.text(
                                            if (isEnabled) "ON" else "OFF",
                                            if (isEnabled) NamedTextColor.GREEN
                                            else NamedTextColor.RED
                                    )
                            )
            )
        }
    }

    private fun loadConfigValues() {
        reloadConfig()
        isEnabled = config.getBoolean("enabled")
        afkTimeThresholdMillis = config.getLong("afk-time-seconds") * 1000L
        warningTimeThresholdMillis = config.getLong("warning-time-seconds") * 1000L
        cleanupTimeThresholdMillis = config.getLong("cleanup-timeout-seconds", 600) * 1000L
        rotationSensitivity = config.getDouble("rotation-threshold", 1.0)
    }

    private fun trackPlayer(player: Player) {
        fishingSessions.getOrPut(player.uniqueId) {
            FisherSession(System.currentTimeMillis(), player.yaw, player.pitch)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFish(event: PlayerFishEvent) {
        if (!isEnabled) return
        if (event.state == PlayerFishEvent.State.CAUGHT_FISH) {
            trackPlayer(event.player)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        fishingSessions.remove(event.player.uniqueId)
        warnedPlayers.remove(event.player.uniqueId)
    }

    private fun checkAfkFishers() {
        if (!isEnabled || fishingSessions.isEmpty()) return
        pendingKicks.clear()

        val now = System.currentTimeMillis()
        val iterator = fishingSessions.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val uuid = entry.key
            val session = entry.value
            val player = Bukkit.getPlayer(uuid)

            // Cleanup invalid players
            if (player == null || !player.isOnline) {
                iterator.remove()
                warnedPlayers.remove(uuid)
                continue
            }

            // Check for movement
            val currentYaw = player.yaw
            val currentPitch = player.pitch

            val diffYaw = abs(currentYaw - session.lastYaw)
            val diffPitch = abs(currentPitch - session.lastPitch)

            if ((diffYaw + diffPitch) > rotationSensitivity) {
                session.lastActiveTime = now
                session.lastYaw = currentYaw
                session.lastPitch = currentPitch
                warnedPlayers.remove(uuid)
                continue
            }

            // Haven't moved, still fishing?
            val timeInactive = now - session.lastActiveTime

            if (player.fishHook == null) {
                if (timeInactive > cleanupTimeThresholdMillis) {
                    iterator.remove()
                    warnedPlayers.remove(uuid)
                }
                continue
            }

            // Handle Actions
            if (timeInactive > afkTimeThresholdMillis) {
                pendingKicks.add(player to uuid)
                iterator.remove()
                continue
            }

            if (timeInactive > warningTimeThresholdMillis && !warnedPlayers.contains(uuid)) {
                sendWarning(player)
                warnedPlayers.add(uuid)
            }
        }
        pendingKicks.forEach { handleAfkPlayer(it.first, it.second) }
    }

    private fun sendWarning(player: Player) {
        val msgRaw = config.getString("warning-message", "&c&lAFK DETECTED!")
        val mainMsg = legacySerializer.deserialize(msgRaw!!)

        val title =
                Title.title(
                        mainMsg,
                        Component.text("You will be kicked shortly.", NamedTextColor.YELLOW),
                        Title.Times.times(
                                Duration.ofMillis(500),
                                Duration.ofMillis(3000),
                                Duration.ofMillis(1000)
                        )
                )
        player.showTitle(title)
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f)
    }

    private fun handleAfkPlayer(player: Player, uuid: UUID) {
        warnedPlayers.remove(uuid)

        if (config.getBoolean("actions.cancel-fishing")) {
            player.fishHook?.remove()
            player.sendMessage(
                    Component.text("You stopped fishing due to inactivity.", NamedTextColor.RED)
            )
        }

        val alertRaw = config.getString("actions.broadcast-alert")
        if (!alertRaw.isNullOrEmpty()) {
            val alertMsg = legacySerializer.deserialize(alertRaw.replace("%player%", player.name))
            Bukkit.broadcast(alertMsg)
        }

        if (config.getBoolean("actions.kick-player")) {
            val reasonRaw = config.getString("actions.kick-message", "AFK Fishing")
            player.kick(legacySerializer.deserialize(reasonRaw!!))
        }
    }
}
