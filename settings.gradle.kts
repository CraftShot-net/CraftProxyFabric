pluginManagement {
	repositories {
		mavenLocal()
		mavenCentral()
		gradlePluginPortal()
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
		maven("https://maven.parchmentmc.org") { name = "ParchmentMC" }
	}
	includeBuild("build-logic")
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
	// Keep in sync with the `stonecutter` version in gradle/libs.versions.toml!
	id("dev.kikugie.stonecutter") version "0.10-beta.1"
	id("dev.kikugie.loom-back-compat") version "0.4.1"
}

stonecutter {
	create(rootProject) {
		fun match(version: String, vararg loaders: String) =
			loaders.forEach { version("$version-$it", version).buildscript = "build.$it.gradle.kts" }

		// Supported versions, picked from the most played Fabric modpack versions on Modrinth.
		match("26.3", "fabric")
		match("26.2", "fabric")
		match("26.1.2", "fabric")
		match("1.21.11", "fabric")
		match("1.21.1", "fabric")
		match("1.20.1", "fabric")

		vcsVersion = "26.3-fabric"
	}
}

rootProject.name = "craftproxy"
