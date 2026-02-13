package com.ju1.fishDetector.managers

import com.ju1.fishDetector.FishDetector
import java.time.Duration
import java.util.UUID
import kotlin.math.abs
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
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
            var lastActiveTime: Long,
            var lastReelTime: Long,
            var lastYaw: Float,
            var lastPitch: Float
    )

    private val sessions = mutableMapOf<UUID, FisherSession>()
    private val warnedPlayers = mutableSetOf<UUID>()

    private val mm = MiniMessage.miniMessage()

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
        return Bukkit.getOnlinePlayers()
                .filter { sessions.containsKey(it.uniqueId) && it.fishHook != null }
                .associateWith {
                    "Loc: ${it.location.blockX}, ${it.location.blockY}, ${it.location.blockZ}"
                }
    }

    private fun trackPlayer(player: Player): FisherSession {
        var sess = sessions.computeIfAbsent(player.uniqueId) {
            FisherSession(System.currentTimeMillis(), System.currentTimeMillis(),player.yaw, player.pitch)
        }
        sess.lastReelTime = System.currentTimeMillis()
        return sess
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
        // plugin.logger.info("Fishing manager tick. Current fishers: ${sessions.keys.size}")
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

            // If not fishing anymore (hook gone), check cleanup timeout
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

        val title =
                Title.title(
                        mm.deserialize(config.warningMessage),
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

    private fun punishPlayer(player: Player) {
        warnedPlayers.remove(player.uniqueId)
        plugin.dataManager.incrementPunishmentCount(player.uniqueId)

        val config = plugin.configManager

        // Action: Cancel Fishing
        if (config.cancelFishing) {
            player.fishHook?.remove()
            val message =
                    mm.deserialize(
                            config.broadcastAlert,
                            Placeholder.component("player", Component.text(player.name))
                    )
            Bukkit.broadcast(message)
        }

        // Action: Kick
        if (config.kickPlayer) {
            player.kick(mm.deserialize(config.kickMessage))
        }
    }
}
