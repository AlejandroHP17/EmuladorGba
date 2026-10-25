# Emulador Game Boy Advance

Emulador embebible para [MiniJuegos](https://github.com/AlejandroHP17/MiniJuegos). Vive en su propio repo: library publicable (AAR) y app shell para probarlo aparte.

La app no incluye ROMs ni BIOS. El jugador aporta su archivo `.gba`.

## Artefacto Maven

| Coordenada | Valor |
|------------|--------|
| Group | `pelkidev.com.mx.minijuegos` |
| Artifact | `emuladorgba` |
| Versión | `1.0.0-SNAPSHOT` |

Depende de: `pelkidev.com.mx.minijuegos:sdk:1.0.0`

| Campo | Valor |
|-------|--------|
| `id` | `emuladorgba` |
| Título | Emulador Game Boy Advance |
| Descripción | Juega un archivo .gba en el dispositivo |

## Módulos

| Módulo | Tipo | Rol |
|--------|------|-----|
| `:emuladorgba` | `android-library` | `EmuladorGbaGameModule` y la pantalla inicial → AAR |
| `:app` | `android-application` | Shell de prueba (`applicationId` = `pelkidev.com.mx.emuladorgba`) |

## Núcleo mGBA

La emulación la hace [mGBA](https://mgba.io) `0.10.5`, compilado con el NDK dentro del AAR (`libemuladorgba_mgba.so`). Solo el núcleo GBA, sin frontends ni dependencias opcionales.

- El código de mGBA no está en este repo. `emuladorgba/src/main/cpp/CMakeLists.txt` lo descarga al compilar desde la etiqueta `0.10.5` y comprueba su SHA-256. La primera compilación necesita red; después queda en caché en `emuladorgba/.cxx/`.
- ABIs publicadas: `arm64-v8a` y `x86_64`.
- El código nativo se compila en Release también en debug, para probar a velocidad real.
- `consumer-rules.pro` conserva los métodos `native` de `data.MgbaCore`, así que el host puede minificar con R8.

### Licencia

mGBA se distribuye bajo la [Mozilla Public License 2.0](https://www.mozilla.org/MPL/2.0/) (© Vicki Pfau y colaboradores). Al publicar una app con este AAR hay que conservar ese aviso y ofrecer el código fuente de mGBA (los archivos MPL), por ejemplo enlazando a [github.com/mgba-emu/mgba](https://github.com/mgba-emu/mgba/tree/0.10.5). El resto del emulador no queda bajo MPL.

## Desarrollo local

Requisitos: Android SDK con NDK `30.0.16248370` y CMake `4.1.2` (SDK Manager → SDK Tools). Si faltan, AGP intenta descargarlos con las licencias del SDK aceptadas.

```bash
./gradlew :app:assembleDebug
```

`MainActivity` llama a `EmuladorGbaGameModule.Entry(onExit = ::finish)`.

## Publicar AAR

Maven Local:

```bash
./gradlew :emuladorgba:publishToMavenLocal
```

GitHub Packages (repo `AlejandroHP17/EmuladorGba`): push a `main` publica `1.0.0-SNAPSHOT`. Hace falta el secret `GH_PACKAGES_TOKEN` (PAT con `read:packages` y `write:packages`). El publish remoto falla hasta que ese repo y el secret existan.

## Integración en el host (pendiente)

Todavía no está cableado en `MiniJuegos/`. Cuando el módulo ya se pueda jugar:

1. Añadir el Maven repo del juego en `settings.gradle.kts` (o usar `mavenLocal()` en desarrollo).
2. `implementation("pelkidev.com.mx.minijuegos:emuladorgba:1.0.0-SNAPSHOT")`.
3. Registrar `EmuladorGbaGameModule` en `GameCatalog.games`.
4. Icono `ic_game_emuladorgba` y rama en `GameVisuals.forId("emuladorgba")`.

## Credenciales locales

En `~/.gradle/gradle.properties` o en el entorno:

```properties
gpr.user=AlejandroHP17
gpr.token=ghp_xxx
```
