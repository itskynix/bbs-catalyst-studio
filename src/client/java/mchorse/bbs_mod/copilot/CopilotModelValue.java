package mchorse.bbs_mod.copilot;

import mchorse.bbs_mod.settings.values.core.ValueString;

import java.util.List;

public class CopilotModelValue extends ValueString {
    public static final List<String> MODELS = List.of(
        CopilotConfig.MODEL_3_8_FLASH
    );

    public CopilotModelValue(String id) {
        super(id, CopilotConfig.getInstance().getModel());
    }

    @Override
    public String get() {
        return CopilotConfig.getInstance().getModel();
    }

    @Override
    public void set(String value) {
        String normalized = CopilotConfig.normalizeModel(value);
        super.set(normalized);
        CopilotConfig.getInstance().setModel(normalized);
        CopilotConfig.getInstance().save();
    }
}
