package net.horizonsend.helm

import co.aikar.commands.InvalidCommandArgument
import co.aikar.commands.VelocityCommandManager
import com.google.inject.Inject
import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.EventTask.async
import com.velocitypowered.api.event.ResultedEvent.ComponentResult
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.event.connection.PreLoginEvent
import com.velocitypowered.api.event.connection.PreLoginEvent.PreLoginComponentResult
import com.velocitypowered.api.event.proxy.ListenerBoundEvent
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import com.velocitypowered.api.proxy.server.ServerPing
import com.velocitypowered.api.proxy.server.ServerPing.Players
import com.velocitypowered.api.proxy.server.ServerPing.SamplePlayer
import com.velocitypowered.api.proxy.server.ServerPing.Version
import com.velocitypowered.api.scheduler.ScheduledTask
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.TimeUnit.MINUTES
import javax.security.auth.login.LoginException
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.readText
import net.dv8tion.jda.api.JDABuilder.createLight
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
class HEProxy @Inject constructor(
	private val server: ProxyServer,
	private val logger: Logger,
	@DataDirectory private val dataDirectory: Path
) {
	private var motds: Set<String> = setOf()
	private lateinit var motdTask: ScheduledTask

	@Subscribe
	fun onPreLoginEvent(event: PreLoginEvent): EventTask = async {
		if (event.connection.protocolVersion.protocol != 758)
			event.result = PreLoginComponentResult.denied(miniMessage().deserialize("<red>Only Minecraft 1.18.2 is supported on Horizon's End!"))
	}

	@Subscribe
	fun onLoginEvent(event: LoginEvent): EventTask = async {
		if (server.playerCount > 30 && !event.player.hasPermission("helm.maxPlayerBypass"))
			event.result = ComponentResult.denied(miniMessage().deserialize("<yellow>The server is full!"))
	}

	@Subscribe
	fun onProxyPingEvent(event: ProxyPingEvent): EventTask = async {
		event.ping = ServerPing(
			Version(758, "1.18.2"),
			Players(server.playerCount, 30, server.allPlayers.map { SamplePlayer(it.username, it.uniqueId) }),
			miniMessage().deserialize("<gold><b>Horizon's End</b><gray> - <i>A continuation of Star Legacy.<reset>\n${motds.random()}"),
			null
		)
	}

	@Subscribe
	fun onStart(event: ListenerBoundEvent): EventTask = async {
		val taskBuilder = server.scheduler.buildTask(this) {
			motds = URL("https://raw.githubusercontent.com/HorizonsEndMC/MOTDs/main/MOTD").readText().split("\n").filter { it.isNotEmpty() }.toSet()
		}

		taskBuilder.repeat(5L, MINUTES)
		motdTask = taskBuilder.schedule()

		val commandManager = VelocityCommandManager(server, this)

		commandManager.commandCompletions.registerCompletion("servers") {
			server.allServers.map { it.serverInfo.name }
		}

		commandManager.commandCompletions.registerCompletion("targets") {
			val result = mutableSetOf("*")

			result.addAll(server.allPlayers.map { it.username })
			result.addAll(server.allServers.map { "@${it.serverInfo.name}" })

			result
		}

		commandManager.commandContexts.registerContext(RegisteredServer::class.java) {
			val serverName = it.popFirstArg()
			val result = server.getServer(serverName).orElse(null)

			if (result == null) {
				it.sender.sendMessage(miniMessage().deserialize("<yellow>Server <white>\"$serverName\"</white> does not exist!"))
				throw InvalidCommandArgument(false)
			}

			result
		}

		commandManager.commandContexts.registerContext(Collection::class.java) {
			val selector = it.popFirstArg()

			when (selector.first()) {
				'*' -> server.allPlayers
				'@' -> {
					val serverName = selector.substring(1)
					val server = server.getServer(serverName).orElse(null)

					if (server == null) {
						it.sender.sendMessage(miniMessage().deserialize("<yellow>Server <white>\"$serverName\"</white> does not exist!"))
						throw InvalidCommandArgument(false)
					}

					server.playersConnected
				}
				else -> {
					val player = server.getPlayer(selector).orElse(null)

					if (player == null) {
						it.sender.sendMessage(miniMessage().deserialize("<yellow>Player <white>\"$selector\"</white> is not online!"))
						throw InvalidCommandArgument(false)
					}

					setOf(player)
				}
			}
		}

		commandManager.registerCommand(Move())
		commandManager.registerCommand(Server())

		loadJDA()
	}

	private fun loadJDA() {
		val tokenFile = dataDirectory.createDirectories().resolve("token")

		if (!tokenFile.exists()) tokenFile.createFile()

		try {
			createLight(tokenFile.readText(), emptySet()).build()
		} catch (_: LoginException) {
			logger.warn("Failed to connect to discord, server status will not be shown in discord.")
		}
	}
}