package com.ju1.fishDetector.managers

import com.ju1.fishDetector.FishDetector
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

class DataManager(private val plugin: FishDetector) {

    private val dataFile = File(plugin.dataFolder, "data.yml")
    private lateinit var dataConfig: YamlConfiguration

    fun load() {
        if (!dataFile.exists()) {
            try {
                dataFile.parentFile.mkdirs() // Ensure folder exists
                dataFile.createNewFile()
            } catch (e: Exception) {
                plugin.logger.severe("Could not create data.yml!")
                e.printStackTrace()
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile)
    }

    // Sync save for onDisable
    fun save() {
        try {
            dataConfig.save(dataFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save data.yml!")
            e.printStackTrace()
        }
    }

    // Async save for runtime usage
    fun saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            save()
        })
    }

    fun getPunishmentCount(uuid: UUID): Int {
        return dataConfig.getInt("players.$uuid.punishments", 0)
    }

    fun getAllPunishedPlayers(): Map<String, Int> {
        val section = dataConfig.getConfigurationSection("players") ?: return emptyMap()
        val results = mutableMapOf<String, Int>()

        for (uuidStr in section.getKeys(false)) {
            val count = dataConfig.getInt("players.$uuidStr.punishments", 0)
            if (count > 0) {
                // Try to resolve name, fallback to UUID if unknown
                val name = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr)).name ?: uuidStr
                results[name] = count
            }
        }
        return results
    }

    fun setPunishmentCount(uuid: UUID, count: Int) {
        dataConfig.set("players.$uuid.punishments", count)
        saveAsync() // Use async save here
    }

    fun incrementPunishmentCount(uuid: UUID) {
        val current = getPunishmentCount(uuid)
        setPunishmentCount(uuid, current + 1)
    }
}