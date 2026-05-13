# PvPTL — Minecraft PvP Plugin Suite

Multi-module Gradle workspace building six plugins for a Paper 1.21+ Minecraft server:

| Module | JAR | What it does |
|---|---|---|
| `tourney/` | `Tourney.jar` | Auto 1v1 tournament bracket plugin |
| `duels/` | `Duels.jar` | 1v1 **and** N-vs-N duels with queue, challenges, GUI kit picker, NPC binding |
| `aihelp/` | `AIHelp.jar` | Groq-powered AI help NPC with per-player memory |
| `stats/` | `PvPTLStats.jar` | Shared SQLite-backed duel statistics service |
| `kits/` | `PvPTLKits.jar` | Shared kit storage and editor used by Tourney and Duels |
| `parties/` | `PvPTLParties.jar` | Party system — group up, then run team-vs-team duels |

## Build

Requires Java 21. From the workspace root:

```bash
./gradlew build
```

Output JARs land in `<module>/build/libs/`.

### Auto-deploy

Every push to `main` triggers GitHub Actions:

1. **Build workflow** — builds all six JARs and uploads them as a downloadable artifact. Runs on every push, no setup required.
2. **Deploy workflow** — if you've configured the secrets listed in `DEPLOY.md`, the workflow uploads the JARs via SFTP (curl) to your Minecraft server's `plugins/` folder. Plugin configs are never touched — only the JARs are replaced.

See `DEPLOY.md` for the full setup.

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
- `/partyduel <player>` → party-leader-only — challenges the target's party leader to a team-vs-team match
- `/duels queue [kit]` — auto-matchmaking (1v1)
- `/duels gui` — open the queue GUI
- `/duels tagentity` — tag any entity as a queue NPC (right-click opens GUI)
- Multi-spawn arenas: `/duels setarena a` appends a spawn for side A; do it once per team slot
- Inventory save/restore, first-to-1/3/5/7 support, optional match time cap
- Cooldown between sending challenges
- Clickable accept/deny buttons in chat
- Round semantics: a round ends when one team is fully eliminated; first-to-N round wins takes the match
- Friendly fire prevented across the whole match (melee + projectiles)
- Per-match sidebar shows server IP and live round score; team matches show "Team A (N) : Team B (N)"
- Post-duel "Requeue: kit" paper in slot 8 — right-click to jump back into queue

### Parties

- `/party create` — start a new party
- `/party invite <player>` / `/party accept` / `/party deny`
- `/party leave` / `/party disband` / `/party kick <player>` / `/party promote <player>`
- `/party info` / `/party chat <message>` / `/p <message>` shortcut
- Members persist across sessions (in-memory; leadership auto-transfers if leader logs out and the config allows it)
- Public `PartyService` via Bukkit's ServicesManager — Duels soft-depends on it for `/partyduel`

### AIHelp

- `/ask <question>` — Groq-powered chat NPC
- Per-player conversation memory (persisted to disk)
- Optional chat watcher detects confused players and offers help
- `/aihelp setkey`, `/aihelp status`, `/aihelp reset [player]`

## Setting up a party-duel arena

1. Build N matching spawn pads for each side of the arena.
2. As an op, stand on side-A pad 1 → `/duels setarena a`. Repeat for pad 2, pad 3, etc.
3. Do the same for side B → `/duels setarena b`.
4. Verify: `/duels info` shows "Side A spawns: N" and "Max team size: N".
5. Players form parties via `/party invite` and the leader runs `/partyduel <enemy-leader>`.

The arena's "max team size" is the smaller of the two spawn counts. Parties larger than that can't start a duel until more spawns are added.

## Layout

```
PvPTL/
├── settings.gradle.kts
├── build.gradle.kts          ← root workspace config
├── tourney/                  ← tournament plugin
├── duels/                    ← duels plugin (1v1 + team)
├── aihelp/                   ← AI help plugin
├── stats/                    ← shared duel stats service
├── kits/                     ← shared kit storage
├── parties/                  ← party system
└── gradle/wrapper/           ← gradle wrapper
```

Each module has its own `build.gradle.kts`, `src/main/java/`, `src/main/resources/{plugin.yml,config.yml}`.
