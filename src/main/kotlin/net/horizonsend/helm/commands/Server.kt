package net.horizonsend.helm.commands

import co.aikar.commands.BaseCommand
import co.aikar.commands.annotation.CommandAlias
import co.aikar.commands.annotation.CommandCompletion
import co.aikar.commands.annotation.Default
import co.aikar.commands.annotation.Description
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.server.RegisteredServer
import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage

@CommandAlias("server")
class Server: BaseCommand() {
	@Default
	@CommandCompletion("@servers")
	@Description("Switch to a server.")
	@Suppress("unused") // Entrypoint (Command)
	fun server(source: Player, targetServer: RegisteredServer) {
		if (!source.hasPermission("helm.server.${targetServer.serverInfo.name}")) {
			source.sendMessage(miniMessage().deserialize("<red>You do not have permission to go to <white>${targetServer.serverInfo.name}</white>."))
			return
		}

		source.createConnectionRequest(targetServer).fireAndForget()
	}
}