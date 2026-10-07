package mchorse.bbs_mod.copilot;

import mchorse.bbs_mod.settings.values.core.ValueString;

public class CopilotApiKeyValue extends ValueString {
    public CopilotApiKeyValue(String id) {
        super(id, CopilotConfig.getInstance().getApiKey());
    }

    @Override
    public String get() {
        return CopilotConfig.getInstance().getApiKey();
    }

    @Override
    public void set(String value) {
        super.set(value);
        CopilotConfig.getInstance().setApiKey(value);
        CopilotConfig.getInstance().save();
    }
}
