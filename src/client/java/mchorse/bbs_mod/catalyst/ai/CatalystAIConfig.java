package mchorse.bbs_mod.catalyst.ai;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/**
 * Catalyst AI's settings: the chosen provider and, per provider, the API key, the model and (for
 * the OpenAI compatible one) the base address. Kept in {@code bbs/catalyst_ai.json} in the game
 * folder, on this machine only - the file is plain text, so it is never shipped with a world or a
 * film, and nothing in it is ever sent anywhere but to the provider it belongs to.
 */
public class CatalystAIConfig
{
    private static CatalystAIConfig instance;

    public CatalystAIProvider provider = CatalystAIProvider.OFFLINE;

    private final Map<CatalystAIProvider, String> keys = new EnumMap<>(CatalystAIProvider.class);
    private final Map<CatalystAIProvider, String> models = new EnumMap<>(CatalystAIProvider.class);
    private final Map<CatalystAIProvider, String> urls = new EnumMap<>(CatalystAIProvider.class);

    public static CatalystAIConfig get()
    {
        if (instance == null)
        {
            instance = new CatalystAIConfig();
            instance.load();
        }

        return instance;
    }

    public static File getFile()
    {
        return BBSMod.getGamePath("bbs/catalyst_ai.json");
    }

    public String getKey(CatalystAIProvider provider)
    {
        return this.keys.getOrDefault(provider, "");
    }

    public void setKey(CatalystAIProvider provider, String key)
    {
        this.keys.put(provider, key == null ? "" : key.trim());
    }

    public String getModel(CatalystAIProvider provider)
    {
        String model = this.models.getOrDefault(provider, "").trim();

        /* "models/x" is how Google lists its models; the address adds that prefix itself */
        while (model.startsWith("models/"))
        {
            model = model.substring("models/".length()).trim();
        }

        return model.isBlank() ? provider.getDefaultModel() : model;
    }

    public void setModel(CatalystAIProvider provider, String model)
    {
        this.models.put(provider, model == null ? "" : model.trim());
    }

    public String getUrl(CatalystAIProvider provider)
    {
        String url = this.urls.getOrDefault(provider, "");

        url = url.isBlank() ? provider.defaultUrl : url;

        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public void setUrl(CatalystAIProvider provider, String url)
    {
        this.urls.put(provider, url == null ? "" : url.trim());
    }

    /** The provider that will really answer: a hosted one without a key falls back to the rule engine. */
    public CatalystAIProvider getEffectiveProvider()
    {
        return this.provider.isOnline() && !this.getKey(this.provider).isBlank() ? this.provider : CatalystAIProvider.OFFLINE;
    }

    public void load()
    {
        File file = getFile();

        if (!file.isFile())
        {
            return;
        }

        MapType data = DataToString.mapFromString(readSilently(file));

        if (data == null)
        {
            return;
        }

        this.provider = CatalystAIProvider.fromId(data.getString("provider", "offline"));

        for (CatalystAIProvider provider : CatalystAIProvider.values())
        {
            MapType entry = data.getMap(provider.id, null);

            if (entry != null)
            {
                this.setKey(provider, entry.getString("key", ""));
                this.setModel(provider, entry.getString("model", ""));
                this.setUrl(provider, entry.getString("url", ""));
            }
        }
    }

    public void save()
    {
        MapType data = new MapType();

        data.putString("provider", this.provider.id);

        for (CatalystAIProvider provider : CatalystAIProvider.values())
        {
            if (!provider.isOnline())
            {
                continue;
            }

            MapType entry = new MapType();

            entry.putString("key", this.getKey(provider));
            entry.putString("model", this.models.getOrDefault(provider, ""));
            entry.putString("url", this.urls.getOrDefault(provider, ""));
            data.put(provider.id, entry);
        }

        File file = getFile();

        file.getParentFile().mkdirs();
        DataToString.writeSilently(file, data, true);
    }

    private static String readSilently(File file)
    {
        try
        {
            return java.nio.file.Files.readString(file.toPath());
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
