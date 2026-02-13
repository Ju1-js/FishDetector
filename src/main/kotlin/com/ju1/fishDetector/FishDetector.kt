package com.ju1.fishDetector

import com.ju1.fishDetector.commands.CommandRegistry
import com.ju1.fishDetector.managers.ConfigManager
import com.ju1.fishDetector.managers.DataManager
import com.ju1.fishDetector.utils.DebugUtils
import com.ju1.fishDetector.managers.FishingManager
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

class FishDetector : JavaPlugin() {

    lateinit var configManager: ConfigManager
    lateinit var fishingManager: FishingManager
    lateinit var dataManager: DataManager
    private var task: BukkitTask? = null

    override fun onEnable() {
        DebugUtils.init(this)
        configManager = ConfigManager(this)
        fishingManager = FishingManager(this)
        dataManager = DataManager(this)

        dataManager.load()
        configManager.load()
        server.pluginManager.registerEvents(fishingManager, this)

        CommandRegistry(this).register()
        updateTask()
        logger.info("FishDetector has been enabled.")
    }

    override fun onDisable() {
        task?.cancel()
        task = null
        fishingManager.shutdown()
    }

    fun updateTask() {
        task?.cancel()
        val interval = configManager.checkInterval
        task = server.scheduler.runTaskTimer(this, Runnable {
            fishingManager.tick()
        }, interval, interval)
    }
}