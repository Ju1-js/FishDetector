package com.ju1.fishDetector

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.time.Duration
import java.util.UUID
import kotlin.math.abs

class FishDetector : JavaPlugin(), Listener {

    private var isEnabled: Boolean = false
    private var afkTimeThresholdMillis: Long = 0
    private var warningTimeThresholdMillis: Long = 0
    private var cleanupTimeThresholdMillis: Long = 0
    private var rotationSensitivity: Double = 0.0

    // Map: UUID -> Timestamp of last ROTATION
    private val fishingPlayers: MutableMap<UUID, Long> = HashMap()
    private val warnedPlayers: MutableSet<UUID> = HashSet()

    override fun onEnable() {
        saveDefaultConfig()
        loadConfigValues()

        server.pluginManager.registerEvents(this, this)

        val interval = config.getInt("check-interval-ticks", 100).toLong()
        server.scheduler.runTaskTimer(this, Runnable { checkAfkFishers() }, interval, interval)

        logger.info("FishDetector enabled (Passive Rotation Detection)")
    }

    override fun onDisable() {
        fishingPlayers.clear()
        warnedPlayers.clear()
    }

    private fun loadConfigValues() {
        reloadConfig()
        val config = config
        isEnabled = config.getBoolean("enabled")
        afkTimeThresholdMillis = config.getLong("afk-time-seconds") * 1000L
        warningTimeThresholdMillis = config.getLong("warning-time-seconds") * 1000L
        cleanupTimeThresholdMillis = config.getLong("cleanup-timeout-seconds", 600) * 1000L
        rotationSensitivity = config.getDouble("rotation-threshold", 1.0)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (command.name.equals("fishdetector", ignoreCase = true)) {
            if (!sender.hasPermission("fishdetector.admin")) {
                sender.sendMessage(Component.text("No permission.", NamedTextColor.RED))
                return true
            }

            if (args.isEmpty()) {
                sender.sendMessage(Component.text("Usage: /fishdetector <toggle|reload>", NamedTextColor.YELLOW))
                return true
            }

            if (args[0].equals("reload", ignoreCase = true)) {
                loadConfigValues()
                sender.sendMessage(Component.text("FishDetector config reloaded successfully.", NamedTextColor.GREEN))
                return true
            }

            if (args[0].equals("toggle", ignoreCase = true)) {
                isEnabled = !isEnabled
                config.set("enabled", isEnabled)
                saveConfig()

                val status = if (isEnabled) Component.text("ON", NamedTextColor.GREEN) else Component.text("OFF", NamedTextColor.RED)
                sender.sendMessage(Component.text("FishDetector global toggle: ", NamedTextColor.GOLD).append(status))

                if (!isEnabled) {
                    fishingPlayers.clear()
                    warnedPlayers.clear()
                }
                return true
            }

            sender.sendMessage(Component.text("Usage: /fishdetector <toggle|reload>", NamedTextColor.YELLOW))
            return true
        }
        return false
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFish(event: PlayerFishEvent) {
        if (!isEnabled) return
        if (event.state == PlayerFishEvent.State.FISHING) {
            fishingPlayers.putIfAbsent(event.player.uniqueId, System.currentTimeMillis())
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (!isEnabled || fishingPlayers.isEmpty()) return

        val uuid = event.player.uniqueId
        if (!fishingPlayers.containsKey(uuid)) return

        if (event.from.yaw == event.to.yaw && event.from.pitch == event.to.pitch) return

        val diffYaw = abs(event.from.yaw - event.to.yaw)
        val diffPitch = abs(event.from.pitch - event.to.pitch)

        if ((diffYaw + diffPitch) > rotationSensitivity) {
            fishingPlayers[uuid] = System.currentTimeMillis()
            warnedPlayers.remove(uuid)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        fishingPlayers.remove(event.player.uniqueId)
        warnedPlayers.remove(event.player.uniqueId)
    }

    private fun checkAfkFishers() {
        if (!isEnabled || fishingPlayers.isEmpty()) return

        val now = System.currentTimeMillis()
        val config = config
        val iterator = fishingPlayers.keys.iterator()

        while (iterator.hasNext()) {
            val uuid = iterator.next()
            val player = Bukkit.getPlayer(uuid)

            if (player == null) {
                iterator.remove()
                warnedPlayers.remove(uuid)
                continue
            }

            val lastRotation = fishingPlayers[uuid] ?: continue
            val timeInactive = now - lastRotation

            if (player.fishHook == null) {
                if (timeInactive > cleanupTimeThresholdMillis) {
                    iterator.remove()
                    warnedPlayers.remove(uuid)
                }
                continue
            }

            // Punishment Check
            if (timeInactive > afkTimeThresholdMillis) {
                handleAfkPlayer(player, uuid, config)
                iterator.remove()
                continue
            }

            // Warning Check
            if (timeInactive > warningTimeThresholdMillis && !warnedPlayers.contains(uuid)) {
                sendWarning(player, config)
                warnedPlayers.add(uuid)
            }
        }
    }

    private fun sendWarning(player: Player, config: FileConfiguration) {
        val msgRaw = config.getString("warning-message", "&c&lAFK DETECTED!")
        val mainMsg = LegacyComponentSerializer.legacyAmpersand().deserialize(msgRaw!!)

        val title = Title.title(
            mainMsg,
            Component.text("You will be kicked shortly.", NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000))
        )
        player.showTitle(title)
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f)
    }

    private fun handleAfkPlayer(player: Player, uuid: UUID, config: FileConfiguration) {
        warnedPlayers.remove(uuid)

        if (config.getBoolean("actions.cancel-fishing")) {
            player.fishHook?.remove()
            player.sendMessage(Component.text("You stopped fishing due to inactivity.", NamedTextColor.RED))
        }

        val alertRaw = config.getString("actions.broadcast-alert")
        if (!alertRaw.isNullOrEmpty()) {
            val alertMsg = LegacyComponentSerializer.legacyAmpersand().deserialize(alertRaw.replace("%player%", player.name))
            Bukkit.broadcast(alertMsg)
        }

        if (config.getBoolean("actions.kick-player")) {
            val reasonRaw = config.getString("actions.kick-message", "AFK Fishing")
            player.kick(LegacyComponentSerializer.legacyAmpersand().deserialize(reasonRaw!!))
        }
    }
}