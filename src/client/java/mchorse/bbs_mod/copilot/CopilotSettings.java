package mchorse.bbs_mod.copilot;

import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class CopilotSettings {
    public static CopilotApiKeyValue apiKey;
    public static CopilotModelValue model;
    public static ValueFloat temperature;
    public static CopilotTestConnectionValue testConnection;
    public static CopilotClearChatsValue clearChats;

    public static void register(SettingsBuilder builder) {
        builder.category("copilot", Icons.PROCESSOR);

        apiKey = new CopilotApiKeyValue("api_key");
        builder.register(apiKey);

        model = new CopilotModelValue("model");
        builder.register(model);

        temperature = builder.getFloat("temperature", CopilotConfig.getInstance().getTemperature(), 0.0f, 2.0f);
        temperature.slider(0.05f);
        temperature.postCallback((val, flag) -> {
            CopilotConfig.getInstance().setTemperature(temperature.get());
            CopilotConfig.getInstance().save();
        });

        testConnection = new CopilotTestConnectionValue("test_connection");
        builder.register(testConnection);

        clearChats = new CopilotClearChatsValue("clear_chats");
        builder.register(clearChats);
    }
}
