package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.catalyst.CatalystComposition;
import mchorse.bbs_mod.catalyst.CatalystLayer;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UICatalystPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The gain window of the sound clips that are picked: a slider from -24 to +24 dB with the factor it
 * stands for, a button back to 0 dB, and two ways to use it. Applied directly, the gain is put on the
 * clips as the slider moves, so their waveforms and the playback follow it; normalizing instead brings
 * the loudest sample of each clip to the level on the slider, which takes a moment to find.
 * Enter keeps it, Esc puts the clips back as they were.
 */
public class UIAudioGainOverlayPanel extends UIOverlayPanel
{
    private final UICatalystPanel panel;
    private final List<CatalystLayer> layers;
    private final float[] original;

    private final UISliderTrackpad db;
    private final UICirculate mode;
    private final UILabel factor;
    private final UILabel caption;

    private boolean finished;

    public UIAudioGainOverlayPanel(UICatalystPanel panel, List<CatalystLayer> layers)
    {
        super(UIKeys.CATALYST_NLE_GAIN);

        this.panel = panel;
        this.layers = layers;
        this.original = new float[layers.size()];

        for (int i = 0; i < this.original.length; i++)
        {
            this.original[i] = layers.get(i).gainDb;
        }

        this.db = new UISliderTrackpad((v) -> this.changed());
        this.db.limit(-24D, 24D);
        this.db.setValue(layers.get(0).gainDb);
        this.db.h(20);

        this.mode = new UICirculate((c) -> this.modeChanged());
        this.mode.addLabel(UIKeys.CATALYST_NLE_GAIN_MODE_APPLY);
        this.mode.addLabel(UIKeys.CATALYST_NLE_GAIN_MODE_NORMALIZE);
        this.mode.h(20);

        this.caption = UI.label(UIKeys.CATALYST_NLE_GAIN_DB);
        this.caption.h(14);

        this.factor = UI.label(mchorse.bbs_mod.l10n.keys.IKey.EMPTY);
        this.factor.color(Colors.GRAY);
        this.factor.h(14);

        UIButton reset = new UIButton(UIKeys.CATALYST_NLE_GAIN_RESET, (b) ->
        {
            this.db.setValueAndNotify(0D);
        });
        UIButton ok = new UIButton(UIKeys.CATALYST_NLE_APPLY, (b) -> this.confirm());
        UIButton cancel = new UIButton(UIKeys.SANDBOX_CANCEL, (b) -> this.cancel());

        reset.h(20);
        ok.h(20);
        cancel.h(20);

        UIElement container = new UIElement();

        container.relative(this.content).xy(10, 6).w(1F, -20).hTo(this.content.area, 1F, -6);
        container.column(6).vertical().stretch();
        container.add(this.caption, this.db, this.factor, UI.row(this.mode, reset).h(20), UI.row(ok, cancel).h(20));
        this.content.add(container);

        this.updateFactor();
        this.onClose((o) -> this.restore());
    }

    public static void open(UIContext context, UICatalystPanel panel, List<CatalystLayer> layers)
    {
        UIOverlay.addOverlay(context, new UIAudioGainOverlayPanel(panel, layers), 360, 190);
    }

    private boolean normalizing()
    {
        return this.mode.getValue() == 1;
    }

    private void updateFactor()
    {
        double value = this.db.getValue();

        this.factor.label = mchorse.bbs_mod.l10n.keys.IKey.raw(String.format(java.util.Locale.ROOT, "x%.3f", Math.pow(10D, value / 20D)));
        this.caption.label = this.normalizing() ? UIKeys.CATALYST_NLE_GAIN_TARGET : UIKeys.CATALYST_NLE_GAIN_DB;
    }

    private void modeChanged()
    {
        this.restore();
        this.updateFactor();

        if (this.normalizing())
        {
            this.db.setValue(-1D);
        }
        else
        {
            this.changed();
        }
    }

    /** The slider moved: applied directly, the clips take the gain at once */
    private void changed()
    {
        this.updateFactor();

        if (!this.normalizing())
        {
            for (CatalystLayer layer : this.layers)
            {
                layer.gainDb = (float) this.db.getValue();
            }

            this.panel.markDirty();
        }
    }

    private void restore()
    {
        if (this.finished)
        {
            return;
        }

        for (int i = 0; i < this.original.length; i++)
        {
            this.layers.get(i).gainDb = this.original[i];
        }
    }

    @Override
    public void confirm()
    {
        if (this.finished)
        {
            return;
        }

        if (this.normalizing())
        {
            this.normalize((float) this.db.getValue());
        }
        else
        {
            this.changed();
            this.finish();
        }
    }

    private void cancel()
    {
        this.restore();
        this.finished = true;
        this.close.clickItself();
    }

    private void finish()
    {
        this.finished = true;
        this.panel.markDirty();
        this.close.clickItself();
    }

    /** Each clip's own loudest sample is found in the background, and the gain that brings it to the target is put on it */
    private void normalize(float targetDb)
    {
        CatalystComposition comp = this.panel.getActiveComposition();
        int fps = comp != null && comp.fps > 0 ? comp.fps : 60;
        List<CatalystLayer> layers = this.layers;

        this.finished = true;
        this.close.clickItself();
        this.panel.getContext().notifyInfo(UIKeys.CATALYST_NLE_AI_WORKING);

        Thread thread = new Thread(() ->
        {
            float[] gains = new float[layers.size()];

            for (int i = 0; i < gains.length; i++)
            {
                CatalystLayer layer = layers.get(i);
                CatalystAudioAnalysis.Envelope envelope = layer.layerType == CatalystLayer.LayerType.AUDIO ? CatalystAudioAnalysis.envelope(layer.resourcePath) : null;

                if (envelope == null)
                {
                    gains[i] = Float.NaN;

                    continue;
                }

                double from = (layer.mediaOffset + layer.audioOffset) / (double) fps;
                double peak = CatalystAudioAnalysis.toDb(envelope.peakIn(from, from + layer.duration / (double) fps));

                gains[i] = peak <= -100D ? Float.NaN : (float) (targetDb - peak);
            }

            this.panel.runLater(() ->
            {
                for (int i = 0; i < gains.length; i++)
                {
                    if (!Float.isNaN(gains[i]))
                    {
                        layers.get(i).gainDb = Math.max(-60F, Math.min(60F, gains[i]));
                    }
                }

                this.panel.markDirty();
            });
        }, "CatalystNormalize");

        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.getKeyAction() == KeyAction.PRESSED)
        {
            if (context.getKeyCode() == GLFW.GLFW_KEY_ENTER)
            {
                this.confirm();

                return true;
            }

            if (context.getKeyCode() == GLFW.GLFW_KEY_ESCAPE)
            {
                this.cancel();

                return true;
            }
        }

        return super.subKeyPressed(context);
    }
}
