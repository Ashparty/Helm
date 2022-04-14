import org.gradle.jvm.toolchain.JavaLanguageVersion.of

plugins {
    kotlin("jvm") version "1.6.10"
    kotlin("kapt") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

repositories {
    maven("https://papermc.io/repo/repository/maven-public/")
    maven("https://repo.aikar.co/content/groups/aikar/")
    mavenCentral()
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.1.1")

    kapt("com.velocitypowered:velocity-api:3.1.1")

    implementation("net.kyori:adventure-text-minimessage:4.10.0")
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