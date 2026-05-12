package com.samarth.aihelp.groq;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Minimal HTTP client for Groq's OpenAI-compatible chat completions endpoint.
 * All requests are async (returns CompletableFuture). The caller is responsible for
 * hopping back to the main thread before touching Bukkit state.
 */
public final class GroqClient {

    private static final String ENDPOINT = "https://api.groq.com/openai/v1/chat/completions";

    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final HttpClient http;

    public GroqClient(String apiKey, String model, int timeoutSeconds) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.timeout = Duration.ofSeconds(Math.max(2, timeoutSeconds));
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public CompletableFuture<String> chat(List<ChatMessage> messages) {
        if (!isConfigured()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Groq API key not set"));
        }
        String body = buildBody(messages);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(ENDPOINT))
            .timeout(timeout)
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenApply(this::extractContent);
    }

    private String buildBody(List<ChatMessage> messages) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model);
        root.addProperty("max_tokens", 300);
        root.addProperty("temperature", 0.7);
        JsonArray arr = new JsonArray();
        for (ChatMessage m : messages) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role());
            o.addProperty("content", m.content());
            arr.add(o);
        }
        root.add("messages", arr);
        return root.toString();
    }

    private String extractContent(HttpResponse<String> resp) {
        int code = resp.statusCode();
        String raw = resp.body() == null ? "" : resp.body();
        if (code < 200 || code >= 300) {
            String snippet = raw.length() > 200 ? raw.substring(0, 200) + "…" : raw;
            throw new RuntimeException("HTTP " + code + ": " + snippet);
        }
        try {
            JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
            JsonArray choices = obj.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("no choices in response");
            }
            JsonObject first = choices.get(0).getAsJsonObject();
            JsonObject msg = first.getAsJsonObject("message");
            if (msg == null) throw new RuntimeException("no message in first choice");
            return msg.get("content").getAsString().trim();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("parse error: " + e.getMessage(), e);
        }
    }
}
