package mchorse.bbs_mod.catalyst.ai;

import java.util.List;

/**
 * Who answers Catalyst AI: the built-in rule engine, or one of the hosted models. Each hosted one
 * lists the models offered in the settings (the first is the default) and, for the OpenAI
 * compatible one, the address it starts at; any other model name can be typed in by hand.
 */
public enum CatalystAIProvider
{
    OFFLINE("offline", "", List.of()),
    /* Text models only: the Live models speak over a socket, not generateContent. The 2.5 ones are open to
     * keys that used them before, and answer 404 to new ones. */
    GEMINI("gemini", "https://generativelanguage.googleapis.com/v1beta", List.of("gemini-3.8-flash", "gemini-4-argon", "gemini-3.5-flash-lite", "gemini-2.5-flash", "gemini-2.5-pro")),
    OPENAI("openai", "https://api.openai.com/v1", List.of("gpt-6-luna", "gpt-6.1-terra", "gpt-6.1-sol", "gpt-6-astra", "gpt-4o-mini", "gpt-4o")),
    ANTHROPIC("anthropic", "https://api.anthropic.com/v1", List.of("claude-sonnet-5-5", "claude-haiku-5-5", "claude-opus-5-5", "claude-fable-5-1")),
    /* deepseek-chat is DeepSeek-V3 and deepseek-reasoner DeepSeek-R1 */
    CUSTOM("custom", "https://api.deepseek.com/v1", List.of("deepseek-chat", "deepseek-reasoner"));

    public final String id;
    public final String defaultUrl;
    public final List<String> models;

    CatalystAIProvider(String id, String defaultUrl, List<String> models)
    {
        this.id = id;
        this.defaultUrl = defaultUrl;
        this.models = models;
    }

    public String getDefaultModel()
    {
        return this.models.isEmpty() ? "" : this.models.get(0);
    }

    public boolean isOnline()
    {
        return this != OFFLINE;
    }

    public String getLangKey()
    {
        return "bbs.catalyst_ai.provider." + this.id;
    }

    public static CatalystAIProvider fromId(String id)
    {
        for (CatalystAIProvider provider : values())
        {
            if (provider.id.equals(id))
            {
                return provider;
            }
        }

        return OFFLINE;
    }
}
