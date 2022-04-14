package net.horizonsend.helm

import co.aikar.commands.InvalidCommandArgument
import co.aikar.commands.VelocityCommandManager
import com.google.inject.Inject
import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.EventTask.async
import com.velocitypowered.api.event.ResultedEvent.ComponentResult
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.connection.PreLoginEvent
import com.velocitypowered.api.event.connection.PreLoginEvent.PreLoginComponentResult
import com.velocitypowered.api.event.player.KickedFromServerEvent
import com.velocitypowered.api.event.player.KickedFromServerEvent.DisconnectPlayer
import com.velocitypowered.api.event.player.KickedFromServerEvent.RedirectPlayer
import com.velocitypowered.api.event.player.ServerConnectedEvent
import com.velocitypowered.api.event.proxy.ListenerBoundEvent
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.event.proxy.ProxyReloadEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerPing
import com.velocitypowered.api.proxy.server.ServerPing.Players
import com.velocitypowered.api.proxy.server.ServerPing.SamplePlayer
import com.velocitypowered.api.proxy.server.ServerPing.Version
import com.velocitypowered.api.scheduler.ScheduledTask
import java.net.URL
import java.util.concurrent.TimeUnit.SECONDS
import net.horizonsend.helm.commands.Move
import net.horizonsend.helm.commands.Server
import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage
import org.slf4j.Logger

@Plugin(
	id = "helm",
	name = "Helm",
	version = "1.0.0",
	description = "Horizon's End Proxy Plugin",
	url = "https://horizonsend.net",
	authors = ["PeterCrawley"]
)
class Helm @Inject constructor(
	private val proxy: ProxyServer,
	logger: Logger
) {
	private var motds: Set<String> = setOf()

	private val limbo: RegisteredServer? = proxy.getServer("limbo").orElseGet(null)

	@Subscribe
	fun onPreLoginEvent(event: PreLoginEvent): EventTask = async {
		if (event.connection.protocolVersion.protocol != 758)
			event.result = PreLoginComponentResult.denied(miniMessage().deserialize("<red>Only Minecraft 1.18.2 is supported on Horizon's End!"))
	}

	@Subscribe
	fun onLoginEvent(event: LoginEvent): EventTask = async {
		if (proxy.playerCount > 30 && !event.player.hasPermission("helm.maxPlayerBypass")) {
			event.result = ComponentResult.denied(miniMessage().deserialize("<yellow>The server is full!"))
			return@async
		}
	}

	@Subscribe
	fun onProxyPingEvent(event: ProxyPingEvent): EventTask = async {
		event.ping = ServerPing(
			Version(758, "1.18.2"),
			Players(proxy.playerCount, 30, proxy.allPlayers.map { SamplePlayer(it.username, it.uniqueId) }),
			miniMessage().deserialize("<gold><b>Horizon's End</b><gray> - <i>A continuation of Star Legacy.<reset>\n${motds.random()}"),
			null
		)
	}

	private fun transferToServer(player: Player, server: RegisteredServer) {
		player.createConnectionRequest(server).connect().handleAsync { result, _ ->
			if (!result.isSuccessful) transferToServer(player, server)
		}
	}

	@Subscribe
	fun onKickedFromServerEvent(event: KickedFromServerEvent): EventTask = async {
		if (limbo == null || event.server == limbo) {
			event.result = DisconnectPlayer.create(miniMessage().deserialize("<red>Unexpectedly disconnected from <white>${event.server.serverInfo.name}</white>."))
			return@async
		}

		event.result = RedirectPlayer.create(limbo, miniMessage().deserialize("<aqua><red><b>Welcome to Limbo!</b></red>\nAs you're here, the server is restarting, or something broke.\n<grey><i>How am I meant to know? I'm just a pre-written message.</i></grey>\nAnyway, we will try to get you back where you were as soon as we can.\nHowever you can switch to another server using the <white>/server</white> command."))

		lateinit var task: ScheduledTask
		task = proxy.scheduler.buildTask(this) {
			try { event.server.ping().join() } catch (_: Exception) {} finally { // Ping the server, ignore failure, transfer the player once we succeed.
				task.cancel()
				transferToServer(event.player, event.server)
			}
		}.repeat(1, SECONDS).schedule()
	}

	fun formatServerName(server: RegisteredServer): String =
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
	fun onProxyReload(@Suppress("unused") event: ProxyReloadEvent): EventTask = async {
		loadMOTDs()
	}

	@Subscribe
	fun onStart(@Suppress("unused") event: ListenerBoundEvent): EventTask = async {
		loadMOTDs()

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
	}

	private fun loadMOTDs() {
		motds = URL("https://raw.githubusercontent.com/HorizonsEndMC/MOTDs/main/MOTD").readText().split("\n").filter { it.isNotEmpty() }.toSet()
	}
}