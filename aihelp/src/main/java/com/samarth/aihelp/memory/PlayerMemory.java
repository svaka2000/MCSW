package com.samarth.aihelp.memory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.samarth.aihelp.groq.ChatMessage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Per-player rolling chat history. Cap at N pairs to avoid unbounded prompts.
 * Persisted to plugins/AIHelp/memory/&lt;uuid&gt;.json when memory.persist=true.
 */
public final class PlayerMemory {

    private final JavaPlugin plugin;
    private final File dir;
    private final Map<UUID, Deque<ChatMessage>> cache = new HashMap<>();

    public PlayerMemory(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "memory");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Could not create memory directory: " + dir);
        }
    }

    private boolean persist() {
        return plugin.getConfig().getBoolean("memory.persist", true);
    }

    private int historyPairs() {
        return Math.max(0, plugin.getConfig().getInt("memory.history-pairs", 4));
    }

    public synchronized List<ChatMessage> recent(UUID id) {
        Deque<ChatMessage> dq = cache.get(id);
        if (dq == null) {
            dq = loadFromDisk(id);
            cache.put(id, dq);
        }
        return new ArrayList<>(dq);
    }

    public synchronized void append(UUID id, ChatMessage userMsg, ChatMessage assistantMsg) {
        Deque<ChatMessage> dq = cache.computeIfAbsent(id, k -> loadFromDisk(k));
        dq.addLast(userMsg);
        dq.addLast(assistantMsg);
        int capPairs = historyPairs();
        while (dq.size() > capPairs * 2) {
            dq.removeFirst();
        }
        if (persist()) saveToDisk(id, dq);
    }

    public synchronized void clear(UUID id) {
        cache.remove(id);
        File f = fileFor(id);
        if (f.exists() && !f.delete()) {
            plugin.getLogger().warning("Failed to delete memory file: " + f);
        }
    }

    public synchronized void flushAll() {
        if (!persist()) return;
        for (Map.Entry<UUID, Deque<ChatMessage>> e : cache.entrySet()) {
            saveToDisk(e.getKey(), e.getValue());
        }
    }

    private Deque<ChatMessage> loadFromDisk(UUID id) {
        Deque<ChatMessage> dq = new LinkedList<>();
        File f = fileFor(id);
        if (!f.exists()) return dq;
        try {
            String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = arr.get(i).getAsJsonObject();
                dq.add(new ChatMessage(o.get("role").getAsString(), o.get("content").getAsString()));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Couldn't load memory for " + id + ": " + e.getMessage());
        }
        return dq;
    }

    private void saveToDisk(UUID id, Deque<ChatMessage> dq) {
        JsonArray arr = new JsonArray();
        for (ChatMessage m : dq) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role());
            o.addProperty("content", m.content());
            arr.add(o);
        }
        try {
            Files.writeString(fileFor(id).toPath(), arr.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't save memory for " + id + ": " + e.getMessage());
        }
    }

    private File fileFor(UUID id) {
        return new File(dir, id + ".json");
    }
}
