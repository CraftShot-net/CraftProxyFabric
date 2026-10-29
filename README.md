# CraftProxy

Host your Minecraft world to the public. Only the host needs the mod.

## Supported versions

One codebase, built for every version with [Stonecutter](https://stonecutter.kikugie.dev/)
(based on [stonecutter-mod-template](https://github.com/rotgruengelb/stonecutter-mod-template)).
The versions are the most played Fabric modpack versions on Modrinth:

| Minecraft      | Java | Notes                                   |
|----------------|------|-----------------------------------------|
| 26.3           | 25   | version kept in git (`vcsVersion`)      |
| 26.2           | 25   |                                         |
| 26.1 – 26.1.2  | 25   |                                         |
| 1.21.11        | 21   |                                         |
| 1.21 – 1.21.1  | 21   |                                         |
| 1.20.1         | 17   |                                         |

## Development

Requires Java 25 to run Gradle; the Java 17/21 toolchains are downloaded automatically.

```bash
./gradlew buildAndCollect          # build all versions -> build/libs/<version>/
./gradlew :1.21.1-fabric:build     # build a single version
./gradlew runActiveClient          # run the client of the active version
./gradlew "Set active project to 1.20.1-fabric"   # switch the version shown in src/
```

Always switch back to `26.3-fabric` (`./gradlew "Reset active project"`) before committing,
so `src/` stays in the committed state.

### Logging in with a real account

Dev clients include [DevAuth](https://github.com/DJtheRedstoner/DevAuth) (dev runtime only, never in the built jar).
On the first start it prints a Microsoft login link in the console; log in in your browser.
The account `main` from `~/.devauth/config.toml` is used (`-Ddevauth.account=main`).
Tokens are cached in `~/.devauth/`. Delete that folder to log out. To start offline,
remove the `devauth` JVM arguments from the client run in `build.fabric.gradle.kts`
(Loom then uses a random `Player###` name).

### Version-specific code

Code that differs between versions uses Stonecutter comments:

```java
//? if >=1.21.9 {
mc.player.sendSystemMessage(message);
//?} else {
/*mc.player.displayClientMessage(message, false);
*///?}
```

Most API differences are hidden behind `net.craftproxy.mod.client.compat`:

* `ClientCompat` – screens, toasts, LAN publishing, chat, identifiers
* `Gfx` – GUI drawing (`GuiGraphics` before 26.1, `GuiGraphicsExtractor` since)
* `SkinData` – version independent player skin

`ResourceLocation` (before 1.21.11) and `Identifier` are swapped automatically, see `build.fabric.gradle.kts`.

### Adding a Minecraft version

1. Add `match("<version>", "fabric")` in `settings.gradle.kts`.
2. Add a `[fabric."<version>"]` table with `deps.minecraft` and `deps.fabric-api` in `stonecutter.properties.toml`.
3. Build it and fix compile errors with Stonecutter conditions or `compat` helpers.

### Releasing

Bump `mod.version` in `stonecutter.properties.toml`, update `CHANGELOG.md` and push a matching tag.
`.github/workflows/release.yml` builds all versions and publishes them (configure the `PUB_*`
variables/secrets, see `.env.template`).

## License

CC0-1.0
