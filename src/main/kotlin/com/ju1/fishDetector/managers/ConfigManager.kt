package com.ju1.fishDetector.managers

import com.ju1.fishDetector.FishDetector
import org.bukkit.configuration.file.FileConfiguration

class ConfigManager(private val plugin: FishDetector) {

    var isEnabled: Boolean = false
        private set

    var afkTimeMillis: Long = 0
    var botAfkTimeMillis: Long = 0
    var warningTimeMillis: Long = 0
    var cleanupTimeMillis: Long = 0
    var rotationSensitivity: Double = 0.01
    var checkInterval: Long = 100
    var pageSize: Int = 10

    // Actions
    var cancelFishing: Boolean = true
    var kickPlayer: Boolean = false

    // Messages (Strings)
    var warningMessage: String = ""
    var broadcastAlert: String = ""
    var kickMessage: String = ""

    fun load() {
        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        val c = plugin.config

        isEnabled = c.getBoolean("enabled")
        reload(c)
    }

    fun reload(c: FileConfiguration) {
        afkTimeMillis = c.getLong("afk-time-seconds", 30) * 1000L
        botAfkTimeMillis = c.getLong("bot-afk-time-seconds", 600) * 1000L
        warningTimeMillis = c.getLong("warning-time-seconds", 570) * 1000L
        cleanupTimeMillis = c.getLong("cleanup-timeout-seconds", 1200) * 1000L
        rotationSensitivity = c.getDouble("rotation-threshold", 0.01)
        checkInterval = c.getLong("check-interval-ticks", 100)
        pageSize = c.getInt("page-size", 10)

        cancelFishing = c.getBoolean("actions.cancel-fishing", true)
        kickPlayer = c.getBoolean("actions.kick-player", false)

        warningMessage =
            c.getString("actions.warning-message", "<red><bold>Are you afk? <yellow>Move your mouse!")!!
        broadcastAlert =
            c.getString("actions.broadcast-alert", "<yellow>%player% stopped fishing due to inactivity.")!!
        kickMessage = c.getString("actions.kick-message", "<red>No AFK Fishing allowed!")!!
    }

    fun toggleState(): Boolean {
        plugin.reloadConfig()
        reload(plugin.config)
        isEnabled = !isEnabled
        plugin.config.set("enabled", isEnabled)
        plugin.saveConfig()
        return isEnabled
    }
}