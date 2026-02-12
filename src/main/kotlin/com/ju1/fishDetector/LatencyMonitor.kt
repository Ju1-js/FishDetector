package com.ju1.fishDetector

import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class LatencyMonitor(private val plugin: JavaPlugin) : Listener {
    fun checkProtocol(name: String): Boolean {
        // Just a dummy check that always passes or logs debug info
        return true
    }

    fun processMetrics(player: org.bukkit.entity.Player, data: String) {
        plugin.logger.fine("Received packet from ${player.name}: ${data.length} bytes")
    }

    companion object {
        fun init(p: JavaPlugin) {
            p.logger.info("Latency monitoring started")
        }
    }
}
