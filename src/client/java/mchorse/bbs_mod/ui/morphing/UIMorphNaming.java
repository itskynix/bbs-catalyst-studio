package mchorse.bbs_mod.ui.morphing;

import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.forms.forms.AudioForm;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.DecalForm;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.FramebufferForm;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.forms.PortalForm;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.forms.forms.VideoForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * What the Morphing Studio calls a form: a readable title ("Sabitleme Noktası", "Bucur Bucur"),
 * the technical name it is stored under (kept, one line below), and nothing else made up.
 */
public class UIMorphNaming
{
    public static boolean isEmpty(Form form)
    {
        return form != null && form.name.get().isEmpty() && "none".equals(form.getDisplayName());
    }

    /** The technical name: what the form was called before it was made readable. */
    public static String code(Form form)
    {
        if (form == null)
        {
            return "";
        }

        if (!form.name.get().isEmpty())
        {
            return form.getFormId();
        }

        return form.getDisplayName();
    }

    public static String title(Form form)
    {
        if (form == null)
        {
            return "";
        }

        String custom = form.name.get();

        if (!custom.isEmpty())
        {
            return custom;
        }

        if (isEmpty(form))
        {
            return UIKeys.MORPH_NAME_EMPTY.get();
        }

        IKey fixed = fixed(form);

        if (fixed != null)
        {
            return fixed.get();
        }

        try
        {
            if (form instanceof ModelForm model)
            {
                return model(model.model.get(), UIKeys.MORPH_NAME_MODEL);
            }

            if (form instanceof MobForm mob)
            {
                Identifier id = Identifier.tryParse(mob.mobID.get());

                if (id != null && Registries.ENTITY_TYPE.containsId(id))
                {
                    return Registries.ENTITY_TYPE.get(id).getName().getString();
                }

                return mob.mobID.get().isEmpty() ? UIKeys.MORPH_NAME_MOB.get() : humanize(mob.mobID.get());
            }

            if (form instanceof BlockForm block)
            {
                return block.blockState.get().getBlock().getName().getString();
            }

            if (form instanceof ItemForm item)
            {
                return item.stack.get().getName().getString();
            }
        }
        catch (Exception e)
        {
            /* a registry that does not know the id: the readable form of the id will do */
        }

        return humanize(form.getDisplayName());
    }

    /** Forms that are one thing whatever they hold carry the name of that thing. */
    private static IKey fixed(Form form)
    {
        if (form instanceof AnchorForm) return UIKeys.MORPH_NAME_ANCHOR;
        if (form instanceof FramebufferForm) return UIKeys.MORPH_NAME_FRAMEBUFFER;
        if (form instanceof PortalForm) return UIKeys.MORPH_NAME_PORTAL;
        if (form instanceof TrailForm) return UIKeys.MORPH_NAME_TRAIL;
        if (form instanceof ParticleForm) return UIKeys.MORPH_NAME_PARTICLE;
        if (form instanceof VanillaParticleForm) return UIKeys.MORPH_NAME_VANILLA_PARTICLE;
        if (form instanceof VideoForm) return UIKeys.MORPH_NAME_VIDEO;
        if (form instanceof AudioForm) return UIKeys.MORPH_NAME_AUDIO;
        if (form instanceof ExtrudedForm) return UIKeys.MORPH_NAME_EXTRUDED;
        if (form instanceof LabelForm) return UIKeys.MORPH_NAME_LABEL;
        if (form instanceof DecalForm) return UIKeys.MORPH_NAME_DECAL;
        if (form instanceof BillboardForm) return UIKeys.MORPH_NAME_BILLBOARD;
        if (form instanceof StructureForm) return UIKeys.MORPH_NAME_STRUCTURE;

        return null;
    }

    /** "Bucur_bucur/bucur" is Bucur Bucur, "player/steve_3d" is Steve 3D: the folder only when it already says the file's name. */
    private static String model(String key, IKey fallback)
    {
        if (key == null || key.isEmpty())
        {
            return fallback.get();
        }

        String[] parts = key.split("/");
        String last = humanize(parts[parts.length - 1]);

        if (parts.length > 1)
        {
            String folder = humanize(parts[parts.length - 2]);

            if (!last.isEmpty() && folder.toLowerCase().contains(last.toLowerCase()))
            {
                return folder;
            }
        }

        return last.isEmpty() ? fallback.get() : last;
    }

    /**
     * The folder of a model under assets/models, from its model key: 'player/steve' is 'player',
     * 'props/chairs/oak' is 'props/chairs' and 'steve' has none, which is the empty string.
     */
    public static String modelFolder(String key)
    {
        if (key == null)
        {
            return "";
        }

        int colon = key.indexOf(':');
        String path = colon >= 0 ? key.substring(colon + 1) : key;
        int slash = path.lastIndexOf('/');

        return slash <= 0 ? "" : path.substring(0, slash);
    }

    /** A folder path as a heading: 'props/chairs' is 'Props / Chairs' */
    public static String folderTitle(String folder)
    {
        StringBuilder out = new StringBuilder();

        for (String part : folder.split("/"))
        {
            String word = humanize(part);

            if (word.isEmpty())
            {
                continue;
            }

            if (out.length() > 0)
            {
                out.append(" / ");
            }

            out.append(word);
        }

        return out.length() == 0 ? folder : out.toString();
    }

    /** Namespace and extension off, separators to spaces, every word started with a capital. */
    public static String humanize(String raw)
    {
        if (raw == null)
        {
            return "";
        }

        String text = raw;
        int colon = text.indexOf(':');

        if (colon >= 0)
        {
            text = text.substring(colon + 1);
        }

        int slash = text.lastIndexOf('/');

        if (slash >= 0 && slash < text.length() - 1)
        {
            text = text.substring(slash + 1);
        }

        int dot = text.lastIndexOf('.');

        if (dot > 0 && text.length() - dot <= 6)
        {
            text = text.substring(0, dot);
        }

        StringBuilder out = new StringBuilder();

        for (String word : text.split("[_\\-/\\s]+"))
        {
            if (word.isEmpty())
            {
                continue;
            }

            if (out.length() > 0)
            {
                out.append(' ');
            }

            if (word.matches("\\d+[a-zA-Z]{1,2}"))
            {
                out.append(word.toUpperCase());
            }
            else
            {
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }

        return out.toString();
    }
}
