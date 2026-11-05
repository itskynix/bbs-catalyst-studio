package mchorse.bbs_mod.ui.film.clips.pov;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.utils.Ids;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The particle clip's picker: every particle a puff can be made of, as a cell with the particle's
 * own first sprite (from the game's particle atlas, by the particle's description in the
 * resources), its name and its id.
 */
public class UIPovParticlePicker extends UIPovGridPicker<String>
{
    /** The sprite of each particle, looked up once; empty for one without a sprite (a block's or an item's crumbs) */
    private static final Map<String, Optional<Sprite>> SPRITES = new HashMap<>();

    private final Consumer<String> callback;
    private final String current;

    public UIPovParticlePicker(String current, Consumer<String> callback)
    {
        super(L10n.lang("bbs.ui.film.pov_clips.particle.pick"));

        this.current = current;
        this.callback = callback;
        this.refresh();
    }

    /** A particle's name as people say it: its id's path, its words spaced and capitalised */
    public static String name(String id)
    {
        Identifier identifier = Identifier.tryParse(id);
        String path = identifier == null ? id : identifier.getPath();
        StringBuilder builder = new StringBuilder();

        for (String word : path.split("_"))
        {
            if (!word.isEmpty())
            {
                builder.append(builder.length() == 0 ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }

        return builder.toString();
    }

    private static Sprite sprite(String id)
    {
        return SPRITES.computeIfAbsent(id, (key) ->
        {
            Identifier identifier = Identifier.tryParse(key);

            if (identifier == null)
            {
                return Optional.empty();
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            Optional<Resource> resource = mc.getResourceManager().getResource(Ids.of(identifier.getNamespace(), "particles/" + identifier.getPath() + ".json"));

            if (resource.isEmpty())
            {
                return Optional.empty();
            }

            try (Reader reader = resource.get().getReader())
            {
                JsonElement json = JsonParser.parseReader(reader);
                JsonArray textures = json.isJsonObject() && ((JsonObject) json).has("textures") ? ((JsonObject) json).getAsJsonArray("textures") : null;
                AbstractTexture atlas = mc.getTextureManager().getTexture(SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);

                if (textures == null || textures.isEmpty() || !(atlas instanceof SpriteAtlasTexture particles))
                {
                    return Optional.empty();
                }

                Identifier texture = Identifier.tryParse(textures.get(0).getAsString());

                return texture == null ? Optional.empty() : Optional.of(particles.getSprite(texture));
            }
            catch (Exception e)
            {
                return Optional.empty();
            }
        }).orElse(null);
    }

    @Override
    protected float cellAspect()
    {
        return 0.45F;
    }

    @Override
    protected List<String> getEntries()
    {
        return UIPovParticleClip.particles();
    }

    @Override
    protected boolean matches(String entry, String needle)
    {
        return entry.contains(needle) || name(entry).toLowerCase(Locale.ROOT).contains(needle);
    }

    @Override
    protected void pick(String entry)
    {
        this.callback.accept(entry);
    }

    @Override
    protected boolean isCurrent(String entry)
    {
        return entry.equals(this.current);
    }

    @Override
    protected String describe(String entry)
    {
        return entry;
    }

    @Override
    protected void renderCell(UIContext context, String entry, int x, int y, int w, int h, boolean hovered)
    {
        FontRenderer font = context.batcher.getFont();
        Sprite sprite = sprite(entry);
        int ix = x + 4;
        int iy = y + (h - 24) / 2;

        context.batcher.box(ix, iy, ix + 24, iy + 24, 0xff101010);

        if (sprite != null)
        {
            context.batcher.getContext().drawSprite(ix + 2, iy + 2, 0, 20, 20, sprite);
        }

        context.batcher.textShadow(font.limitToWidth(name(entry), w - 36), x + 32, y + h / 2 - font.getHeight(), Colors.WHITE);
        context.batcher.textShadow(font.limitToWidth(entry, w - 36), x + 32, y + h / 2 + 2, Colors.A100 | 0x888888);
    }
}
