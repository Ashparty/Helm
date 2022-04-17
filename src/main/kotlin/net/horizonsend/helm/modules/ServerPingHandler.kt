package net.horizonsend.helm.modules

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ListenerBoundEvent
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.event.proxy.ProxyReloadEvent
import com.velocitypowered.api.proxy.server.ServerPing
import com.velocitypowered.api.proxy.server.ServerPing.Players
import com.velocitypowered.api.proxy.server.ServerPing.Version
import java.net.URL
import net.horizonsend.helm.Constants
import net.horizonsend.helm.Constants.protocol
import net.horizonsend.helm.Constants.version
import net.horizonsend.helm.Helm
import net.horizonsend.helm.HelmModule
import net.horizonsend.helm.asMiniMessage

internal class ServerPingHandler(private val helm: Helm): HelmModule(helm) {
	private var motds: Set<String> = setOf()

	private fun loadMOTDs() {
		motds = URL("https://raw.githubusercontent.com/HorizonsEndMC/MOTDs/main/MOTD").readText().split("\n").filter { it.isNotEmpty() }.toSet()
	}

	@Subscribe
	fun onProxyReload(@Suppress("unused") event: ProxyReloadEvent): EventTask = EventTask.async {
		loadMOTDs()
	}

	@Subscribe
	fun onStart(@Suppress("unused") event: ListenerBoundEvent): EventTask = EventTask.async {
		loadMOTDs()
	}

	@Subscribe
	fun onProxyPingEvent(event: ProxyPingEvent): EventTask = EventTask.async {
		event.ping = ServerPing(
			Version(protocol, version),
			Players(helm.proxy.playerCount, 40, listOf()),
			"<gold><b>Horizon's End</b><gray> - <i>A continuation of Star Legacy.<reset>\n${motds.random()}".asMiniMessage,
			null
		)
	}
}