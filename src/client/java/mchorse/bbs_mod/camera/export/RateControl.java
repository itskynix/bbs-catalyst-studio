package mchorse.bbs_mod.camera.export;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;

/**
 * Rate control methods for video encoding.
 */
public enum RateControl
{
    CRF("crf", IKey.raw("CRF / Constant Quality")),
    CBR("cbr", IKey.raw("CBR (Constant Bitrate)")),
    VBR("vbr", IKey.raw("VBR (Target Bitrate)")),
    LOSSLESS("lossless", IKey.raw("Lossless"));

    private final String id;
    private final IKey label;

    RateControl(String id, IKey label)
    {
        this.id = id;
        this.label = label;
    }

    public String getId()
    {
        return this.id;
    }

    /** The label in the current language; the English one written above is the fallback. */
    public IKey getLabel()
    {
        return L10n.lang("bbs.ui.deliver.rate." + this.name().toLowerCase(), this.label.get());
    }

    public static RateControl fromId(String id)
    {
        if (id == null)
        {
            return CRF;
        }

        for (RateControl rc : values())
        {
            if (rc.id.equalsIgnoreCase(id) || rc.name().equalsIgnoreCase(id))
            {
                return rc;
            }
        }

        return CRF;
    }
}
