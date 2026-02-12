package com.ju1.fishDetector

import com.ju1.fishDetector.commands.CommandRegistry
import com.ju1.fishDetector.managers.ConfigManager
import com.ju1.fishDetector.managers.DebugUtils
import com.ju1.fishDetector.managers.FishingManager
import org.bukkit.plugin.java.JavaPlugin

class FishDetector : JavaPlugin() {

    lateinit var configManager: ConfigManager
    lateinit var fishingManager: FishingManager

    override fun onEnable() {
        DebugUtils.init(this)
        configManager = ConfigManager(this)
        fishingManager = FishingManager(this)

        configManager.load()
        server.pluginManager.registerEvents(fishingManager, this)

        val interval = configManager.checkInterval
        server.scheduler.runTaskTimer(this, Runnable {
            fishingManager.tick()
        }, interval, interval)

        CommandRegistry(this).register()
        logger.info("FishDetector has been enabled.")
    }

    override fun onDisable() {
        fishingManager.shutdown()
    }
}