plugins {
	id("mod-platform")
	id("dev.kikugie.loom-back-compat")
}

stonecutter {
	// Mojang renamed ResourceLocation to Identifier in 1.21.11.
	replacements.string(current.parsed >= "1.21.11") {
		replace("ResourceLocation", "Identifier")
	}
}

platform {
	loader = "fabric"
	dependencies {
		required("minecraft") {
			fabricLikeVersionRange = sc.properties.rawOrNull("deps.minecraft_range")?.to<String>() ?: prop("deps.minecraft")
		}
		required("fabric-api") {
			slug("fabric-api")
			fabricLikeVersionRange = ">=${prop("deps.fabric-api")}"
		}
		required("fabricloader") {
			fabricLikeVersionRange = ">=${prop("deps.fabric-loader")}"
		}
	}
}

loom {
	val devJvmArgs = propsList("mod", "dev_jvm_args") +
		propsList("mod", "dev_jvm_args_mixin_debug")
	runs.named("client") {
		client()
		generateRunConfig.set(true)
		// -Pdev.runDir / -Pdev.account allow a second client side by side (e.g. run2 + alt).
		runDirectory.set(layout.projectDirectory.dir(providers.gradleProperty("dev.runDir").getOrElse("run")))
		jvmArguments.addAll(devJvmArgs)
		// Log in with a real Microsoft account through DevAuth (see README).
		jvmArguments.add("-Ddevauth.enabled=true")
		jvmArguments.add("-Ddevauth.account=" + providers.gradleProperty("dev.account").getOrElse("main"))
		// Endpoint overrides for local development, e.g.
		//   ./gradlew runActiveClient -Pcraftshot.url=http://localhost:8000
		listOf(
			"craftproxy.backend.url",
			"craftproxy.website.url",
			"craftshot.url",
			"craftproxy.mojang.session.url",
			"craftproxy.mojang.profile.url",
			"craftproxy.tunnel.host",
			"craftproxy.discord.url",
			"craftproxy.discord.rpc.url",
		).forEach { key ->
			providers.gradleProperty(key).orNull?.let { jvmArguments.add("-D$key=$it") }
		}
		// Trust the local Herd CA for https://*.test backends during development.
		// Created once in run/ (git-ignored); release builds are unaffected.
		val herdTruststore = rootProject.layout.projectDirectory.file("run/herd-truststore.p12").asFile
		if (herdTruststore.exists()) {
			jvmArguments.add("-Djavax.net.ssl.trustStore=${herdTruststore.absolutePath}")
			jvmArguments.add("-Djavax.net.ssl.trustStorePassword=changeit")
		}
		displayName.set("Fabric Client")
	}
	runs.named("server") {
		server()
		generateRunConfig.set(true)
		runDirectory.set(layout.projectDirectory.dir("run"))
		jvmArguments.addAll(devJvmArgs)
		displayName.set("Fabric Server")
	}
}

repositories {
	mavenCentral()
	strictMaven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1", "me.djtheredstoner") { name = "DevAuth" }
}

configurations.all {
	resolutionStrategy {
		force("net.fabricmc:fabric-loader:${prop("deps.fabric-loader")}")
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${prop("deps.minecraft")}")
	if (sc.current.parsed < "26") {
		mappings(loom.layered {
			officialMojangMappings()
			if (sc.properties.contains("deps.parchment"))
				parchment("org.parchmentmc.data:parchment-${prop("deps.parchment")}@zip")
		})
	}
	modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric-loader")}")
	modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}")
	// Dev-only: Microsoft login for the dev client, never part of the built jar.
	modLocalRuntime("me.djtheredstoner:DevAuth-fabric:${prop("deps.devauth")}")
}
