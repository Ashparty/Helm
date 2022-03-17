package net.horizonsend.helm.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.CommandPermission
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Description
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.server.RegisteredServer
import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage

@CommandAlias("move")
class Move: BaseCommand() {
	@Default
	@CommandCompletion("@targets @servers")
	@CommandPermission("helm.move")
	@Description("Move players to servers.")
	@Suppress("unused") // Entrypoint (Command)
	fun move(source: Player, targetPlayers: Collection<Player>, targetServer: RegisteredServer) {
		targetPlayers.forEach {
			it.sendMessage(miniMessage().deserialize("<aqua>You have been moved to <white>\"${targetServer.serverInfo.name}\"</white> by <white>\"${source.username}\"</white>!"))
			it.createConnectionRequest(targetServer).fireAndForget()
		}
	}
}