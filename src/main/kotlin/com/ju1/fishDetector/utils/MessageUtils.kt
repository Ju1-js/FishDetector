package com.ju1.fishDetector.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

object MessageUtils {
    private val mm = MiniMessage.miniMessage()

    /**
     * Creates a standard TagResolver for a player.
     * usage: <player> will be replaced with the player's name.
     */
    fun getPlayerResolver(player: OfflinePlayer): TagResolver {
        val name = player.name ?: "Unknown"
        return TagResolver.resolver(
            "player", Tag.selfClosingInserting(Component.text(name))
        )
    }

    fun getPlayerResolver(player: Player): TagResolver {
        return TagResolver.resolver(
            "player", Tag.selfClosingInserting(player.displayName())
        )
    }

    /**
     * Parses a string into a Component using the provided resolvers.
     */
    fun parse(message: String, vararg resolvers: TagResolver): Component {
        return mm.deserialize(message, *resolvers)
    }

    /**
     * Extension function to send parsed messages to any sender
     */
    fun CommandSender.sendRichMessage(message: String, vararg resolvers: TagResolver) {
        this.sendMessage(parse(message, *resolvers))
    }
}