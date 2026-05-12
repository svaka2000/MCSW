# Auto-deploy setup

Every push to `main` triggers a GitHub Actions workflow that:

1. Builds all three plugin JARs (`Tourney.jar`, `Duels.jar`, `AIHelp.jar`).
2. Uploads them as a GitHub Actions artifact (downloadable from the Actions tab — survives 30 days).
3. (Optional) SCPs them to your Minecraft server's `plugins/` folder.
4. (Optional) Hot-reloads each plugin via PlugManX so you don't have to restart.

## What runs out of the box

The **Build** workflow runs on every push. JARs become downloadable artifacts at:

```
https://github.com/svaka2000/MCSW/actions
```

No setup required. Plugin configs (`plugins/<PluginName>/config.yml`) are never touched — only JARs are replaced.

## To enable auto-deploy to your server

You need a server you SSH into. Add these secrets at:

```
https://github.com/svaka2000/MCSW/settings/secrets/actions
```

| Secret | Required? | What it is |
|---|---|---|
| `MC_SERVER_HOST` | yes | Server hostname or IP (e.g. `1.2.3.4` or `mc.pvptl.com`) |
| `MC_SERVER_USER` | yes | SSH username with write permission on the plugins folder |
| `MC_SERVER_SSH_KEY` | yes | Private SSH key (the whole `-----BEGIN OPENSSH PRIVATE KEY-----` block) |
| `MC_PLUGINS_PATH` | yes | Absolute path to plugins/ — e.g. `/home/mc/paper/plugins` |
| `MC_SERVER_PORT` | no | SSH port if non-standard. Defaults to 22. |
| `MC_SCREEN_NAME` | no | If your server runs under `screen -S <name>`, set this to enable hot reload via PlugManX. Skip if you'd rather restart manually. |

Once those are set, every push to `main` will:

- Build the JARs
- SCP them into `MC_PLUGINS_PATH`
- (If `MC_SCREEN_NAME` is set and PlugManX is installed) run `plugman reload Tourney`, `plugman reload Duels`, `plugman reload AIHelp` inside the server console.

If `MC_SCREEN_NAME` isn't set, the JARs are uploaded but **not** reloaded — you'd run `reload confirm` or restart the server yourself. The new JARs take effect on next plugin load.

## How to set up the SSH key

On your local machine (or the server admin's machine):

```bash
ssh-keygen -t ed25519 -C "github-actions-pvptl" -f ~/.ssh/pvptl_deploy
# Press enter for no passphrase (GitHub Actions doesn't handle passphrases easily)

# Add the public key to your server
ssh-copy-id -i ~/.ssh/pvptl_deploy.pub user@your-server

# Copy the private key contents into the MC_SERVER_SSH_KEY GitHub secret
cat ~/.ssh/pvptl_deploy
```

Restrict the deploy user on your server to write only in `plugins/`. Don't reuse your personal SSH key.

## Manual deploy (if you can't SSH from CI)

The Build workflow always uploads JARs as a GitHub artifact. Download the latest from the Actions tab, drop into `plugins/`, restart or `plugman reload <Plugin>`.

## Plugin hot-reload caveats

PlugManX reload works for most plugins, but plugins that bind native resources, register Brigadier commands, or use modern Paper lifecycle events may not fully reload. If anything misbehaves after a reload, a full server restart fixes it. Tourney and Duels both should reload cleanly. AIHelp may need a restart if its scheduled tasks get re-registered.

## Local rebuild

If you want to rebuild without going through CI:

```bash
./gradlew build
# JARs land in tourney/build/libs/, duels/build/libs/, aihelp/build/libs/
```
