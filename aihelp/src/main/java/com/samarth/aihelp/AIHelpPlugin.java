package com.samarth.aihelp;

import com.samarth.aihelp.chat.ChatWatcher;
import com.samarth.aihelp.commands.AIHelpCommand;
import com.samarth.aihelp.commands.AskCommand;
import com.samarth.aihelp.groq.GroqClient;
import com.samarth.aihelp.memory.PlayerMemory;
import java.util.List;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AIHelpPlugin extends JavaPlugin {

    private GroqClient groq;
    private PlayerMemory memory;
    private ChatWatcher watcher;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.memory = new PlayerMemory(this);
        rebuildGroqClient();

        AskCommand ask = new AskCommand(this);
        AIHelpCommand admin = new AIHelpCommand(this);
        bind("ask", ask);
        bind("aihelp", admin);

        this.watcher = new ChatWatcher(this);
        getServer().getPluginManager().registerEvents(watcher, this);

        if (getConfig().getString("groq.api-key", "").isBlank()) {
            getLogger().warning("Groq API key is not set. Run /aihelp setkey <key> in-game (op).");
        } else {
            getLogger().info("AI help enabled — Groq client ready (" + getConfig().getString("groq.model") + ").");
        }

        if (getServer().getPluginManager().getPlugin("Citizens") != null) {
            getLogger().info("Citizens detected — NPC bindings will be added in a future patch.");
        }
    }

    @Override
    public void onDisable() {
        if (memory != null) memory.flushAll();
    }

    public GroqClient groq() { return groq; }
    public PlayerMemory memory() { return memory; }

    /** Called after /aihelp setkey or /aihelp reload so the HTTP client picks up changes. */
    public void rebuildGroqClient() {
        // Priority: operator override (api-key) > embedded character-split default (api-key-parts).
        String key = getConfig().getString("groq.api-key", "");
        if (key == null || key.isBlank()) {
            List<String> parts = getConfig().getStringList("groq.api-key-parts");
            if (!parts.isEmpty()) {
                StringBuilder sb = new StringBuilder(parts.size());
                for (String p : parts) sb.append(p);
                key = sb.toString();
            }
        }
        String model = getConfig().getString("groq.model", "llama-3.3-70b-versatile");
        int timeout = getConfig().getInt("groq.timeout-seconds", 20);
        this.groq = new GroqClient(key, model, timeout);
    }

    private void bind(String name, Object executor) {
        PluginCommand pc = getCommand(name);
        if (pc == null) {
            getLogger().severe("Command '" + name + "' missing from plugin.yml");
            return;
        }
        if (executor instanceof org.bukkit.command.CommandExecutor ce) pc.setExecutor(ce);
        if (executor instanceof org.bukkit.command.TabCompleter tc) pc.setTabCompleter(tc);
    }
}
