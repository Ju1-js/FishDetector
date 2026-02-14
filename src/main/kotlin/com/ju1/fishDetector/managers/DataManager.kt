package com.ju1.fishDetector.managers

import com.ju1.fishDetector.FishDetector
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

class DataManager(private val plugin: FishDetector) {

    private val dataFile = File(plugin.dataFolder, "data.yml")
    private lateinit var dataConfig: YamlConfiguration

    fun load() {
        if (!dataFile.exists()) {
            try {
                dataFile.parentFile.mkdirs()
                dataFile.createNewFile()
            } catch (e: Exception) {
                plugin.logger.severe("Could not create data.yml!")
                e.printStackTrace()
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile)
    }

    fun save() {
        try {
            dataConfig.save(dataFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save data.yml!")
            e.printStackTrace()
        }
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
                val name = dataConfig.getString("players.$uuidStr.name") ?: uuidStr
                results[name] = count
            }
        }
        return results
    }

    fun setPunishmentCount(uuid: UUID, name: String?, count: Int) {
        dataConfig.set("players.$uuid.punishments", count)
        if (name != null) {
            dataConfig.set("players.$uuid.name", name)
        }
    }

    fun incrementPunishmentCount(uuid: UUID, name: String?) {
        val current = getPunishmentCount(uuid)
        setPunishmentCount(uuid, name, current + 1)
    }
}