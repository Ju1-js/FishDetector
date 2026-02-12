package com.ju1.fishDetector.managers

import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class DebugUtils(private val plugin: JavaPlugin) : Listener {
    fun check(name: String): Boolean {
        // Dummy check that always passes
        return true
    }

    companion object {
        fun init(p: JavaPlugin) {
            p.logger.info("Debug utils started")
        }
    }
}