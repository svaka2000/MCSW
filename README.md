# PvPTL — Minecraft PvP Plugin Suite

Multi-module Gradle workspace building three plugins for a Paper 1.21+ Minecraft server:

| Module | JAR | What it does |
|---|---|---|
| `tourney/` | `Tourney.jar` | Auto 1v1 tournament bracket plugin |
| `duels/` | `Duels.jar` | 1v1 duels with queue, challenges, GUI kit picker, NPC binding |
| `aihelp/` | `AIHelp.jar` | Groq-powered AI help NPC with per-player memory |

## Build

Requires Java 21. From the workspace root:

```bash
./gradlew build
```

Output JARs land in `tourney/build/libs/`, `duels/build/libs/`, `aihelp/build/libs/`.

### Auto-deploy

Every push to `main` triggers GitHub Actions:

1. **Build workflow** — builds all three JARs and uploads them as a downloadable artifact. Runs on every push, no setup required.
2. **Deploy workflow** — if you've configured the secrets listed in `DEPLOY.md`, the workflow SCPs the JARs to your Minecraft server's `plugins/` folder and (optionally) hot-reloads each plugin via PlugManX. Plugin configs are never touched — only the JARs are replaced.

See `DEPLOY.md` for the full setup (SSH key, GitHub secrets, PlugManX install).

## Server compatibility

- Paper 1.21.x (Spigot/Bukkit will not work)
- Java 21 server runtime
- Soft-depend (AIHelp only): Citizens

## Notable features

### Tourney

- `/tourney start [join=N] [rounds=N] [freeze=N] [cap=N]` — per-tournament overrides
- 1v1 first-to-N kills, single-elimination, parallel arenas
- Bracket viewer GUI, spectator sidebar with live score
- Crash-safe inventory persistence
- Achievement broadcast suppression during tournaments
- Hunger/saturation lock for participants

### Duels

- `/duel <player>` → opens kit picker GUI; right-click a kit to customize rounds + time limit
- `/duels queue [kit]` — auto-matchmaking
- `/duels gui` — open the queue GUI
- `/duels tagentity` — tag any entity as a queue NPC (right-click opens GUI)
- Inventory save/restore, BO1/3/5/7 support, optional match time cap
- Cooldown between sending challenges
- Clickable accept/deny buttons in chat

### AIHelp

- `/ask <question>` — Groq-powered chat NPC
- Per-player conversation memory (persisted to disk)
- Optional chat watcher detects confused players and offers help
- `/aihelp setkey`, `/aihelp status`, `/aihelp reset [player]`

## Layout

```
PvPTL/
├── settings.gradle.kts
├── build.gradle.kts          ← root workspace config
├── tourney/                  ← tournament plugin
├── duels/                    ← duels plugin
├── aihelp/                   ← AI help plugin
└── gradle/wrapper/           ← gradle wrapper
```

Each module has its own `build.gradle.kts`, `src/main/java/`, `src/main/resources/{plugin.yml,config.yml}`.
