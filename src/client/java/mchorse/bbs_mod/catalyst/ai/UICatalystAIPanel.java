package mchorse.bbs_mod.catalyst.ai;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Catalyst AI: the director's assistant of the film editor. A translucent panel docked at the
 * right of the editor, over the properties and clear of the preview and the timeline, so the
 * result of a command can be watched while the next one is typed.
 *
 * <p>Top to bottom: the title with the ready/working badge and the settings, who answers (the
 * built-in rule engine or a hosted model), the quick director commands, the conversation
 * (requests on the right, Catalyst AI on the left, failures in red), and the undo button with the
 * command line.</p>
 *
 * <p>A hosted model only talks: it answers with a reply and a list of command sentences, and those
 * run through the same {@link CatalystAICommandExecutor} the rule engine and the quick buttons use.
 * The request runs off the game thread; this checks on it once a frame.</p>
 */
public class UICatalystAIPanel extends UIElement
{
    private static final int HEADER_H = 22;
    private static final int PROVIDER_H = 24;
    private static final int BOTTOM_H = 48;
    private static final int HISTORY = 12;

    private static final int BACKGROUND = 0xd8101216;
    private static final int BORDER = 0xff2a2f3a;
    private static final int ACCENT = 0xff9b59b6;

    private enum Kind
    {
        USER, AI, ERROR
    }

    private final UIFilmPanel film;
    private final CatalystAICommandExecutor executor;
    private final List<Runnable> undos = new ArrayList<>();
    private final List<CatalystAIClient.Turn> history = new ArrayList<>();

    public UIIcon settings;
    public UIIcon close;
    public UICirculate provider;
    public UIElement quick;
    public UIScrollView messages;
    public UIButton undo;
    public UITextbox input;
    public UIButton send;

    /** A local command waits one frame before it runs, so the badge gets to say it is working. */
    private String pending;
    private boolean pendingShown;

    /** The hosted model's answer on its way */
    private CompletableFuture<String> request;
    private CatalystAIProvider requestProvider;

    public UICatalystAIPanel(UIFilmPanel film)
    {
        this.film = film;
        this.executor = new CatalystAICommandExecutor(film);

        CatalystAIConfig config = CatalystAIConfig.get();

        this.close = new UIIcon(Icons.CLOSE, (b) -> this.setVisible(false));
        this.close.relative(this).x(1F, -20).y(3).wh(16, 16);

        this.settings = new UIIcon(Icons.GEAR, (b) -> this.openSettings());
        this.settings.tooltip(key("settings.title"));
        this.settings.relative(this).x(1F, -38).y(3).wh(16, 16);

        this.provider = new UICirculate((b) ->
        {
            config.provider = CatalystAIProvider.values()[b.getValue()];
            config.save();
        });

        for (CatalystAIProvider provider : CatalystAIProvider.values())
        {
            this.provider.addLabel(L10n.lang(provider.getLangKey()));
        }

        this.provider.setValue(config.provider.ordinal());
        this.provider.tooltip(key("provider.tooltip"));
        this.provider.relative(this).x(6).y(HEADER_H + 3).w(1F, -12).h(18);

        /* Quick director commands, two to a row */
        this.quick = new UIElement();
        this.quick.relative(this).x(6).y(HEADER_H + PROVIDER_H + 4).w(1F, -12).column(4).vertical().stretch();

        List<CatalystAIActionRegistry.QuickAction> actions = CatalystAIActionRegistry.getActions();

        for (int i = 0; i < actions.size(); i += 2)
        {
            UIElement row = UI.row(4, this.quickButton(actions.get(i)));

            if (i + 1 < actions.size())
            {
                row.add(this.quickButton(actions.get(i + 1)));
            }

            this.quick.add(row);
        }

        int top = HEADER_H + PROVIDER_H + 8 + ((actions.size() + 1) / 2) * 24;

        this.messages = new UIScrollView();
        this.messages.relative(this).x(4).y(top).w(1F, -8).h(1F, -(top + 4 + BOTTOM_H));
        this.messages.column(4).vertical().stretch().scroll().padding(4);

        this.undo = new UIButton(key("undo"), (b) -> this.undoLast());
        this.undo.relative(this).x(6).y(1F, -BOTTOM_H + 2).w(1F, -12).h(18);

        this.input = new UITextbox(1000, null)
        {
            @Override
            public boolean subKeyPressed(UIContext context)
            {
                if (this.isFocused() && (context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER)))
                {
                    UICatalystAIPanel.this.submit();

                    return true;
                }

                return super.subKeyPressed(context);
            }
        };
        this.input.placeholder(key("placeholder"));
        this.input.relative(this).x(6).y(1F, -24).w(1F, -78).h(20);

        this.send = new UIButton(key("send"), (b) -> this.submit());
        this.send.relative(this).x(1F, -68).y(1F, -24).w(62).h(20);

