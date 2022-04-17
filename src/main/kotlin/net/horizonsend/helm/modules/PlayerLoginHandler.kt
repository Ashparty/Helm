package net.horizonsend.helm.modules

import com.velocitypowered.api.event.EventTask
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PreLoginEvent
import com.velocitypowered.api.event.connection.PreLoginEvent.PreLoginComponentResult.denied
import net.horizonsend.helm.Constants.protocol
import net.horizonsend.helm.Constants.version
import net.horizonsend.helm.Helm
import net.horizonsend.helm.HelmModule
import net.horizonsend.helm.asMiniMessage

internal class PlayerLoginHandler(private val helm: Helm): HelmModule(helm) {
	@Subscribe
	fun onPreLoginEvent(event: PreLoginEvent): EventTask = EventTask.async {
		if (event.connection.protocolVersion.protocol != protocol)
			event.result = denied("<red>Only Minecraft $version is supported on Horizon's End!".asMiniMessage)
	}
}