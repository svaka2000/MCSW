# Tourney Setup Guide

This guide walks you through getting the plugin running end-to-end, including how to configure **multiple parallel arenas** so several matches play out at once.

---

## 1. Install

1. Build the plugin: `./gradlew build` — the JAR appears in `build/libs/MinecraftTournamentPlugin-<version>.jar`.
2. Drop the JAR into your server's `plugins/` folder.
3. Start (or restart) the server.
4. The plugin generates `plugins/Tourney/config.yml` on first run. You don't need to hand-edit it — the in-game wizard does everything.

---

## 2. Concept: how parallel matches work

A tournament runs as a **single-elimination bracket**. Every round, the manager looks at all configured arenas and dispatches as many matches in parallel as there are free arenas. So:

- **1 arena configured** → one match at a time, the rest of the players wait between matches. This works but is slow on large tournaments.
- **4 arenas configured** → up to 4 first-round matches play simultaneously. With 16 sign-ups, the entire first round is over at the speed of one match.
- **Rule of thumb**: configure roughly `players / 4` arenas if you want each round to feel snappy. More arenas never hurts, just take the space.

Each arena has **two spawn points**: side `a` (left fighter) and side `b` (right fighter). They should be 5–15 blocks apart, facing each other.

---

## 3. Building an arena (in-world)

For each arena you want, build a small enclosed PvP space. Recommended layout:

- **Size**: ~20×20 blocks of flat floor, enclosed walls 6+ blocks tall (so players can't escape).
- **Material**: cobblestone, stone bricks, or anything blast-/projectile-resistant.
- **Two spawn points**: opposite corners or sides, exactly the same distance from the center, facing each other.
- **No water, no lava, no sand**, no obvious gimmicks. The kit is the same for both fighters, so the only variable should be skill.
- **Spacing between arenas**: at least 100 blocks apart so projectiles, sounds, or players from one arena can't bleed into another.

If you want a "void arena" feel, build the floor at y=64 in a sky world; the kit's Prot III Unbreakable armor still gets full void damage if someone falls off, which counts as a kill for their opponent (it's awarded as a kill on death whether or not the opponent landed the blow, since they're the only other person in the arena).

---

## 4. Run the in-game wizard

You **must** be an OP (or have `tourney.setup` permission) to run setup commands.

### Step 1 — Save the lobby

Stand where you want players to be teleported back to **before**, **between**, and **after** matches.

```
/tourney setup lobby
```

This saves your current position (and looking direction) as the lobby spawn.

### Step 2 — Save arena 1, side a

Walk into your first arena and stand on the spot where fighter A should appear. Run:

```
/tourney setup arena arena1 a
```

The name `arena1` is just a label — pick anything alphanumeric (dashes and underscores are fine).

### Step 3 — Save arena 1, side b

Walk to the opposite side and stand on the spot where fighter B should appear. Run:

```
/tourney setup arena arena1 b
```

### Step 4 — Add more arenas (this is the parallel-matches part)

For every additional arena you build, repeat steps 2–3 with a new name:

```
/tourney setup arena arena2 a
/tourney setup arena arena2 b
/tourney setup arena arena3 a
/tourney setup arena arena3 b
```

The plugin will run as many simultaneous matches as you have **completed** arenas (both sides defined).

### Step 5 — Verify

```
/tourney setup
```

This shows your current setup. You should see:

```
=== Tourney setup status ===
Lobby: ✓ world @ 100.5, 64.0, -23.5
Arenas: 3
  - arena1 ✓ ready
  - arena2 ✓ ready
  - arena3 ✓ ready
Ready arenas (parallel matches possible): 3
─────────────
✓ Setup complete. Run /tourney start to begin.
```

### Useful setup commands

```
/tourney setup arena list                # list all arenas + completion status
/tourney setup arena remove <name>       # delete an arena
/tourney reload                          # reload config.yml after manual edits
```

---

## 5. Run a tournament

```
/tourney start
```

This:

1. Broadcasts a clickable join message to chat.
2. Opens a 5-minute join window with a live countdown bossbar.
3. After the window closes, builds a single-elimination bracket and dispatches first-round matches across all your arenas in parallel.
4. Each match is **first to 5 kills** — players respawn in the arena, kit auto-refills.
5. Match has a 5-minute hard cap; if neither player hits 5 kills, higher kill count wins.
6. Loser eliminated, winner advances. Final winner is announced in chat.

### Player commands

```
/tourney join                # join during the join window
/tourney leave               # leave before the bracket is built
/tourney bracket             # GUI showing the live bracket
/tourney spectate            # GUI of all live matches
/tourney spectate <player>   # follow that player directly
/tourney stopspectate        # exit spectator mode, return to lobby
```

---

## 6. The kit (fixed)

Every fighter gets the same kit, every match, every respawn:

- Diamond Helmet, Chestplate, Leggings, Boots — all **Protection III**, all **Unbreakable**.
- Diamond Sword — **no enchants**, **Unbreakable**.

Items can't be dropped during a match, so no kit theft.

---

## 7. Tuning (config.yml)

If you want to change defaults without re-running the wizard, edit `plugins/Tourney/config.yml`:

```yaml
tournament:
  join-window-seconds: 300        # how long the join window stays open
  min-players: 2                  # tournament cancels if fewer joined
  max-players: 64
  match-time-cap-seconds: 300     # match auto-ends after this
  kills-to-win: 5                 # change the round length
  freeze-seconds: 5               # pre-fight freeze + countdown
  disconnect-grace-seconds: 60    # how long to wait for a reconnect before forfeit
```

Run `/tourney reload` after editing to apply changes (no server restart needed).

---

## 8. Troubleshooting

| Symptom | Fix |
|---|---|
| `/tourney start` says "Setup incomplete" | You haven't set the lobby or any arena. Run `/tourney setup` to see what's missing. |
| Players don't get teleported | Make sure each arena has BOTH `a` and `b` defined. Use `/tourney setup arena list` to check. |
| All matches run sequentially | You only have 1 arena configured. Add more with different names. |
| Bracket viewer shows "TBD" | That match's previous round hasn't finished yet — totally normal mid-tournament. |
| Player crashed / disconnected mid-match | They have 60 seconds (configurable) to rejoin before auto-forfeit. Inventory is saved to disk and restored on rejoin. |
| Server crashed mid-tournament | When players rejoin, the plugin automatically restores their pre-tournament inventory from `plugins/Tourney/saved/`. |

---

## 9. Permissions

| Permission | Default | What it does |
|---|---|---|
| `tourney.use` | everyone | Join, leave, view bracket, spectate |
| `tourney.start` | OP | Start a tournament |
| `tourney.cancel` | OP | Cancel a running tournament |
| `tourney.setup` | OP | Configure lobby and arenas, reload config |
