package com.ju1.fishDetector.managers

import com.ju1.fishDetector.FishDetector
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.persistence.PersistentDataType
import java.time.Duration
import java.util.UUID
import kotlin.math.abs

class FishingManager(private val plugin: FishDetector) : Listener {

    private data class FisherSession(
        var lastActiveTime: Long,
        var lastYaw: Float,
        var lastPitch: Float
    )

    private val sessions = mutableMapOf<UUID, FisherSession>()
    private val warnedPlayers = mutableSetOf<UUID>()
    private val kickCountKey = NamespacedKey(plugin, "afk_fish_p_count")

    private val mm = MiniMessage.miniMessage()

    fun shutdown() {
        sessions.clear()
        warnedPlayers.clear()
    }

    fun getPunishmentCount(player: Player): Int {
        return player.persistentDataContainer.get(kickCountKey, PersistentDataType.INTEGER) ?: 0
    }

    fun getActiveFishers(): Map<Player, String> {
        return Bukkit.getOnlinePlayers()
            .filter { sessions.containsKey(it.uniqueId) && it.fishHook != null }
            .associateWith {
                "Loc: ${it.location.blockX}, ${it.location.blockY}, ${it.location.blockZ}"
            }
    }

    private fun trackPlayer(player: Player) {
        sessions.computeIfAbsent(player.uniqueId) {
            FisherSession(System.currentTimeMillis(), player.yaw, player.pitch)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFish(event: PlayerFishEvent) {
        if (!plugin.configManager.isEnabled) return

        // If they cast or caught a fish, they are active; track/reset them
        // Testing with only CAUGHT_FISH
        if (event.state == PlayerFishEvent.State.CAUGHT_FISH) { // event.state == PlayerFishEvent.State.FISHING ||
            trackPlayer(event.player)
            resetSession(event.player)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        sessions.remove(event.player.uniqueId)
        warnedPlayers.remove(event.player.uniqueId)
    }

    fun tick() {
        if (!plugin.configManager.isEnabled || sessions.isEmpty()) return

        val now = System.currentTimeMillis()
        val config = plugin.configManager

        val iterator = sessions.iterator()

        while (iterator.hasNext()) {
            val (uuid, session) = iterator.next()
            val player = Bukkit.getPlayer(uuid)

            if (player == null || !player.isOnline) {
                iterator.remove()
                warnedPlayers.remove(uuid)
                continue
            }

            // Check Rotation
            if (hasMoved(player, session, config.rotationSensitivity)) {
                resetSession(player, session)
                continue
            }

            // If not fishing anymore (hook gone), check cleanup timeout
            if (player.fishHook == null) {
                if (now - session.lastActiveTime > config.cleanupTimeMillis) {
                    iterator.remove()
                    warnedPlayers.remove(uuid)
                }
                continue
            }

            // AFK Logic
            val timeInactive = now - session.lastActiveTime

            if (timeInactive > config.afkTimeMillis) {
                punishPlayer(player)
                iterator.remove()
            } else if (timeInactive > config.warningTimeMillis && warnedPlayers.add(uuid)) {
                sendWarning(player)
            }
        }
    }

    private fun hasMoved(player: Player, session: FisherSession, sensitivity: Double): Boolean {
        val diffYaw = abs(player.yaw - session.lastYaw)
        val diffPitch = abs(player.pitch - session.lastPitch)
        return (diffYaw + diffPitch) > sensitivity
    }

    private fun resetSession(player: Player, session: FisherSession? = null) {
        val s = session ?: sessions[player.uniqueId] ?: return
        s.lastActiveTime = System.currentTimeMillis()
        s.lastYaw = player.yaw
        s.lastPitch = player.pitch
        warnedPlayers.remove(player.uniqueId)
    }

    private fun sendWarning(player: Player) {
        val config = plugin.configManager

        val title = Title.title(
            mm.deserialize(config.warningMessage),
            Component.text("You will be kicked shortly.", NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000))
        )

        player.showTitle(title)
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f)
    }

    private fun punishPlayer(player: Player) {
        warnedPlayers.remove(player.uniqueId)

        // Increment Count
        val pdc = player.persistentDataContainer
        val newCount = getPunishmentCount(player) + 1
        pdc.set(kickCountKey, PersistentDataType.INTEGER, newCount)

        val config = plugin.configManager

        // Action: Cancel Fishing
        if (config.cancelFishing) {
            player.fishHook?.remove()
            player.sendMessage(mm.deserialize(config.broadcastAlert.replace("%player%", player.name)))
        }

        // Action: Kick
        if (config.kickPlayer) {
            player.kick(mm.deserialize(config.kickMessage))
        }
    }
}