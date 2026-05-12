package com.samarth.aihelp.chat;

import com.samarth.aihelp.AIHelpPlugin;
import com.samarth.aihelp.groq.ChatMessage;
import com.samarth.aihelp.groq.GroqClient;
import com.samarth.aihelp.memory.PlayerMemory;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Watches public chat for "confused player" cues (configurable keywords) and quietly
 * offers an AI hint. Disabled by default — turn on via config (watcher.enabled=true).
 * Cooldown per player prevents spam.
 */
public final class ChatWatcher implements Listener {

    private final AIHelpPlugin plugin;
    private final Map<UUID, Long> lastHintAt = new HashMap<>();

    public ChatWatcher(AIHelpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!plugin.getConfig().getBoolean("watcher.enabled", false)) return;
        GroqClient groq = plugin.groq();
        if (!groq.isConfigured()) return;

        Player p = e.getPlayer();
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).toLowerCase();

        List<String> keywords = plugin.getConfig().getStringList("watcher.keywords");
        boolean matched = false;
        for (String kw : keywords) {
            if (text.contains(kw.toLowerCase())) { matched = true; break; }
        }
        if (!matched) return;

        long cooldownMs = plugin.getConfig().getLong("watcher.cooldown-seconds", 180L) * 1000L;
        long now = System.currentTimeMillis();
        Long last = lastHintAt.get(p.getUniqueId());
        if (last != null && now - last < cooldownMs) return;
        lastHintAt.put(p.getUniqueId(), now);

        UUID id = p.getUniqueId();
        PlayerMemory mem = plugin.memory();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(
            "You are a helpful NPC on the " + plugin.getConfig().getString("server.name", "PvPTL") + " server. " +
            "A player just typed in public chat — offer a brief, friendly hint (1-2 sentences). " +
            "Do not be intrusive; if their question isn't actually for help, say nothing useful (just acknowledge)."));
        ChatMessage userMsg = ChatMessage.user(text);
        messages.add(userMsg);

        String prefix = plugin.getConfig().getString("messages.prefix", "§7[§bAI§7] §f");

        groq.chat(messages).whenComplete((reply, error) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) return; // silent in watcher mode
                int cap = plugin.getConfig().getInt("groq.max-reply-chars", 400);
                String trimmed = reply.length() > cap ? reply.substring(0, cap - 1) + "…" : reply;
                p.sendMessage(prefix + trimmed);
                mem.append(id, userMsg, ChatMessage.assistant(trimmed));
            });
        });
    }
}
