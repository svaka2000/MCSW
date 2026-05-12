# Auto-deploy setup

Every push to `main` triggers two workflows:

1. **Build** — builds all four JARs (Tourney, Duels, AIHelp, PvPTLStats) and uploads them as a downloadable artifact at https://github.com/svaka2000/MCSW/actions. Runs on every push, no setup required.
2. **Deploy** — if the secrets below are set, SFTPs the JARs to your server's plugins folder. Skips with a warning if secrets are missing.

Plugin configs (`plugins/<PluginName>/config.yml`) are **never touched** — only JARs are replaced. Players can still customize per-plugin settings on the server without losing them between deploys.

## Required GitHub secrets

Add these at https://github.com/svaka2000/MCSW/settings/secrets/actions:

| Secret | Required? | What it is |
|---|---|---|
| `MC_SERVER_HOST` | yes | SFTP hostname |
| `MC_SERVER_USER` | yes | SFTP username from your host panel |
| `MC_SERVER_PASSWORD` | yes | SFTP password (usually = your panel password) |
| `MC_SERVER_PORT` | yes | SFTP port (often non-standard on managed hosts, e.g. 2022) |
| `MC_PLUGINS_PATH` | yes | Path to plugins folder inside the SFTP root (`/plugins` for most hosts) |

## GravelHost specifically

GravelHost's SFTP panel shows these details under the Server → SFTP page:

- `MC_SERVER_HOST` — the part after `sftp://` and before `:` (e.g. `prla2.gravelhost.com`)
- `MC_SERVER_USER` — the username shown in the panel (looks like `xxxxx.f8247ebb`)
- `MC_SERVER_PASSWORD` — same as your GravelHost panel login password
- `MC_SERVER_PORT` — the port shown after the `:` in the SFTP address (`2022`)
- `MC_PLUGINS_PATH` — `/plugins`

## After a deploy lands

The host server keeps running the OLD JARs until you either:

- Restart the server in the panel ("Restart" button)
- Or open the panel console and run `reload confirm` (server-wide reload — works but plugins that bind native resources may not fully reload)

Either way, the new JAR files are sitting in `plugins/` after the deploy finishes — Bukkit just hasn't loaded them yet.

## Manual deploy (alternative)

The Build workflow always uploads JARs as a GitHub artifact, regardless of deploy secrets. Download the latest from https://github.com/svaka2000/MCSW/actions, drop into the panel's File Manager → `plugins/`, then restart.

## Local rebuild

```bash
cd ~/PvPTL
./gradlew build
# JARs land in {tourney,duels,aihelp,stats}/build/libs/
```
