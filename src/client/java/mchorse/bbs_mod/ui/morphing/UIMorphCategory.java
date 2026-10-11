package mchorse.bbs_mod.ui.morphing;

import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.ParticleForm;
import mchorse.bbs_mod.forms.forms.StructureForm;
import mchorse.bbs_mod.forms.forms.TrailForm;
import mchorse.bbs_mod.forms.forms.VanillaParticleForm;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.Set;

public enum UIMorphCategory
{
    ALL(UIKeys.MORPH_CATEGORY_ALL, Icons.GLOBE, 0x4fc3f7),
    RIGS(UIKeys.MORPH_CATEGORY_RIGS, Icons.POSE, 0x81c784),
    MOBS(UIKeys.MORPH_CATEGORY_MOBS, Icons.CHICKEN, 0xffb74d),
    VFX(UIKeys.MORPH_CATEGORY_VFX, Icons.PARTICLE, 0xba68c8),
    BLOCKS(UIKeys.MORPH_CATEGORY_BLOCKS, Icons.BLOCK, 0x4db6ac),
    FAVORITES(UIKeys.MORPH_CATEGORY_FAVORITES, Icons.FAVORITE, 0xffd54f);

    public final IKey title;
    public final Icon icon;
    public final int color;

    UIMorphCategory(IKey title, Icon icon, int color)
    {
        this.title = title;
        this.icon = icon;
        this.color = color;
    }

    public boolean matches(Form form, Set<String> favorites)
    {
        if (form == null)
        {
            return false;
        }

        if (this == ALL)
        {
            return true;
        }

        if (this == FAVORITES)
        {
            return favorites != null && favorites.contains(getFormKey(form));
        }

        if (this == RIGS)
        {
            return form instanceof ModelForm;
        }

        if (this == MOBS)
        {
            return form instanceof MobForm;
        }

        if (this == VFX)
        {
            return form instanceof ParticleForm || form instanceof VanillaParticleForm || form instanceof TrailForm;
        }

        if (this == BLOCKS)
        {
            return form instanceof BlockForm || form instanceof ItemForm || form instanceof StructureForm || form instanceof BillboardForm;
        }

        return false;
    }

    public static String getFormKey(Form form)
    {
        if (form == null)
        {
            return "";
        }

        String name = form.name.get();

        if (!name.isEmpty())
        {
            return name;
        }

        return form.getFormId() + ":" + form.getDisplayName();
    }

    public static String getFormTypeLabel(Form form)
    {
        if (form == null)
        {
            return "";
        }

        if (form instanceof ModelForm modelForm)
        {
            String modelKey = modelForm.model.get();

            return "Model (" + (modelKey.isEmpty() ? "BBS Bends" : modelKey) + ")";
        }

        if (form instanceof MobForm mobForm)
        {
            return "Mob (" + mobForm.mobID.get() + ")";
        }

        if (form instanceof ParticleForm)
        {
            return "Particle Scheme (Snowstorm)";
        }

        if (form instanceof VanillaParticleForm)
        {
            return "Vanilla Particle VFX";
        }

        if (form instanceof BlockForm)
        {
            return "Block Form";
        }

        if (form instanceof ItemForm)
        {
            return "Item Prop";
        }

        if (form instanceof StructureForm)
        {
            return "Structure Asset";
        }

        if (form instanceof TrailForm)
        {
            return "Trail Ribbon";
        }

        return form.getFormId();
    }

    public static String getFormTypeShort(Form form)
    {
        if (form instanceof ModelForm)
        {
            return "Model / Rig";
        }

        if (form instanceof MobForm)
        {
            return "Mob";
        }

        if (form instanceof ParticleForm || form instanceof VanillaParticleForm || form instanceof TrailForm)
        {
            return "VFX";
        }

        if (form instanceof BlockForm || form instanceof ItemForm || form instanceof StructureForm)
        {
            return "Block / Prop";
        }

        return "Custom";
    }
}
