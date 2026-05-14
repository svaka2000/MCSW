# PvPTL — Minecraft PvP Plugin Suite

Multi-module Gradle workspace building six plugins for a Paper 1.21+ Minecraft server.

| Module | JAR | What it does |
|---|---|---|
| `tourney/` | `Tourney.jar` | Auto 1v1 tournament bracket plugin |
| `duels/` | `Duels.jar` | 1v1 + N-vs-N duels, ranked/unranked queues, parallel arenas, party fights |
| `aihelp/` | `AIHelp.jar` | Groq-powered AI help NPC with per-player memory |
| `stats/` | `PvPTLStats.jar` | Shared SQLite-backed stats — duels, tournaments, per-kit Elo |
| `kits/` | `PvPTLKits.jar` | Shared kit storage used by Tourney and Duels |
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
2. **Deploy workflow** — if the secrets in `DEPLOY.md` are set, the workflow uploads the JARs via SFTP (curl) to your Minecraft server's `plugins/` folder. Plugin configs are never touched — only the JARs are replaced.

See `DEPLOY.md` for the full setup.

## Server compatibility

- Paper 1.21.x (Spigot/Bukkit will not work)
- Java 21 server runtime
- Soft-depend (AIHelp only): Citizens

## Feature highlights

### Duels — ranked, unranked, parties, parallel arenas

The biggest plugin. Everything below is in `Duels.jar`.

**Lobby hotbar:**
- Slot 0 — **diamond sword**: right-click → opens the **ranked** kit picker. Ranked games are first-to-3 with per-kit Elo tracking.
- Slot 1 — **iron sword**: right-click → opens the **unranked** kit picker (casual play, no Elo).
- Slot 8 — **leave-queue barrier** (while queued) or **requeue paper** (after a match). Right-click the paper to jump back into the same queue you just played.

**Parallel arenas:**
- Configure N arenas, each with its own `spawns_a` / `spawns_b` lists.
- Matches occupy one arena exclusively, so N configured arenas = up to N concurrent duels.
- The waiting queue avoids head-of-line blocking — a 1v1 in the queue can grab a free 1v1 arena even while a 3v3 ahead of it waits for a bigger arena.

**Ranked play:**
- First-to-3 rounds per match.
- Per-kit Elo (Glicko-free standard formula, default K=32, default start=1000).
- After each ranked match, both players see `Ranked win/loss. Elo: 1042 (+13)` in chat.
- `/elo top <kit>` — top 10 by Elo for that kit, gold/silver/bronze medals on the top three.

**Party fights:**
- `/party create` / `/party invite ...` / `/party accept|deny|leave|disband|kick|promote`
- `/partyduel <enemy-leader>` — leader-only party-vs-party challenge, runs as a team-vs-team duel.

**Commands cheat sheet:**

| Command | Notes |
|---|---|
| `/duel <player>` | Open kit picker for a 1v1 challenge (right-click a kit to pick first-to 1/5/6/7/10/15) |
| `/partyduel <leader>` | Party leader → challenge another party leader |
| `/duels queue [kit]` | Manual unranked queue join |
| `/duels gui` | Open the kit/queue picker |
| `/duels leave` | Leave the current queue |
| `/duels accept` / `deny` | Accept or deny the most recent pending challenge |
| `/leave` | Forfeit your current duel or tournament match, or leave a queue |
| `/duels info` | Status: arenas, kits, active matches, waiting queue |
| `/duels setlobby` | Admin — save current location as lobby |
| `/duels setarena <a\|b>` | Admin — append spawn at your loc (default arena) |
| `/duels setarena <arena> <a\|b> [slot]` | Admin — named arena, optional slot overwrite |
| `/duels setarena list` | Admin — list all arenas + spawns |
| `/duels setarena clear <arena> [a\|b]` | Admin — wipe one side or the whole arena |
| `/duels setarena delete <arena>` | Admin — remove arena entirely |
| `/elo top <kit>` | Top 10 by Elo on a kit |

**Combat rules:**
- Friendly fire disabled in team matches (melee AND projectiles).
- Teammates don't body-block (Scoreboard collision rule = FOR_OTHER_TEAMS).
- Nameplates are color-coded — team A aqua, team B red.
- Round ends when one entire team is dead. First to N round wins takes the match.

### Tourney

- `/tourney start [join=N] [rounds=N] [freeze=N] [cap=N]` — per-tournament overrides
- 1v1 first-to-N kills, single-elimination, parallel arenas
- Bracket viewer GUI, spectator sidebar with live score
- Achievement broadcast suppression during tournaments
- Hunger/saturation lock for participants

### Parties

- `/party create` — start a new party
- `/party invite <player>` / `/party accept` / `/party deny`
- `/party leave` / `/party disband` / `/party kick <player>` / `/party promote <player>`
- `/party info` / `/party chat <message>` / `/p <message>` shortcut
- Public `PartyService` via Bukkit's ServicesManager — Duels soft-depends on it for `/partyduel`

### AIHelp

- `/ask <question>` — Groq-powered chat NPC
- Per-player conversation memory (persisted to disk)
- Optional chat watcher detects confused players and offers help
- `/aihelp setkey`, `/aihelp status`, `/aihelp reset [player]`

### Stats

- SQLite store at `plugins/PvPTLStats/stats.db`
- `/stats [player]`, `/top <category>`, `/profile [player]`
- Schema v2 adds the `duel_elo` table + `ranked` column on `duel_results`
- Public `StatsService` — used by Duels for both casual results and ranked Elo updates

## Setting up arenas

For a single 1v1 arena (legacy install):
```
/duels setarena a   # stand on side-A pad → run
/duels setarena b   # stand on side-B pad → run
```

For multiple parallel 1v1 arenas:
```
/duels setarena arena_main a
/duels setarena arena_main b
/duels setarena arena_two a
/duels setarena arena_two b
```

For a 2v2 arena (two slots per side):
```
# Stand on side-A pad 1
/duels setarena arena_2v2 a
# Stand on side-A pad 2
/duels setarena arena_2v2 a
# Side B
/duels setarena arena_2v2 b
/duels setarena arena_2v2 b
```

Check what you've got:
```
/duels setarena list
/duels info
```

## Config quick-reference (`duels/config.yml`)

```yaml
duels:
  default-first-to: 1       # casual default (1 / 5 / 6 / 7 / 10 / 15 selectable in GUI)
  ranked-first-to: 3        # ranked is always first-to-N (default 3)
  elo-starting: 1000        # new players start here
  elo-k-factor: 32          # higher = bigger Elo swings per match
  lobby-items: true         # diamond + iron sword in hotbar slots 0/1
  freeze-seconds: 3
  match-time-cap-seconds: 180

locations:
  lobby: <Location>          # /duels setlobby writes this
  arenas:
    arena_0:
      spawns_a: [<Location>, ...]
      spawns_b: [<Location>, ...]
```

## Layout

```
PvPTL/
├── settings.gradle.kts
├── build.gradle.kts          ← root workspace config
├── tourney/                  ← tournament plugin
├── duels/                    ← duels plugin (1v1 + team + ranked)
├── aihelp/                   ← AI help plugin
├── stats/                    ← shared duel/elo stats service
├── kits/                     ← shared kit storage
├── parties/                  ← party system
└── gradle/wrapper/           ← gradle wrapper
```

Each module has its own `build.gradle.kts`, `src/main/java/`, `src/main/resources/{plugin.yml,config.yml}`.
