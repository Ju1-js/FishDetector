package com.ju1.fishDetector.managers

import com.ju1.fishDetector.FishDetector
import com.ju1.fishDetector.utils.MessageUtils
import java.time.Duration
import java.util.UUID
import kotlin.math.abs
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerQuitEvent

class FishingManager(private val plugin: FishDetector) : Listener {

    private data class FisherSession(
        var lastActiveTime: Long, var lastReelTime: Long, var lastYaw: Float, var lastPitch: Float
    )

    private val sessions = mutableMapOf<UUID, FisherSession>()
    private val warnedPlayers = mutableSetOf<UUID>()

    fun shutdown() {
        sessions.clear()
        warnedPlayers.clear()
    }

    fun getPunishmentCount(player: OfflinePlayer): Int {
        return plugin.dataManager.getPunishmentCount(player.uniqueId)
    }

    fun resetPunishmentCount(player: OfflinePlayer) {
        plugin.dataManager.setPunishmentCount(player.uniqueId, 0)
    }

    fun getActiveFishers(): Map<Player, String> {
        return Bukkit.getOnlinePlayers().filter { sessions.containsKey(it.uniqueId) }.associateWith {
            "Loc: ${it.location.blockX}, ${it.location.blockY}, ${it.location.blockZ}"
        }
    }

    private fun trackPlayer(player: Player) {
        val sess = sessions.computeIfAbsent(player.uniqueId) {
            FisherSession(
                System.currentTimeMillis(), System.currentTimeMillis(), player.yaw, player.pitch
            )
        }
        sess.lastReelTime = System.currentTimeMillis()
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFish(event: PlayerFishEvent) {
        if (!plugin.configManager.isEnabled) return

        if (event.state == PlayerFishEvent.State.CAUGHT_FISH || event.state == PlayerFishEvent.State.REEL_IN) {
            trackPlayer(event.player)
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
        val toPunish = mutableListOf<Player>()

        while (iterator.hasNext()) {
            val (uuid, session) = iterator.next()
            val player = Bukkit.getPlayer(uuid)

            if (player == null || !player.isOnline) {
                iterator.remove()
                warnedPlayers.remove(uuid)
                continue
            }

            // Cleanup: Hook gone and timeout passed
            if (player.fishHook == null) {
                if (now - session.lastActiveTime > config.cleanupTimeMillis) {
                    iterator.remove()
                    warnedPlayers.remove(uuid)
                }
                continue
            }

            // Check Rotation
            if (hasMoved(player, session, config.rotationSensitivity)) {
                resetSession(player, session)
                continue
            }

            // Filter hook-sitters
            if (now - session.lastReelTime > config.afkTimeMillis) {
                iterator.remove()
                warnedPlayers.remove(uuid)
                continue
            }

            // AFK Logic
            val timeInactive = now - session.lastActiveTime

            if (timeInactive > config.botAfkTimeMillis) {
                toPunish.add(player)
                iterator.remove()
            } else if (timeInactive > config.warningTimeMillis && warnedPlayers.add(uuid)) {
                sendWarning(player)
            }
        }
        if (toPunish.isNotEmpty()) {
            toPunish.forEach { punishPlayer(it) }
        }
    }

    private fun hasMoved(player: Player, session: FisherSession, sensitivity: Double): Boolean {
        val diffYaw = abs(player.yaw - session.lastYaw)
        val diffPitch = abs(player.pitch - session.lastPitch)
        return (diffYaw + diffPitch) > sensitivity
    }

    private fun resetSession(player: Player, session: FisherSession) {
        session.lastActiveTime = System.currentTimeMillis()
        session.lastYaw = player.yaw
        session.lastPitch = player.pitch
        warnedPlayers.remove(player.uniqueId)
    }

    private fun sendWarning(player: Player) {
        val config = plugin.configManager
        val titleMain = MessageUtils.parse(config.warningMessage)
        val titleSub = MessageUtils.parse(config.warningSubtitle)

        val title = Title.title(
            titleMain, titleSub, Title.Times.times(
                Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000)
            )
        )

        player.showTitle(title)
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f)
    }

    private fun punishPlayer(player: Player) {
        warnedPlayers.remove(player.uniqueId)
        plugin.dataManager.incrementPunishmentCount(player.uniqueId)

        val config = plugin.configManager

        val playerResolver = MessageUtils.getPlayerResolver(player)

        // Action: Cancel Fishing
        if (config.cancelFishing) {
            player.fishHook?.remove()
            val message = MessageUtils.parse(config.broadcastAlert, playerResolver)
            Bukkit.broadcast(message)
        }

        // Action: Kick
        if (config.kickPlayer) {
            val message = MessageUtils.parse(config.kickMessage, playerResolver)
            player.kick(message)
        }
    }
}