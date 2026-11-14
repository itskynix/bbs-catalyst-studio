package mchorse.bbs_mod.ui.forms;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.morphing.UIMorphStudioScreen;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.forms.categories.UIFormCategory;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.BBSSettings;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class UIFormPalette extends UIElement implements IUIFormList
{
    public UIFormList list;
    public UIFormEditor editor;

    /** The podium view of the list; only the pickers opened through {@link #open} have it */
    public UIMorphStudioScreen studio;

    public Consumer<Form> callback;

    private UIFormCategory lastSelected;
    private boolean background = true;
    private boolean cantExit;
    private boolean immersive;
    private boolean canModify;
    private boolean classic;

    /** The picker comes up in the view it was left in last */
    private static boolean lastClassic;

    public static UIFormPalette open(UIElement parent, boolean editing, Form form, Consumer<Form> callback)
    {
        return open(parent, editing, form, false, callback);
    }

    public static UIFormPalette open(UIElement parent, boolean editing, Form form, boolean ignore, Consumer<Form> callback)
    {
        UIContext context = parent.getContext();

        if (!ignore)
        {
            if (!parent.getRoot().getChildren(UIFormPalette.class).isEmpty() || context == null)
            {
                return null;
            }
        }

        context.unfocus();

        UIFormPalette palette = new UIFormPalette(callback);

        palette.withStudio();
        palette.resetFlex().full(parent);
        palette.resize();

        parent.add(palette);

        palette.setSelected(form);
        palette.edit(editing);

        return palette;
    }

    public UIFormPalette(Consumer<Form> callback)
    {
        this.callback = callback;

        this.list = new UIFormList(this);
        this.list.full(this);

        this.editor = new UIFormEditor(this);
        this.editor.full(this);
        this.editor.setVisible(false);

        this.add(this.list, this.editor);

        this.eventPropagataion(EventPropagation.BLOCK_INSIDE).markContainer();

        this.keys().register(Keys.FORMS_EDIT, () ->
        {
            if (!this.editor.isEditing())
            {
                this.toggleEditor();
            }
        });
    }

    /** Gives this palette the podium picker; the classic list stays one click away. */
    public UIFormPalette withStudio()
    {
        if (this.studio != null || this.immersive || this.cantExit)
        {
            return this;
        }

        this.studio = new UIMorphStudioScreen(new UIMorphStudioScreen.Host()
        {
            @Override
            public void confirmForm(Form form)
            {
                UIFormPalette.this.accept(form);
                UIFormPalette.this.exit();
            }

            @Override
            public void editForm(Form form)
            {
                UIFormPalette.this.setSelected(form);
                UIFormPalette.this.edit(true);
            }

            @Override
            public void showClassic()
            {
                UIFormPalette.this.setClassic(true);
            }

            @Override
            public void close()
            {
                UIFormPalette.this.exit();
            }
        }, true);
        this.studio.full(this);
        this.studio.setupForms(BBSModClient.getFormCategories());

        UIIcon back = new UIIcon(Icons.ARROW_LEFT, (b) -> this.setClassic(false));

        back.tooltip(UIKeys.MORPH_STUDIO_BACK_TO_PICKER, Direction.TOP);
        this.list.bar.add(back);
        this.add(this.studio);

        this.classic = lastClassic;
        this.syncViews();

        return this;
    }

    /** What the picker is called: character form, model block form... */
    public UIFormPalette title(IKey title)
    {
        if (this.studio != null)
        {
            this.studio.setTitle(title);
        }

        return this;
    }

    private void setClassic(boolean classic)
    {
        this.classic = classic;
        lastClassic = classic;

        if (classic && this.studio != null && this.studio.getSelectedForm() != null)
        {
            this.list.setSelected(this.studio.getSelectedForm());
            this.list.scrollToSelected();
        }
        else if (!classic && this.studio != null)
        {
            Form form = this.list.getSelected();

            if (form != null)
            {
                this.studio.setSelectedForm(form);
            }
        }

        this.syncViews();
    }

    private void syncViews()
    {
        boolean editing = this.editor.isEditing();
        boolean podium = this.studio != null && !this.classic;

        this.list.setVisible(!editing && !podium);
        this.editor.setVisible(editing);

        if (this.studio != null)
        {
            this.studio.setVisible(!editing && podium);
        }
    }

    public void noBackground()
    {
        this.background = false;
    }

    public void cantExit()
    {
        this.cantExit = true;

        this.list.close.removeFromParent();
        this.eventPropagataion(EventPropagation.PASS);
    }

    public void canModify()
    {
        this.canModify = true;
    }

    public void immersive()
    {
        this.immersive = true;
    }

    public UIFormPalette updatable()
    {
        this.editor.renderer.updatable();

        return this;
    }

    public void edit(boolean editing)
    {
        if (editing != this.editor.isEditing())
        {
            this.toggleEditor();
        }
    }

    @Override
    public void exit()
    {
        if (!this.editor.isEditing())
        {
            if (!this.cantExit)
            {
                this.removeFromParent();
            }
        }
        else
        {
            this.toggleEditor();
        }
    }

    /**
     * In the morphing panel there is nothing to close — the palette is the panel — so a
     * double-click goes straight into editing the form instead.
     */
    @Override
    public void confirm()
    {
        if (this.immersive)
        {
            if (!this.editor.isEditing())
            {
                this.toggleEditor();
            }
        }
        else
        {
            this.exit();
        }
    }

    @Override
    public void toggleEditor()
    {
        this.events.emit(new UIToggleEditorEvent(this, !this.editor.isEditing()));

        if (!this.editor.isEditing())
        {
            Form form = this.list.getSelected();

            if (this.editor.edit(form))
            {
                this.lastSelected = this.list.getSelectedCategory();
            }
        }
        else
        {
            Form form = this.editor.finish();

            /* The editor can be entered without going through the select branch above (edit()
             * refused, or an outside opener) — finishing must not NPE on a missing selection. */
            if (this.canModify && this.lastSelected != null && this.lastSelected.category.canModify(form))
            {
                int index = this.lastSelected.category.getForms().indexOf(this.lastSelected.selected);

                if (index >= 0)
                {
                    this.lastSelected.category.replaceForm(index, form);
                }
            }

            this.list.setSelected(form);
            this.accept(form);

            this.lastSelected = null;

            if (this.studio != null)
            {
                this.studio.setSelectedForm(form);
            }
        }

        this.syncViews();
    }

    @Override
    public void accept(Form form)
    {
        if (this.callback != null)
        {
            this.callback.accept(form);
        }
    }

    public void setSelected(Form form)
    {
        this.list.setSelected(form);
        this.list.scrollToSelected();

        if (this.studio != null)
        {
            this.studio.setSelectedForm(form);
        }
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            boolean wasEditing = this.editor.isEditing();

            this.exit();

            if (!this.cantExit)
            {
                return true;
            }

            if (wasEditing)
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public void render(UIContext context)
    {
        if (this.background)
        {
            if (!this.immersive || this.list.isVisible())
            {
                this.area.render(context.batcher, BBSSettings.baseSurface());
            }
        }

        /* Panels are the deep surface, so inputs take the raised (lighter) surface to stand out -
         * same as the film editor. Sections flip this back to false for their own inner inputs. */
        boolean lightInputs = BBSSettings.lightInputs;

        BBSSettings.lightInputs = true;

        try
        {
            super.render(context);
        }
        finally
        {
            BBSSettings.lightInputs = lightInputs;
        }
    }
}