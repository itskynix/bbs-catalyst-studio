package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueList;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * Forms seen only from inside the replay's head, each hung on a bone of the {@link PovRig}: a
 * watch on the wrist, a glove over the hand. They are drawn with the first-person hands and on top
 * of the hand model's own parts, which stay as they are.
 */
public class PovBodyParts extends ValueList<PovBodyParts.Part>
{
    public PovBodyParts(String id)
    {
        super(id);
    }

    public Part add(Form form, String bone)
    {
        Part part = new Part("");

        part.form.set(form);
        part.bone.set(bone);

        this.preNotify();
        this.add(part);
        this.sync();
        this.postNotify();

        return part;
    }

    public void remove(Part part)
    {
        this.preNotify(IValueListener.FLAG_UNMERGEABLE);
        this.list.remove(part);
        this.sync();
        this.postNotify(IValueListener.FLAG_UNMERGEABLE);
    }

    @Override
    protected Part create(String id)
    {
        return new Part(id);
    }

    public static class Part extends ValueGroup
    {
        public final ValueBoolean enabled = new ValueBoolean("enabled", true);
        public final ValueForm form = new ValueForm("form");
        public final ValueString bone = new ValueString("bone", PovRig.endOf(true));
        public final ValueTransform transform = new ValueTransform("transform", new Transform());

        public Part(String id)
        {
            super(id);

            this.add(this.enabled);
            this.add(this.form);
            this.add(this.bone);
            this.add(this.transform);
        }
    }
}
