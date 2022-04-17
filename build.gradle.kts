import org.gradle.jvm.toolchain.JavaLanguageVersion.of

plugins {
	kotlin("jvm") version "1.6.10"
	id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
	maven("https://s01.oss.sonatype.org/content/repositories/snapshots/") // MiniMessage
	maven("https://papermc.io/repo/repository/maven-public/") // Velocity
	maven("https://repo.aikar.co/content/groups/aikar/") // Annotation Command Framework (Velocity)
	mavenCentral()
}

dependencies {
	compileOnly("com.velocitypowered:velocity-api:3.1.1")

	implementation("net.kyori:adventure-text-minimessage:4.10.1")
	implementation("co.aikar:acf-velocity:0.5.1-SNAPSHOT")
}

tasks {
	compileJava {
		options.compilerArgs.add("-parameters")
		options.isFork = true
	}

	compileKotlin {
		kotlinOptions.javaParameters = true
	}

	shadowJar {
		archiveFileName.set("../Helm.jar")
	}
}

java.toolchain.languageVersion.set(of(17))