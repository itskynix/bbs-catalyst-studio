package mchorse.bbs_mod.ui.morphing;

import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.AudioForm;
import mchorse.bbs_mod.forms.forms.BillboardForm;
import mchorse.bbs_mod.forms.forms.DecalForm;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.forms.HeightFogForm;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.VideoForm;
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

import java.util.Collection;
import java.util.Set;

public enum UIMorphCategory
{
    ALL(UIKeys.MORPH_CATEGORY_ALL, UIKeys.MORPH_STUDIO_ALL_TITLE, Icons.GLOBE, 0x4fc3f7),
    RECENT(UIKeys.MORPH_CATEGORY_RECENT, UIKeys.MORPH_STUDIO_RECENT_TITLE, Icons.TIME, 0x90a4ae),
    MODELS(UIKeys.MORPH_CATEGORY_MODELS, UIKeys.MORPH_CATEGORY_MODELS, Icons.POSE, 0x81c784),
    CREATURES(UIKeys.MORPH_CATEGORY_CREATURES, UIKeys.MORPH_CATEGORY_CREATURES, Icons.CHICKEN, 0xffb74d),
    BLOCKS(UIKeys.MORPH_CATEGORY_BLOCKS, UIKeys.MORPH_CATEGORY_BLOCKS, Icons.BLOCK, 0x4db6ac),
    PRODUCTION(UIKeys.MORPH_CATEGORY_PRODUCTION, UIKeys.MORPH_CATEGORY_PRODUCTION, Icons.FILM, 0xf06292),
    LIGHTING(UIKeys.MORPH_CATEGORY_LIGHTING, UIKeys.MORPH_CATEGORY_LIGHTING, Icons.LIGHT, 0xfff176),
    PARTICLES(UIKeys.MORPH_CATEGORY_PARTICLES, UIKeys.MORPH_CATEGORY_PARTICLES, Icons.PARTICLE, 0xba68c8),
    STRUCTURES(UIKeys.MORPH_CATEGORY_STRUCTURES, UIKeys.MORPH_CATEGORY_STRUCTURES, Icons.STRUCTURE, 0xa1887f),
    UTILITY(UIKeys.MORPH_CATEGORY_UTILITY, UIKeys.MORPH_CATEGORY_UTILITY, Icons.GEAR, 0x90caf9),
    FAVORITES(UIKeys.MORPH_CATEGORY_FAVORITES, UIKeys.MORPH_CATEGORY_FAVORITES, Icons.FAVORITE, 0xffd54f);

    public final IKey label;
    public final IKey title;
    public final Icon icon;
    public final int color;

    UIMorphCategory(IKey label, IKey title, Icon icon, int color)
    {
        this.label = label;
        this.title = title;
        this.icon = icon;
        this.color = color;
    }

    public boolean matches(Form form, Set<String> favorites, Collection<Form> recents)
    {
        if (form == null)
        {
            return false;
        }

        if (this == ALL)
        {
            return true;
        }

        if (this == RECENT)
        {
            return (recents != null && recents.contains(form)) || (favorites != null && favorites.contains(getFormKey(form)));
        }

        if (this == FAVORITES)
        {
            return favorites != null && favorites.contains(getFormKey(form));
        }

        return classify(form) == this;
    }

    /** The one category a form belongs to; everything unlisted is a utility. */
    public static UIMorphCategory classify(Form form)
    {
        if (UIMorphNaming.isEmpty(form))
        {
            return UTILITY;
        }

        if (form instanceof ModelForm)
        {
            return MODELS;
        }

        if (form instanceof MobForm)
        {
            return CREATURES;
        }

        if (form instanceof BlockForm || form instanceof ItemForm)
        {
            return BLOCKS;
        }

        if (form instanceof VideoForm || form instanceof AudioForm || form instanceof BillboardForm || form instanceof ExtrudedForm || form instanceof LabelForm || form instanceof DecalForm
            || form instanceof mchorse.bbs_mod.forms.forms.PhysicsFieldForm || form instanceof mchorse.bbs_mod.forms.forms.FakeSpaceForm || form instanceof mchorse.bbs_mod.forms.forms.NavigationVolumeForm)
        {
            return PRODUCTION;
        }

        if (form instanceof mchorse.bbs_mod.forms.forms.LightForm || form instanceof qualet.irlite.forms.LightForm || form instanceof HeightFogForm || form instanceof mchorse.bbs_mod.forms.forms.PostProcessForm)
        {
            return LIGHTING;
        }

        if (form instanceof ParticleForm || form instanceof VanillaParticleForm || form instanceof TrailForm)
        {
            return PARTICLES;
        }

        if (form instanceof StructureForm)
        {
            return STRUCTURES;
        }

        return UTILITY;
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
        return getFormTypeShort(form);
    }

    public static String getFormTypeShort(Form form)
    {
        if (form == null)
        {
            return "";
        }

        if (form instanceof ItemForm)
        {
            return UIKeys.MORPH_TYPE_ITEM.get();
        }

        switch (classify(form))
        {
            case MODELS: return UIKeys.MORPH_TYPE_MODEL.get();
            case CREATURES: return UIKeys.MORPH_TYPE_CREATURE.get();
            case BLOCKS: return UIKeys.MORPH_TYPE_BLOCK.get();
            case PRODUCTION: return UIKeys.MORPH_TYPE_PRODUCTION.get();
            case LIGHTING: return UIKeys.MORPH_TYPE_LIGHTING.get();
            case PARTICLES: return UIKeys.MORPH_TYPE_PARTICLE.get();
            case STRUCTURES: return UIKeys.MORPH_TYPE_STRUCTURE.get();
            default: return UIKeys.MORPH_TYPE_UTILITY.get();
        }
    }
}