        this.add(this.settings, this.close, this.provider, this.quick, this.messages, this.undo, this.input, this.send);

        this.say(Kind.AI, key("welcome").get());
        this.updateUndo();
    }

    private static IKey key(String id)
    {
        return L10n.lang("bbs.catalyst_ai." + id);
    }

    private UIButton quickButton(CatalystAIActionRegistry.QuickAction action)
    {
        /* Quick commands are already in the engine's words: they run locally, at once, whoever is chosen */
        UIButton button = new UIButton(action.label(), (b) -> this.runLocal(action.command().get()));

        button.tooltip(action.command());
        button.h(20);

        return button;
    }

    private void openSettings()
    {
        UIOverlay.addOverlay(this.getContext(), new UICatalystAISettingsOverlay(null), 340, 0.7F);
    }

    private boolean isBusy()
    {
        return this.pending != null || this.request != null;
    }

    private void submit()
    {
        String text = this.input.textbox.getText().trim();

        if (text.isEmpty() || this.isBusy())
        {
            return;
        }

        this.input.textbox.setText("");

        CatalystAIConfig config = CatalystAIConfig.get();
        CatalystAIProvider provider = config.getEffectiveProvider();

        if (!provider.isOnline())
        {
            if (config.provider.isOnline())
            {
                this.say(Kind.USER, text);
                this.say(Kind.AI, key("no_key").format(L10n.lang(config.provider.getLangKey()).get()).get());
                this.pending = text;
                this.pendingShown = false;

                return;
            }

            this.runLocal(text);

            return;
        }

        this.say(Kind.USER, text);
        this.history.add(new CatalystAIClient.Turn(true, text));

        while (this.history.size() > HISTORY)
        {
            this.history.remove(0);
        }

        this.requestProvider = provider;
        this.request = CatalystAIClient.send(provider, config, CatalystAIPrompt.build(this.film), new ArrayList<>(this.history));
    }

    private void runLocal(String command)
    {
        if (this.isBusy())
        {
            return;
        }

        this.say(Kind.USER, command);
        this.pending = command;
        this.pendingShown = false;
    }

    private CatalystAICommandExecutor.Result execute(String command)
    {
        try
        {
            return this.executor.execute(command);
        }
        catch (Throwable e)
        {
            /* A bad command must never take the editor down */
            return CatalystAICommandExecutor.Result.fail(key("report.error").format(String.valueOf(e.getMessage())).get());
        }
    }

    private void executeLocal(String command)
    {
        CatalystAICommandExecutor.Result result = this.execute(command);

        if (result.undo() != null)
        {
            this.undos.add(result.undo());
            UIUtils.playClick();
        }

        this.say(Kind.AI, result.report());
        this.updateUndo();
    }

    /** The model answered: show the reply, run its commands, and keep them as one undo step. */
    private void finishRequest()
    {
        CompletableFuture<String> request = this.request;
        CatalystAIProvider provider = this.requestProvider;

        this.request = null;
        this.requestProvider = null;

        String text;

        try
        {
            text = request.join();
        }
        catch (CompletionException e)
        {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            String message = cause instanceof CatalystAIClient.CatalystAIException ? cause.getMessage() : cause.getClass().getSimpleName() + ": " + cause.getMessage();

            this.say(Kind.ERROR, key("request_failed").format(L10n.lang(provider.getLangKey()).get(), message).get());

            return;
        }
        catch (Throwable e)
        {
            this.say(Kind.ERROR, key("request_failed").format(L10n.lang(provider.getLangKey()).get(), String.valueOf(e.getMessage())).get());

            return;
        }

        CatalystAIClient.Answer answer = CatalystAIClient.parseAnswer(text);

        this.history.add(new CatalystAIClient.Turn(false, answer.reply().isBlank() ? text : answer.reply()));

        if (!answer.reply().isBlank())
        {
            this.say(Kind.AI, answer.reply());
        }

        List<Runnable> steps = new ArrayList<>();
        StringBuilder reports = new StringBuilder();

        for (String command : answer.commands())
        {
            CatalystAICommandExecutor.Result result = this.execute(command);

            if (result.undo() != null)
            {
                steps.add(result.undo());
            }

            reports.append(reports.length() == 0 ? "" : "\n").append(result.success() ? "+ " : "! ").append(result.report());
        }

        if (!steps.isEmpty())
        {
            /* Taken back newest first, so each restore lands on the state its command started from */
            this.undos.add(() ->
            {
                for (int i = steps.size() - 1; i >= 0; i--)
                {
                    steps.get(i).run();
                }
            });
            UIUtils.playClick();
        }

        if (reports.length() > 0)
        {
            this.say(Kind.AI, reports.toString());
        }

        this.updateUndo();
    }

    private void undoLast()
    {
        if (this.undos.isEmpty())
        {
            return;
        }

        this.undos.remove(this.undos.size() - 1).run();
        this.say(Kind.AI, key("undone").get());
        this.updateUndo();
        UIUtils.playClick();
    }

    private void updateUndo()
    {
        this.undo.setEnabled(!this.undos.isEmpty());
    }

    private void say(Kind kind, String text)
    {
        this.messages.add(new UIBubble(kind, text));
        this.messages.resize();
        this.messages.scroll.scrollTo(this.messages.scroll.scrollSize);
        this.messages.scroll.clamp();
    }

    /* Input: whatever lands on the panel stays on it, so a click on it never reaches the timeline under it */

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        return this.area.isInside(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        return this.area.isInside(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.pending != null)
        {
            if (this.pendingShown)
            {
                String command = this.pending;

                this.pending = null;
                this.executeLocal(command);
            }
            else
            {
                this.pendingShown = true;
            }
        }

        if (this.request != null && this.request.isDone())
        {
            this.finishRequest();
        }

        FontRenderer font = context.batcher.getFont();

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BACKGROUND);
        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), BORDER);
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + HEADER_H, 0xe0181b22);
        context.batcher.box(this.area.x, this.area.y + HEADER_H - 1, this.area.ex(), this.area.y + HEADER_H, ACCENT);

        /* Title and badge */
        context.batcher.icon(Icons.SPARKLES, ACCENT, this.area.x + 6, this.area.y + 3);
        context.batcher.textShadow(key("title").get(), this.area.x + 26, this.area.y + (HEADER_H - font.getHeight()) / 2, Colors.WHITE);

        boolean busy = this.isBusy();
        String badge = key(busy ? "status.busy" : "status.ready").get();
        int bw = font.getWidth(badge) + 10;
        int bx = this.area.ex() - 44 - bw;
        int by = this.area.y + 4;

        context.batcher.box(bx, by, bx + bw, by + HEADER_H - 8, busy ? 0xffc28a1e : 0xff2e8b57);
        context.batcher.text(badge, bx + 5, by + (HEADER_H - 8 - font.getHeight()) / 2 + 1, Colors.WHITE);

        super.render(context);
    }

    /**
     * One message: a request (right, tinted), Catalyst AI (left, neutral) or a failure (left, red),
     * wrapped to most of the width so the two sides read apart at a glance.
     */
    private static class UIBubble extends UIElement
    {
        private static final int PAD = 5;
        private static final int LINE = 11;

        private final Kind kind;
        private final String text;
        private List<String> lines = List.of();

        public UIBubble(Kind kind, String text)
        {
            this.kind = kind;
            this.text = text == null ? "" : text;
            this.h(LINE + PAD * 2 + 10);
        }

        private int bubbleWidth()
        {
            return Math.max(40, (int) (this.area.w * 0.86F));
        }

        @Override
        public void resize()
        {
            FontRenderer font = Batcher2D.getDefaultTextRenderer();

            if (font != null && this.area.w > 0)
            {
                List<String> wrapped = new ArrayList<>();

                for (String paragraph : this.text.split("\n"))
                {
                    wrapped.addAll(paragraph.isEmpty() ? List.of("") : font.wrap(paragraph, this.bubbleWidth() - PAD * 2));
                }

                this.lines = wrapped;

                int h = Math.max(1, this.lines.size()) * LINE + PAD * 2 + 10;

                if (this.flex.h.value != h)
                {
                    this.h(h);
                }
            }

            super.resize();
        }

        @Override
        public void render(UIContext context)
        {
            boolean user = this.kind == Kind.USER;
            int w = this.bubbleWidth();
            int x = user ? this.area.ex() - w : this.area.x;
            int y = this.area.y + 10;
            String who = L10n.lang(user ? "bbs.catalyst_ai.you" : "bbs.catalyst_ai.title").get();
            FontRenderer font = context.batcher.getFont();
            int name = user ? 0xff8ab4f8 : (this.kind == Kind.ERROR ? 0xffff5555 : ACCENT);
            int fill = user ? 0xcc1a2a3a : (this.kind == Kind.ERROR ? 0xcc3a1416 : 0xcc22252c);
            int border = user ? 0xff2b4b70 : (this.kind == Kind.ERROR ? 0xffb03030 : 0xff3a3f4a);

            context.batcher.text(who, user ? this.area.ex() - font.getWidth(who) - 2 : this.area.x + 2, this.area.y, name);
            context.batcher.box(x, y, x + w, this.area.ey(), fill);
            context.batcher.outline(x, y, x + w, this.area.ey(), border);

            for (int i = 0; i < this.lines.size(); i++)
            {
                context.batcher.text(this.lines.get(i), x + PAD, y + PAD + i * LINE, this.kind == Kind.ERROR ? 0xffffd0d0 : Colors.WHITE);
            }

            super.render(context);
        }
    }
}
