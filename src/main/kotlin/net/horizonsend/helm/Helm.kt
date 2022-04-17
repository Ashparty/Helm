package net.horizonsend.helm

import co.aikar.commands.InvalidCommandArgument
import co.aikar.commands.VelocityCommandManager
import com.google.inject.Inject
import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.EventTask.async
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN
import com.velocitypowered.api.event.connection.PreLoginEvent
import com.velocitypowered.api.event.connection.PreLoginEvent.PreLoginComponentResult
import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.event.player.KickedFromServerEvent.DisconnectPlayer
import com.velocitypowered.api.event.player.KickedFromServerEvent.RedirectPlayer
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.event.proxy.ListenerBoundEvent
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import net.horizonsend.helm.commands.Move
import net.horizonsend.helm.commands.Server
import net.horizonsend.helm.modules.PlayerLoginHandler
import net.horizonsend.helm.modules.ServerPingHandler
import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage
import org.slf4j.Logger

class Helm @Inject constructor(
	internal val proxy: ProxyServer,
	logger: Logger
) {
	private val limbo: RegisteredServer? = proxy.getServer("limbo").orElseGet(null)

	@Subscribe
	fun onLoginEvent(event: PlayerChooseInitialServerEvent): EventTask = async {
		if (proxy.playerCount >= 40 && !event.player.hasPermission("helm.maxPlayerBypass")) {
			event.setInitialServer(limbo)
		}
	}

	@Subscribe
	fun onKickedFromServerEvent(event: KickedFromServerEvent): EventTask = async {
		if (limbo == null || event.server == limbo) {
			event.result = DisconnectPlayer.create(miniMessage().deserialize("<red>Unexpectedly disconnected from <white>${event.server.serverInfo.name}</white>."))
			return@async
		}

		event.result = RedirectPlayer.create(limbo, miniMessage().deserialize("<aqua><red><b>Welcome to Limbo!</b></red>\nAs you're here, the server is restarting, or something broke.\n<grey><i>How am I meant to know? I'm just a pre-written message.</i></grey>\nAnyway, we will try to get you back where you were as soon as we can.\nHowever you can switch to another server using the <white>/server</white> command."))
	}

	private fun formatServerName(server: RegisteredServer): String =
		when (val name = server.serverInfo.name) {
			"survival" -> "<red>Survival</red>"
			"creative" -> "<green>Creative</green>"
			"limbo" -> "<yellow>Limbo</yellow>"
			else -> name
		}

	@Subscribe
	fun onPlayerConnectToServer(event: ServerConnectedEvent) {
		val previousServer = event.previousServer.orElse(null)

		if (previousServer == null)
			proxy.sendMessage(miniMessage().deserialize("<gray>[<green>+</green> ${formatServerName(event.server)}]</gray> ${event.player.username} <i><dark_gray>${proxy.playerCount} online now."))

		else
			proxy.sendMessage(miniMessage().deserialize("<gray>[${formatServerName(previousServer)} > ${formatServerName(event.server)}]</gray> ${event.player.username}"))
	}

	@Subscribe
	fun onPlayerDisconnect(event: DisconnectEvent) {
		if (event.loginStatus != SUCCESSFUL_LOGIN) return

		proxy.sendMessage(miniMessage().deserialize("<gray>[<red>-</red> ${formatServerName(event.player.currentServer.orElse(null).server)}]</gray> ${event.player.username} <i><dark_gray>${proxy.playerCount} online now."))
	}

	@Subscribe
	fun onStart(@Suppress("unused") event: ListenerBoundEvent): EventTask = async {
		val commandManager = VelocityCommandManager(proxy, this)

		commandManager.commandCompletions.registerCompletion("servers") {
			proxy.allServers.map { it.serverInfo.name }
		}

		commandManager.commandCompletions.registerCompletion("targets") {
			val result = mutableSetOf("*")

			result.addAll(proxy.allPlayers.map { it.username })
			result.addAll(proxy.allServers.map { "@${it.serverInfo.name}" })

			result
		}

		commandManager.commandContexts.registerContext(RegisteredServer::class.java) {
			val serverName = it.popFirstArg()
			val result = proxy.getServer(serverName).orElse(null)

			if (result == null) {
				it.sender.sendMessage(miniMessage().deserialize("<yellow>Server <white>$serverName</white> does not exist!"))
				throw InvalidCommandArgument(false)
			}

			result
		}

		commandManager.commandContexts.registerContext(Collection::class.java) {
			val selector = it.popFirstArg()

			when (selector.first()) {
				'*' -> proxy.allPlayers
				'@' -> {
					val serverName = selector.substring(1)
					val server = proxy.getServer(serverName).orElse(null)

					if (server == null) {
						it.sender.sendMessage(miniMessage().deserialize("<yellow>Server <white>$serverName</white> does not exist!"))
						throw InvalidCommandArgument(false)
					}

					server.playersConnected
				}
				else -> {
					val player = proxy.getPlayer(selector).orElse(null)

					if (player == null) {
						it.sender.sendMessage(miniMessage().deserialize("<yellow>Player <white>$selector</white> is not online!"))
						throw InvalidCommandArgument(false)
					}

					setOf(player)
				}
			}
		}

		commandManager.registerCommand(Move())
		commandManager.registerCommand(Server())

		ServerPingHandler(this)
		PlayerLoginHandler(this)
	}
}