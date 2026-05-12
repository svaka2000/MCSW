# Tourney — Auto Tournament Plugin

A Minecraft plugin that runs **automatic 1v1 tournaments** on your server. Players join from chat, the plugin builds the bracket for you, teleports fighters into arenas, and runs every match without any manual work. Multiple matches can run **at the same time** across different arenas if you set them up.

---

## What this plugin actually does

You run **one command** — `/tourney start` — and the plugin handles everything:

1. Sends a clickable join message in chat. Players have **5 minutes** to click and join.
2. Builds a single-elimination bracket from whoever joined.
3. Teleports the first round of fighters into the arena(s) you set up.
4. Each match is **first to 5 kills**. After every kill, **both players reset to their spawn points** with a 5-second freeze countdown, then fight resumes. First to 5 round-wins takes the match.
5. Loser goes to spectator mode, winner advances. Plugin pulls the next pair into the arena automatically.
6. Final winner is announced in chat. Tournament ends.

Everyone gets the **exact same kit** every match: full Protection III diamond armor + diamond sword, all unbreakable. No advantage for anyone.

---

## What you need

- A **Minecraft server** running **Paper 1.21 or newer** (Spigot/Bukkit will not work — must be Paper or a Paper fork like Purpur).
- Operator (op) permissions on the server.

You probably already have these if someone is sending you this file.

---

## Step 1 — Install the plugin

1. **Stop your server** if it's running.
2. Find your server's `plugins` folder (it's next to your `server.jar`).
3. **Drop the JAR file** (`Tourney.jar`) into that `plugins` folder.
4. **Start the server.** You should see this in the console:

   ```
   [INFO] Tourney enabled. Run /tourney setup to configure.
   ```

That's it. No extra dependencies, no extra config files to mess with.

---

## Step 2 — First-time setup (one-time, ~5 minutes)

The plugin doesn't know where your arenas are yet. You have to walk around in-game and tell it.

### Build at least one arena

A small enclosed PvP space — about **20×20 blocks**, walls about 6 blocks tall, flat floor. Pick two spots inside as the **two spawn points** (where the fighters appear). They should be ~10 blocks apart, facing each other.

