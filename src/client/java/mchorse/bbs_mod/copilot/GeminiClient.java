package mchorse.bbs_mod.copilot;

import mchorse.bbs_mod.data.DataParser;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class GeminiClient {
    private static final GeminiClient INSTANCE = new GeminiClient();

    private final HttpClient httpClient;

    public static GeminiClient getInstance() {
        return INSTANCE;
    }

    public GeminiClient() {
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(60))
            .build();
    }

    public static String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return CopilotConfig.DEFAULT_MODEL;
        }
        String cleaned = model.replace("models/", "").trim();
        return cleaned.isBlank() ? CopilotConfig.DEFAULT_MODEL : cleaned;
    }

    public CompletableFuture<String> generateContent(String prompt) {
        return this.generateContent(CopilotPromptManager.SYSTEM_INSTRUCTION, prompt);
    }

    public CompletableFuture<String> generateContent(String systemInstruction, String userPrompt) {
        CopilotConfig config = CopilotConfig.getInstance();
        String apiKey = config.getApiKey();
        String modelId = normalizeModel(config.getModel());
        float temperature = config.getTemperature();

        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Gemini API key is not configured."));
        }

        try {
            MapType root = new MapType();

            // systemInstruction
            if (systemInstruction != null && !systemInstruction.isBlank()) {
                MapType sysInst = new MapType();
                ListType sysParts = new ListType();
                MapType sysPart = new MapType();
                sysPart.putString("text", systemInstruction);
                sysParts.add(sysPart);
                sysInst.put("parts", sysParts);
                root.put("systemInstruction", sysInst);
            }

            // contents
            ListType contents = new ListType();
            MapType userContent = new MapType();
            userContent.putString("role", "user");
            ListType parts = new ListType();
            MapType textPart = new MapType();
            textPart.putString("text", userPrompt);
            parts.add(textPart);
            userContent.put("parts", parts);
            contents.add(userContent);
            root.put("contents", contents);

            // generationConfig
            MapType genConfig = new MapType();
            genConfig.putFloat("temperature", temperature);
            root.put("generationConfig", genConfig);

            String jsonPayload = DataToString.toString(root, true);

            String encodedKey = URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8);
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelId + ":generateContent?key=" + encodedKey;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

            return this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    int statusCode = response.statusCode();
                    String body = response.body();

                    BaseType parsed = DataParser.parse(body);
                    if (statusCode >= 200 && statusCode < 300) {
                        if (parsed instanceof MapType map) {
                            if (map.has("candidates")) {
                                ListType candidates = map.getList("candidates");
                                if (candidates.size() > 0 && candidates.get(0) instanceof MapType firstCandidate) {
                                    MapType content = firstCandidate.getMap("content");
                                    if (content != null && content.has("parts")) {
                                        ListType partsList = content.getList("parts");
                                        if (partsList.size() > 0 && partsList.get(0) instanceof MapType firstPart) {
                                            return firstPart.getString("text");
                                        }
                                    }
                                }
                            }
                        }
                        return "";
                    } else {
                        String errorMessage = "HTTP " + statusCode + ": " + body;
                        System.err.println("[BBS Copilot] Generate content failed: " + errorMessage);
                        throw new RuntimeException(errorMessage);
                    }
                });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<String> generateChat(java.util.List<mchorse.bbs_mod.copilot.chat.CopilotChatMessage> history) {
        CopilotConfig config = CopilotConfig.getInstance();
        String apiKey = config.getApiKey();
        String modelId = normalizeModel(config.getModel());
        float temperature = config.getTemperature();

        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Gemini API key is not configured."));
        }

        try {
            MapType root = new MapType();

            // systemInstruction
            MapType sysInst = new MapType();
            ListType sysParts = new ListType();
            MapType sysPart = new MapType();
            sysPart.putString("text", CopilotPromptManager.SYSTEM_INSTRUCTION);
            sysParts.add(sysPart);
            sysInst.put("parts", sysParts);
            root.put("systemInstruction", sysInst);

            // contents (multi-turn)
            ListType contents = new ListType();
            for (mchorse.bbs_mod.copilot.chat.CopilotChatMessage msg : history) {
                MapType turn = new MapType();
                turn.putString("role", msg.isUser() ? "user" : "model");

                ListType parts = new ListType();

                if (msg.isUser() && msg.hasImage()) {
                    MapType imgPart = new MapType();
                    MapType inlineData = new MapType();
                    inlineData.putString("mimeType", "image/png");
                    inlineData.putString("data", msg.imageBase64);
                    imgPart.put("inlineData", inlineData);
                    parts.add(imgPart);
                }

                if (msg.text != null && !msg.text.isBlank()) {
                    MapType textPart = new MapType();
                    textPart.putString("text", msg.text);
                    parts.add(textPart);
                } else if (!parts.isEmpty()) {
                    MapType textPart = new MapType();
                    textPart.putString("text", "Describe and choreograph camera for this scene.");
                    parts.add(textPart);
                }

                if (!parts.isEmpty()) {
                    turn.put("parts", parts);
                    contents.add(turn);
                }
            }
            root.put("contents", contents);

            // generationConfig
            MapType genConfig = new MapType();
            genConfig.putFloat("temperature", temperature);
            root.put("generationConfig", genConfig);

            String jsonPayload = DataToString.toString(root, true);
            String encodedKey = URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8);
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelId + ":generateContent?key=" + encodedKey;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

            return this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    int statusCode = response.statusCode();
                    String body = response.body();

                    if (statusCode >= 200 && statusCode < 300) {
                        BaseType parsed = DataParser.parse(body);
                        if (parsed instanceof MapType map) {
                            if (map.has("candidates")) {
                                ListType candidates = map.getList("candidates");
                                if (candidates.size() > 0 && candidates.get(0) instanceof MapType firstCandidate) {
                                    MapType content = firstCandidate.getMap("content");
                                    if (content != null && content.has("parts")) {
                                        ListType partsList = content.getList("parts");
                                        if (partsList.size() > 0 && partsList.get(0) instanceof MapType firstPart) {
                                            return firstPart.getString("text");
                                        }
                                    }
                                }
                            }
                        }
                        return "";
                    } else {
                        String errorMessage = "HTTP " + statusCode + ": " + body;
                        System.err.println("[BBS Copilot] Generate chat failed: " + errorMessage);
                        throw new RuntimeException(errorMessage);
                    }
                });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<ConnectionTestResult> testConnection() {
        CopilotConfig config = CopilotConfig.getInstance();
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture(new ConnectionTestResult(false, "API Key is empty"));
        }

        String modelId = normalizeModel(config.getModel());

        try {
            String jsonPayload = "{\"contents\":[{\"parts\":[{\"text\":\"ping\"}]}]}";
            String encodedKey = URLEncoder.encode(apiKey.trim(), StandardCharsets.UTF_8);
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelId + ":generateContent?key=" + encodedKey;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .build();

            return this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    int statusCode = response.statusCode();
                    String body = response.body();

                    if (statusCode >= 200 && statusCode < 300) {
                        return new ConnectionTestResult(true, statusCode, "Connected");
                    } else {
                        String errorMessage = "HTTP " + statusCode + ": " + body;
                        System.err.println("[BBS Copilot] Test connection failed: " + errorMessage);
                        return new ConnectionTestResult(false, statusCode, errorMessage);
                    }
                })
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String msg = cause.getMessage();
                    if (msg == null || msg.isBlank()) {
                        msg = cause.getClass().getSimpleName();
                    }
                    if (cause instanceof java.net.http.HttpTimeoutException || msg.toLowerCase().contains("timeout") || msg.toLowerCase().contains("timed out")) {
                        return new ConnectionTestResult(false, 408, "HttpTimeoutException: Request timed out");
                    }
                    int code = extractStatusCode(msg);
                    return new ConnectionTestResult(false, code, msg);
                });
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Error";
            int code = extractStatusCode(msg);
            return CompletableFuture.completedFuture(new ConnectionTestResult(false, code, msg));
        }
    }

    public static int extractStatusCode(String message) {
        if (message == null) {
            return -1;
        }
        int idx = message.indexOf("HTTP ");
        if (idx != -1 && idx + 8 <= message.length()) {
            try {
                return Integer.parseInt(message.substring(idx + 5, idx + 8).trim());
            } catch (Exception ignored) {}
        }
        if (message.contains("503")) return 503;
        if (message.contains("408")) return 408;
        if (message.contains("504")) return 504;
        if (message.contains("404")) return 404;
        if (message.contains("401")) return 401;
        if (message.contains("403")) return 403;
        if (message.contains("429")) return 429;
        return -1;
    }

    public static mchorse.bbs_mod.l10n.keys.IKey getUserFriendlyError(int statusCode) {
        if (statusCode == 503) {
            return mchorse.bbs_mod.l10n.L10n.lang("bbs.copilot.error.busy");
        } else if (statusCode == 408 || statusCode == 504) {
            return mchorse.bbs_mod.l10n.L10n.lang("bbs.copilot.error.timeout");
        } else if (statusCode == 404) {
            return mchorse.bbs_mod.l10n.L10n.lang("bbs.copilot.error.not_found");
        } else if (statusCode == 401 || statusCode == 403) {
            return mchorse.bbs_mod.l10n.L10n.lang("bbs.copilot.error.unauthorized");
        } else if (statusCode == 429) {
            return mchorse.bbs_mod.l10n.L10n.lang("bbs.copilot.error.rate_limit");
        } else {
            return mchorse.bbs_mod.l10n.L10n.lang("bbs.copilot.error.generic");
        }
    }

    public static mchorse.bbs_mod.l10n.keys.IKey getUserFriendlyError(String message) {
        if (message != null) {
            String lower = message.toLowerCase();
            if (lower.contains("timeout") || lower.contains("timed out") || lower.contains("timeoutexception")) {
                return mchorse.bbs_mod.l10n.L10n.lang("bbs.copilot.error.timeout");
            }
            if (message.contains("API key is not configured") || message.contains("API Key is empty") || message.contains("api_key_missing")) {
                return mchorse.bbs_mod.l10n.L10n.lang("bbs.copilot.error.unauthorized");
            }
        }
        int code = extractStatusCode(message);
        return getUserFriendlyError(code);
    }

    public static class ConnectionTestResult {
        public final boolean success;
        public final int statusCode;
        public final String message;

        public ConnectionTestResult(boolean success, String message) {
            this(success, success ? 200 : -1, message);
        }

        public ConnectionTestResult(boolean success, int statusCode, String message) {
            this.success = success;
            this.statusCode = statusCode;
            this.message = message;
        }
    }
}
