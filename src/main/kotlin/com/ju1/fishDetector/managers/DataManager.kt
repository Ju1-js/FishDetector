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

    fun setPunishmentCount(uuid: UUID, count: Int) {
        dataConfig.set("players.$uuid.punishments", count)
        save()
    }

    fun incrementPunishmentCount(uuid: UUID) {
        val current = getPunishmentCount(uuid)
        setPunishmentCount(uuid, current + 1)
    }
}