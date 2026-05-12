package com.samarth.aihelp.commands;

import com.samarth.aihelp.AIHelpPlugin;
import com.samarth.aihelp.groq.ChatMessage;
import com.samarth.aihelp.groq.GroqClient;
import com.samarth.aihelp.memory.PlayerMemory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class AskCommand implements CommandExecutor {

    private final AIHelpPlugin plugin;

    public AskCommand(AIHelpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cPlayers only.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§7Usage: §e/ask <your question>");
            return true;
        }

        GroqClient client = plugin.groq();
        String prefix = plugin.getConfig().getString("messages.prefix", "§7[§bAI§7] §f");
        if (!client.isConfigured()) {
            p.sendMessage(plugin.getConfig().getString("messages.no-key",
                "§cAI help is offline — operator hasn't set the Groq API key yet."));
            return true;
        }

        String question = String.join(" ", args).trim();
        p.sendMessage(prefix + plugin.getConfig().getString("messages.thinking", "§7Thinking…"));

        PlayerMemory mem = plugin.memory();
        UUID id = p.getUniqueId();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(buildSystemPrompt(p.getName())));
        messages.addAll(mem.recent(id));
        ChatMessage userMsg = ChatMessage.user(question);
        messages.add(userMsg);

        int maxReplyChars = plugin.getConfig().getInt("groq.max-reply-chars", 400);

        client.chat(messages).whenComplete((reply, error) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    String msg = plugin.getConfig().getString("messages.error", "§cAI request failed: <reason>")
                        .replace("<reason>", rootCause(error));
                    p.sendMessage(msg);
                    plugin.getLogger().warning("Groq error: " + rootCause(error));
                    return;
                }
                String trimmed = reply.length() > maxReplyChars ? reply.substring(0, maxReplyChars - 1) + "…" : reply;
                p.sendMessage(prefix + trimmed);
                mem.append(id, userMsg, ChatMessage.assistant(trimmed));
            });
        });
        return true;
    }

    private String buildSystemPrompt(String playerName) {
        String serverName = plugin.getConfig().getString("server.name", "PvPTL");
        String desc = plugin.getConfig().getString("server.description",
            "A competitive Minecraft PvP server featuring tournaments and 1v1 duels.");
        return String.join("\n",
            "You are a helpful NPC on the " + serverName + " Minecraft server.",
            desc,
            "You are speaking to player " + playerName + " inside the game.",
            "Be concise (1-3 sentences). No markdown, no bullet points. Use plain text — Minecraft chat does not render formatting.",
            "If asked about commands, you may suggest: /tourney start, /tourney join, /tourney bracket, /tourney spectate, /duels queue, /duelkit list.",
            "If you do not know something specific to this server, say so honestly. Do not invent features.",
            "Stay in character as a friendly help NPC."
        );
    }

    private static String rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }
}
