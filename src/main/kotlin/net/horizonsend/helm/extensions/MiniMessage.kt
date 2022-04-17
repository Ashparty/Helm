package net.horizonsend.helm

import net.kyori.adventure.text.minimessage.MiniMessage.miniMessage

val String.asMiniMessage get() = miniMessage().deserialize(this)

