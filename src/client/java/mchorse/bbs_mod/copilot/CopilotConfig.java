package mchorse.bbs_mod.copilot;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataParser;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.util.List;

public class CopilotConfig {
    private static final CopilotConfig INSTANCE = new CopilotConfig();

    public static final String MODEL_3_8_FLASH = "gemini-3.8-flash";

    public static final String DEFAULT_MODEL = MODEL_3_8_FLASH;
    public static final float DEFAULT_TEMPERATURE = 0.7f;

    public static final List<String> MODELS = List.of(MODEL_3_8_FLASH);

    private String apiKey = "";
    private String model = DEFAULT_MODEL;
    private float temperature = DEFAULT_TEMPERATURE;

    public static CopilotConfig getInstance() {
        return INSTANCE;
    }

    public static String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return DEFAULT_MODEL;
        }
        String trimmed = model.trim();
        while (trimmed.startsWith("models/")) {
            trimmed = trimmed.substring("models/".length());
        }
        return trimmed.isBlank() ? DEFAULT_MODEL : trimmed;
    }

    private CopilotConfig() {
        this.load();
    }

    public static File getConfigFile() {
        File runDir;
        try {
            runDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toFile();
        } catch (Throwable t) {
            runDir = MinecraftClient.getInstance() != null ? MinecraftClient.getInstance().runDirectory : new File(".");
        }
        File bbsDir = new File(runDir != null ? runDir : new File("."), "bbs");
        if (!bbsDir.exists()) {
            bbsDir.mkdirs();
        }
        return new File(bbsDir, "copilot_config.json");
    }

    public synchronized void load() {
        File file = getConfigFile();
        if (!file.exists()) {
            return;
        }

        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            BaseType data = DataParser.parse(content);
            if (data instanceof MapType map) {
                if (map.has("apiKey")) {
                    this.apiKey = map.getString("apiKey");
                }
                if (map.has("model")) {
                    this.model = normalizeModel(map.getString("model"));
                }
                if (map.has("temperature")) {
                    this.temperature = map.getFloat("temperature", DEFAULT_TEMPERATURE);
                }
            }
        } catch (Exception e) {
            System.err.println("[BBS Copilot] Failed to load copilot_config.json: " + e.getMessage());
        }
    }

    public synchronized void save() {
        File file = getConfigFile();
        try {
            MapType map = new MapType();
            map.putString("apiKey", this.apiKey != null ? this.apiKey : "");
            map.putString("model", normalizeModel(this.model));
            map.putFloat("temperature", this.temperature);

            String json = DataToString.toString(map, true);
            Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[BBS Copilot] Failed to save copilot_config.json: " + e.getMessage());
        }
    }

    public String getApiKey() {
        return this.apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
    }

    public String getModel() {
        return normalizeModel(this.model);
    }

    public void setModel(String model) {
        this.model = normalizeModel(model);
    }

    public float getTemperature() {
        return this.temperature;
    }

    public void setTemperature(float temperature) {
        this.temperature = Math.max(0.0f, Math.min(2.0f, temperature));
    }
}