Recommendations:
- Cobblestone or stone walls (so people can't break out).
- No water, no lava, no obstacles inside.
- If you want **multiple arenas** (so several matches can play at once), build them at least **100 blocks apart** so projectiles and sounds from one don't bleed into another.

### Save the lobby

Stand wherever you want players to be teleported **before, between, and after** matches. Run:

```
/tourney setup lobby
```

You'll see a confirmation message.

### Save your first arena

Walk into your arena. Stand on **fighter A's spawn spot**. Run:

```
/tourney setup arena arena1 a
```

(`arena1` is just a name — pick anything.)

Now walk to the **other spawn spot** in the same arena. Run:

```
/tourney setup arena arena1 b
```

Done. That's one arena ready.

### (Optional) Save more arenas for parallel matches

This is the part that makes big tournaments fast. For every additional arena you've built, repeat with a new name:

```
/tourney setup arena arena2 a
/tourney setup arena arena2 b
/tourney setup arena arena3 a
/tourney setup arena arena3 b
```

Whatever number of arenas you set up = the number of matches that can play **simultaneously**. With 4 arenas and 16 players signed up, the entire first round happens at once.

### Check your work

```
/tourney setup
```

You should see something like:

```
=== Tourney setup status ===
Lobby: ✓ world @ 100.5, 64.0, -23.5
Arenas: 3
  - arena1 ✓ ready
  - arena2 ✓ ready
  - arena3 ✓ ready
Ready arenas (parallel matches possible): 3
✓ Setup complete. Run /tourney start to begin.
```

If it says ✓ Setup complete, you're done forever. The setup is saved — you don't need to redo it after restarts.

---

## Step 3 — Run a tournament

```
/tourney start
```

You can also pass per-tournament overrides as key=value args (any combination, all optional):

```
/tourney start rounds=3                    # first-to-3 kills instead of 5
/tourney start join=60 rounds=3            # 1-min join window, first-to-3
/tourney start join=120 rounds=5 freeze=3  # custom freeze countdown too
```

| Key | What it controls | Default | Range |
|---|---|---|---|
| `join` | Join window seconds | 300 | 10–3600 |
| `rounds` | Kills needed to win a match | 5 | 1–100 |
| `freeze` | Pre-fight freeze countdown | 5 | 0–30 |
| `cap` | Match time cap in seconds | 300 | 30–3600 |

Here's what happens after you start:

1. Everyone on the server sees a chat message: **"A tournament is starting! [CLICK TO JOIN]"**
2. A bossbar appears at the top of everyone's screen with a 5-minute countdown.
3. Players type `/tourney join` (or click the message) to enter.
4. After 5 minutes, the bracket builds and **first round matches start automatically** in your arenas.
5. Each match plays out — you don't have to do anything. The plugin tracks kills, respawns players, refills kits, declares winners, and pulls the next pair in.
6. When the final match ends, the winner is announced in chat.

You don't need to babysit it. Just watch.

---

## What players experience

| Step | What they see |
|---|---|
| Tournament starts | Clickable join message in chat + bossbar countdown |
| They click join | "You joined the tournament. (3 players)" |
| Window closes, their turn comes up | Teleported into arena, frozen for 5 seconds with a "PREPARE" title, then countdown to FIGHT |
| Match begins | Action bar shows live kill score: "You: 2  |  Opp: 1" |
| A kill happens (round ends) | Both players teleport back to their spawns, full kit refilled, 5-second freeze countdown, then fight resumes |
| They reach 5 round-wins | "VICTORY" title, teleported to lobby, advance to next round |
| They lose | "DEFEAT" title, teleported to lobby, can spectate |
| They win the whole tournament | Their name announced to the whole server |

Their original inventory is **saved** when the tournament starts and **fully restored** when they're eliminated or the tournament ends. No one loses their stuff.

---

## Commands cheatsheet

### Things you (the host) will use

| Command | What it does |
|---|---|
| `/tourney start [join=N] [rounds=N] [freeze=N] [cap=N]` | Begin a tournament; all params optional |
| `/tourney cancel` | Cancel a running tournament (everyone gets their stuff back) |
| `/tourney setup` | Show setup status |
| `/tourney setup lobby` | Save lobby spawn at your current location |
| `/tourney setup arena <name> <a\|b>` | Save an arena spawn point |
| `/tourney setup arena list` | List all configured arenas |
| `/tourney setup arena remove <name>` | Delete an arena |
| `/tourney reload` | Reload the config file |

### Things players will use

| Command | What it does |
|---|---|
| `/tourney join` | Join the active tournament |
| `/tourney leave` | Leave before the bracket is built |
| `/tourney bracket` | Open a GUI showing the live bracket — click any player to spectate them |
| `/tourney spectate` | Open a GUI listing all matches happening right now. Pick one and you'll see a live score sidebar on the right of your screen. |
| `/tourney spectate <player>` | Spectate a specific player directly (with live score sidebar) |
| `/tourney stopspectate` | Exit spectator mode |

---

## The kit (every player gets this, every match)

- **Helmet:** Diamond, Protection III, Unbreakable
- **Chestplate:** Diamond, Protection III, Unbreakable
- **Leggings:** Diamond, Protection III, Unbreakable
- **Boots:** Diamond, Protection III, Unbreakable
- **Sword:** Diamond, no enchants, Unbreakable

Players cannot drop these items during a match (so no kit theft). Kit auto-refills on every respawn.

---

## Tweaking settings (optional)

You can edit `plugins/Tourney/config.yml` if you want to change defaults. Run `/tourney reload` after editing — no server restart needed.

```yaml
tournament:
  join-window-seconds: 300        # 5 minutes (how long players can join)
  min-players: 2                  # cancels if fewer joined
  max-players: 64
  match-time-cap-seconds: 300     # match auto-ends after 5 minutes (higher kills wins)
  kills-to-win: 5                 # change to 3 for shorter matches, 10 for longer
  freeze-seconds: 5               # pre-fight freeze countdown
  disconnect-grace-seconds: 60    # how long someone has to reconnect before forfeit
```

---

## Troubleshooting

| What you see | What's wrong / how to fix |
|---|---|
| `/tourney start` says "Setup incomplete" | You haven't set the lobby or any arena. Run `/tourney setup` to see what's missing. |
| Players don't get teleported | One of your arenas is missing side `a` or side `b`. Run `/tourney setup arena list` and re-do whichever side has ✗. |
| Only one match plays at a time | You only have one arena. Build more and add them with different names. |
| Bracket viewer shows "TBD" boxes | Those matches haven't been played yet — totally normal mid-tournament. |
| A player crashed | They have 60 seconds to reconnect (configurable). If they don't, they auto-forfeit and the opponent wins. Their inventory is saved on disk and restored when they log back in. |
| The server crashed mid-tournament | When players log back in, the plugin automatically restores their pre-tournament inventory. They lose their progress in the bracket but not their stuff. |
| Plugin doesn't load on server start | Check that you're running **Paper** (not Spigot or Bukkit) on Minecraft **1.21 or newer**. Type `version` in console to check. |

---

## Permissions (advanced)

If you want non-OP players to host tournaments, you'll need a permissions plugin (like LuckPerms). The relevant permissions are:

| Permission | Default | Used for |
|---|---|---|
| `tourney.use` | everyone | Joining, leaving, viewing brackets, spectating |
| `tourney.start` | OP only | Running `/tourney start` |
| `tourney.cancel` | OP only | Running `/tourney cancel` |
| `tourney.setup` | OP only | Configuring arenas, reloading config |

---

## That's it

If something is acting weird, the simplest fix is `/tourney cancel` (gets everyone their stuff back) and try again. The plugin saves inventories to disk before any teleports, so you can't actually lose items even if something goes wrong.

Have fun.
