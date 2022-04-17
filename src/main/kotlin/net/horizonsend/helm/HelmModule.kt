package net.horizonsend.helm

@Suppress("leakingthis")
internal abstract class HelmModule(helm: Helm) {
	init {
		helm.proxy.eventManager.register(helm, this)
	}
}