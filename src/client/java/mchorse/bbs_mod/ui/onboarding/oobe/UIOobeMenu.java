package mchorse.bbs_mod.ui.onboarding.oobe;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.VehicleRide;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.dashboard.panels.landing.LandingBackdrop;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.input.color.UIColorPicker;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.onboarding.welcome.NleLayouts;
import mchorse.bbs_mod.ui.onboarding.welcome.WelcomeManager;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.InterfaceBlur;
import mchorse.bbs_mod.ui.utils.Label;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.Bufs;
import mchorse.bbs_mod.utils.FFMpegUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * The first-run wizard, drawn the way the Windows 11 out-of-box experience is: one rounded card in
 * the middle of the Central Hub's own backdrop, an illustration on the left and the question on
 * the right, the studio's name fixed above it, and a slide-and-fade from one page to the next.
 *
 * <p>A welcome with the player's face, then seven steps - language, look, editor layout, editor
 * keys, vehicle keys (the two on a keyboard drawn key by key), the video encoder and the end. What
 * is new in the release is shown by the Central Hub once the wizard hands over to it. The pages
 * are drawn straight onto the screen and the places that can be clicked are collected as they are
 * drawn, so a page sliding away is never clickable and nothing is laid out twice.</p>
 *
 * <p>Every colour of a page goes through its alpha, the card's and the page's together, and text
 * that has faded under {@link #MIN_TEXT_ALPHA} is not drawn at all: Minecraft's text renderer takes
 * an almost transparent colour for an opaque one, which is what left words hanging on the screen.</p>
 */
public class UIOobeMenu extends UIBaseMenu
{
    private static final int WELCOME = 0;
    private static final int LANGUAGE = 1;
    private static final int THEME = 2;
    private static final int LAYOUT = 3;
    private static final int EDITOR_KEYS = 4;
    private static final int VEHICLE_KEYS = 5;
    private static final int ENCODER = 6;
    private static final int DONE = 7;
    private static final int PAGES = 8;
    /** The steps the dots count: everything after the welcome */
    private static final int STEPS = PAGES - 1;
    private static final String[] NAMES = {"welcome", "lang", "theme", "layout", "keys", "car", "ffmpeg", "done"};

    private static final float SLIDE_SECONDS = 0.45F;
    private static final float CLOSE_SECONDS = 0.5F;
    /** Below this alpha (of 255) text is not drawn: the text renderer would draw it opaque */
    private static final int MIN_TEXT_ALPHA = 10;
    private static final String DOWNLOAD_LINK = "https://ffmpeg.org/download.html";

    private static final int ON_ACCENT = 0xFFFFFFFF;
    private static final int GOOD = 0xFF6CE08A;
    private static final int WARN = 0xFFFFB347;
    private static final int BAD = 0xFFFF5A5A;
    private static final int[] PALETTE = {0xFF4C9CFF, 0xFF6CE08A, 0xFFFFB347, 0xFFFF7AA8, 0xFFB48CFF, 0xFF4CD7E0, 0xFFFFE066};

    /* The four looks: the surface (secondary) and the accent (primary) */
    private static final int[][] THEMES = {{0x241A12, 0xE08A2A}, {0x12202A, 0x22C7D6}, {0x0B1020, 0x3B6CFF}, {0xF5F5F7, 0x0A84FF}};
    private static final String[] THEME_NAMES = {"nostalgic", "cyan", "midnight", "light"};
    private static final float[] SCALES = {0F, 1F, 1.5F, 2F, 2.5F, 3F, 3.5F, 4F};

    private final Runnable onComplete;
    private final LandingBackdrop backdrop = new LandingBackdrop();
    private final Area screen = new Area();

    private int page;
    private int fromPage = -1;
    private float slide = 1F;
    private int direction = 1;
    private float closing = -1F;
    private boolean finished;
    private boolean gone;
    private long lastNanos;
    private float clock;
    private float dt;

    /* Drawing state: the horizontal slide and the fade of the page in hand, and where it can be clicked */
    private float ox;
    private float alpha = 1F;
    private boolean interactive;
    private final List<Hit> hits = new ArrayList<>();
    private int mouseX;
    private int mouseY;

    /* The card's colours, light or dark after the secondary colour, picked each frame */
    private boolean light;
    private int card;
    private int edge;
    private int option;
    private int optionHover;
    private int ink;
    private int muted;

    /* The player */
    private volatile GameProfile profile;

    /* The language list, scrolling smoothly toward where the wheel sent it */
    private List<Label<String>> languages;
    private float languageScroll;
    private float languageTarget;
    private float listX;
    private float listY;
    private float listW;
    private float listH;

    /* The look: the colour boxes and the sliders */
    private final HexField[] fields = {
        new HexField(6, () -> BBSSettings.primaryColor.get(), (v) -> BBSSettings.primaryColor.set(v)),
        new HexField(6, () -> BBSSettings.secondaryColor.get(), (v) -> BBSSettings.secondaryColor.set(v)),
        new HexField(8, () -> BBSSettings.stencilHighlightColor.get(), (v) -> BBSSettings.stencilHighlightColor.set(v))
    };
    private int focusedField = -1;
    private UIColorPicker picker;
    private final Slider[] sliders = {
        new Slider(0F, 1F, false, () -> BBSSettings.overlayBackgroundOpacity.get(), (v) -> BBSSettings.overlayBackgroundOpacity.set((float) v)),
        new Slider(1F, 30F, true, () -> BBSSettings.interfaceBlurRadius.get(), (v) -> BBSSettings.interfaceBlurRadius.set((int) Math.round(v)))
    };
    private int dragging = -1;

    /* The layout: the user's own, kept to go back to, and the one picked */
    private final EditorLayoutNode original;
    private int layout = NleLayouts.CUSTOM;

    /* The keyboards */
    private final List<Cap> caps = new ArrayList<>();
    private final float[] pressed = new float[GLFW.GLFW_KEY_LAST + 1];
    private final long[] virtualHeld = new long[GLFW.GLFW_KEY_LAST + 1];
    private List<Bind> editorBinds;
    private List<Bind> vehicleBinds;
    private int listening = -1;

    /* The encoder */
    private volatile int encoder;
    private volatile String encoderVersion;

    public UIOobeMenu(Runnable onComplete)
    {
        super();

        this.onComplete = onComplete;
        this.original = BBSSettings.editorLayoutSettings.getLayout(ValueEditorLayout.FILM, EditorLayoutNode::defaultFilmLayout);
        this.buildCaps();
        this.fetchProfile();

        UIElement element = new UIOobeRoot();

        element.full(this.viewport);
        this.main.add(element);
    }

    private static IKey key(String id)
    {
        return L10n.lang("bbs.ui.oobe." + id);
    }

    private static boolean animations()
    {
        return BBSSettings.popupAnimations == null || BBSSettings.popupAnimations.get();
    }

    private static int accent()
    {
        return 0xFF000000 | (BBSSettings.primaryColor.get() & 0xFFFFFF);
    }

    private static int accentHover()
    {
        return 0xFF000000 | blend(BBSSettings.primaryColor.get() & 0xFFFFFF, 0xFFFFFF, 0.15F);
    }

    /** How far a smoothed value goes toward its target this frame, by the studio's scroll smoothing */
    private float smoothing()
    {
        float intensity = BBSSettings.getScrollSmoothingIntensity();

        return intensity <= 0F ? 1F : Math.min(1F, this.dt * 20F / (2.5F * intensity));
    }

    /* The player: the session's profile, then the same with its skin, asked for in the background */

    private void fetchProfile()
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        this.profile = mc.getGameProfile();

        if (this.profile == null || this.profile.getId() == null)
        {
            return;
        }

        Thread thread = new Thread(() ->
        {
            try
            {
                ProfileResult result = mc.getSessionService().fetchProfile(this.profile.getId(), false);

                if (result != null && result.profile() != null)
                {
                    this.profile = result.profile();
                }
            }
            catch (Throwable ignored)
            {}
        }, "BBS profile fetch");

        thread.setDaemon(true);
        thread.start();
    }

    private String playerName()
    {
        GameProfile profile = this.profile;

        return profile == null || profile.getName() == null ? MinecraftClient.getInstance().getSession().getUsername() : profile.getName();
    }

    /* The look */

    private static class HexField
    {
        public final int digits;
        public final IntSupplier get;
        public final IntConsumer set;
        public String text = "";

        public HexField(int digits, IntSupplier get, IntConsumer set)
        {
            this.digits = digits;
            this.get = get;
            this.set = set;
        }

        public void read()
        {
            int value = this.get.getAsInt();

            this.text = "#" + (this.digits == 8 ? String.format("%08x", value) : String.format("%06x", value & 0xFFFFFF));
        }

        /** Once the box reads as its whole number of digits, that is the colour */
        public void apply()
        {
            String digits = this.text.startsWith("#") ? this.text.substring(1) : this.text;

            if (digits.length() == this.digits)
            {
                try
                {
                    this.set.accept((int) Long.parseLong(digits, 16));
                }
                catch (NumberFormatException ignored)
                {}
            }
        }
    }

    private static class Slider
    {
        public final float min;
        public final float max;
        public final boolean integer;
        public final DoubleSupplier get;
        public final DoubleConsumer set;
        public float trackX;
        public float trackW;
        public float shown = Float.NaN;

        public Slider(float min, float max, boolean integer, DoubleSupplier get, DoubleConsumer set)
        {
            this.min = min;
            this.max = max;
            this.integer = integer;
            this.get = get;
            this.set = set;
        }

        public float fraction()
        {
            return (float) ((this.get.getAsDouble() - this.min) / (this.max - this.min));
        }

        public void drag(int mouseX)
        {
            float t = this.trackW <= 0F ? 0F : Math.max(0F, Math.min(1F, (mouseX - this.trackX) / this.trackW));
            double value = this.min + (this.max - this.min) * t;

            this.set.accept(this.integer ? Math.round(value) : Math.round(value * 100D) / 100D);
        }
    }

    private void focusField(int index)
    {
        this.focusedField = index;

        if (index >= 0)
        {
            this.fields[index].text = "#";
        }
    }

    /** The studio's own colour picker over a colour box: the box and the setting follow it as it is dragged */
    private void openPicker(int index, float x, float y)
    {
        HexField field = this.fields[index];
        UIColorPicker picker = new UIColorPicker((color) ->
        {
            field.set.accept(color);
            field.read();
        });

        this.closePicker();
        this.focusedField = -1;

        if (field.digits == 8)
        {
            picker.editAlpha();
        }

        picker.setColor(field.get.getAsInt());
        this.overlay.add(picker);
        picker.setup(Math.round(x), Math.round(y));
        picker.bounds(this.main, 2);
        picker.resize();
        this.picker = picker;
    }

    private void closePicker()
    {
        if (this.picker != null)
        {
            this.picker.removeFromParent();
            this.picker = null;
        }
    }

    private void applyTheme(int index)
    {
        BBSSettings.secondaryColor.set(THEMES[index][0]);
        BBSSettings.primaryColor.set(THEMES[index][1]);
    }

    private void stepScale(int by)
    {
        float current = BBSSettings.userIntefaceScale.get();
        int at = 0;

        for (int i = 0; i < SCALES.length; i++)
        {
            if (Math.abs(SCALES[i] - current) < Math.abs(SCALES[at] - current))
            {
                at = i;
            }
        }

        BBSSettings.userIntefaceScale.set(SCALES[Math.max(0, Math.min(SCALES.length - 1, at + by))]);
    }

    /* The keyboard */

    private static class Cap
    {
        public final int code;
        public final String label;
        public final float x;
        public final float y;
        public final float w;

        public Cap(int code, String label, float x, float y, float w)
        {
            this.code = code;
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
        }
    }

    private void buildCaps()
    {
        Object[][][] rows = {
            {{GLFW.GLFW_KEY_GRAVE_ACCENT, "`", 1F}, {GLFW.GLFW_KEY_1, "1", 1F}, {GLFW.GLFW_KEY_2, "2", 1F}, {GLFW.GLFW_KEY_3, "3", 1F}, {GLFW.GLFW_KEY_4, "4", 1F}, {GLFW.GLFW_KEY_5, "5", 1F},
                {GLFW.GLFW_KEY_6, "6", 1F}, {GLFW.GLFW_KEY_7, "7", 1F}, {GLFW.GLFW_KEY_8, "8", 1F}, {GLFW.GLFW_KEY_9, "9", 1F}, {GLFW.GLFW_KEY_0, "0", 1F}, {GLFW.GLFW_KEY_MINUS, "-", 1F},
                {GLFW.GLFW_KEY_EQUAL, "=", 1F}, {GLFW.GLFW_KEY_BACKSPACE, "Bksp", 2F}},
            {{GLFW.GLFW_KEY_TAB, "Tab", 1.5F}, {GLFW.GLFW_KEY_Q, "Q", 1F}, {GLFW.GLFW_KEY_W, "W", 1F}, {GLFW.GLFW_KEY_E, "E", 1F}, {GLFW.GLFW_KEY_R, "R", 1F}, {GLFW.GLFW_KEY_T, "T", 1F},
                {GLFW.GLFW_KEY_Y, "Y", 1F}, {GLFW.GLFW_KEY_U, "U", 1F}, {GLFW.GLFW_KEY_I, "I", 1F}, {GLFW.GLFW_KEY_O, "O", 1F}, {GLFW.GLFW_KEY_P, "P", 1F}, {GLFW.GLFW_KEY_LEFT_BRACKET, "[", 1F},
                {GLFW.GLFW_KEY_RIGHT_BRACKET, "]", 1F}, {GLFW.GLFW_KEY_BACKSLASH, "\\", 1.5F}},
            {{GLFW.GLFW_KEY_CAPS_LOCK, "Caps", 1.75F}, {GLFW.GLFW_KEY_A, "A", 1F}, {GLFW.GLFW_KEY_S, "S", 1F}, {GLFW.GLFW_KEY_D, "D", 1F}, {GLFW.GLFW_KEY_F, "F", 1F}, {GLFW.GLFW_KEY_G, "G", 1F},
                {GLFW.GLFW_KEY_H, "H", 1F}, {GLFW.GLFW_KEY_J, "J", 1F}, {GLFW.GLFW_KEY_K, "K", 1F}, {GLFW.GLFW_KEY_L, "L", 1F}, {GLFW.GLFW_KEY_SEMICOLON, ";", 1F}, {GLFW.GLFW_KEY_APOSTROPHE, "'", 1F},
                {GLFW.GLFW_KEY_ENTER, "Enter", 2.25F}},
            {{GLFW.GLFW_KEY_LEFT_SHIFT, "Shift", 1.75F}, {GLFW.GLFW_KEY_Z, "Z", 1F}, {GLFW.GLFW_KEY_X, "X", 1F}, {GLFW.GLFW_KEY_C, "C", 1F}, {GLFW.GLFW_KEY_V, "V", 1F}, {GLFW.GLFW_KEY_B, "B", 1F},
                {GLFW.GLFW_KEY_N, "N", 1F}, {GLFW.GLFW_KEY_M, "M", 1F}, {GLFW.GLFW_KEY_COMMA, ",", 1F}, {GLFW.GLFW_KEY_PERIOD, ".", 1F}, {GLFW.GLFW_KEY_SLASH, "/", 1F},
                {GLFW.GLFW_KEY_RIGHT_SHIFT, "Shift", 1.25F}, {GLFW.GLFW_KEY_UP, "↑", 1F}},
            {{GLFW.GLFW_KEY_LEFT_CONTROL, "Ctrl", 1.25F}, {GLFW.GLFW_KEY_LEFT_ALT, "Alt", 1.25F}, {GLFW.GLFW_KEY_SPACE, "", 7F}, {GLFW.GLFW_KEY_RIGHT_ALT, "Alt", 1.25F},
                {GLFW.GLFW_KEY_RIGHT_CONTROL, "Ctrl", 1.25F}, {GLFW.GLFW_KEY_LEFT, "←", 1F}, {GLFW.GLFW_KEY_DOWN, "↓", 1F}, {GLFW.GLFW_KEY_RIGHT, "→", 1F}}
        };

        for (int row = 0; row < rows.length; row++)
        {
            float x = 0F;

            for (Object[] cap : rows[row])
            {
                this.caps.add(new Cap((Integer) cap[0], (String) cap[1], x, row, (Float) cap[2]));

                x += (Float) cap[2];
            }
        }
    }

    /* What a key is for */

    private interface Bind
    {
        IKey name();

        int code();

        /** The whole combination, as it reads */
        String combo();

        void assign(int code);

        int color();

        /** The keys the combination holds beside its main one (shift, control), lit more softly */
        List<Integer> modifiers();
    }

    private static class ComboBind implements Bind
    {
        private final IKey name;
        private final KeyCombo combo;
        private final int color;

        public ComboBind(IKey name, KeyCombo combo, int color)
        {
            this.name = name;
            this.combo = combo;
            this.color = color;
        }

        @Override
        public IKey name()
        {
            return this.name;
        }

        @Override
        public int code()
        {
            return this.combo.getMainKey();
        }

        @Override
        public String combo()
        {
            return this.combo.getKeyCombo();
        }

        @Override
        public void assign(int code)
        {
            if (this.combo.keys.isEmpty())
            {
                this.combo.keys.add(code);
            }
            else
            {
                this.combo.keys.set(0, code);
            }
        }

        @Override
        public int color()
        {
            return this.color;
        }

        @Override
        public List<Integer> modifiers()
        {
            return this.combo.keys.size() > 1 ? new ArrayList<>(this.combo.keys.subList(1, this.combo.keys.size())) : List.of();
        }
    }

    private static class GameBind implements Bind
    {
        private final IKey name;
        private final KeyBinding binding;
        private final int color;

        public GameBind(IKey name, KeyBinding binding, int color)
        {
            this.name = name;
            this.binding = binding;
            this.color = color;
        }

        @Override
        public IKey name()
        {
            return this.name;
        }

        @Override
        public int code()
        {
            InputUtil.Key bound = InputUtil.fromTranslationKey(this.binding.getBoundKeyTranslationKey());

            return bound.getCategory() == InputUtil.Type.KEYSYM ? bound.getCode() : -1;
        }

        @Override
        public String combo()
        {
            return this.binding.getBoundKeyLocalizedText().getString();
        }

        @Override
        public void assign(int code)
        {
            this.binding.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(code));
            KeyBinding.updateKeysByCode();
            MinecraftClient.getInstance().options.write();
        }

        @Override
        public int color()
        {
            return this.color;
        }

        @Override
        public List<Integer> modifiers()
        {
            return List.of();
        }
    }

    private List<Bind> binds(int page)
    {
        if (page == EDITOR_KEYS)
        {
            if (this.editorBinds == null)
            {
                this.editorBinds = new ArrayList<>();
                this.editorBinds.add(new ComboBind(key("keys.play"), Keys.PLAUSE, PALETTE[0]));
                this.editorBinds.add(new ComboBind(key("keys.camera"), Keys.FILM_CONTROLLER_TOGGLE_ORBIT_MODE, PALETTE[1]));
                this.editorBinds.add(new ComboBind(key("keys.jump"), Keys.JUMP_FORWARD, PALETTE[2]));
                this.editorBinds.add(new ComboBind(key("keys.keyframe"), Keys.FILM_CONTROLLER_INSERT_FRAME, PALETTE[3]));
            }

            return this.editorBinds;
        }

        if (this.vehicleBinds == null)
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            this.vehicleBinds = new ArrayList<>();
            this.vehicleBinds.add(new GameBind(key("car.throttle"), mc.options.forwardKey, PALETTE[0]));
            this.vehicleBinds.add(new GameBind(key("car.brake"), mc.options.backKey, PALETTE[1]));
            this.vehicleBinds.add(new GameBind(key("car.left"), mc.options.leftKey, PALETTE[2]));
            this.vehicleBinds.add(new GameBind(key("car.right"), mc.options.rightKey, PALETTE[3]));
            this.vehicleBinds.add(new GameBind(key("car.handbrake"), mc.options.jumpKey, PALETTE[4]));

            if (VehicleRide.doorKey != null)
            {
                this.vehicleBinds.add(new GameBind(key("car.door"), VehicleRide.doorKey, PALETTE[5]));
            }

            this.vehicleBinds.add(new GameBind(key("car.exit"), mc.options.sneakKey, PALETTE[6]));
        }

        return this.vehicleBinds;
    }

    /** Whether another key of the list stands where this one does: the same key, with the same keys held beside it */
    private boolean conflicts(List<Bind> list, Bind bind)
    {
        for (Bind other : list)
        {
            if (other != bind && other.code() == bind.code() && other.code() > 0 && other.modifiers().equals(bind.modifiers()))
            {
                return true;
            }
        }

        return false;
    }

    private void assign(int code)
    {
        List<Bind> list = this.binds(this.page);

        if (this.listening < 0 || this.listening >= list.size())
        {
            return;
        }

        list.get(this.listening).assign(code);
        this.listening = -1;

        Settings keybinds = BBSMod.getSettings().modules.get("keybinds");

        if (keybinds != null)
        {
            keybinds.save();
        }
    }

    /* The flow */

    private void go(int target)
    {
        if (target < 0 || target >= PAGES || target == this.page || this.slide < 1F || this.closing >= 0F)
        {
            return;
        }

        this.listening = -1;
        this.focusedField = -1;
        this.dragging = -1;
        this.closePicker();
        this.fromPage = this.page;
        this.direction = target > this.page ? 1 : -1;
        this.page = target;
        this.slide = animations() ? 0F : 1F;

        if (this.slide >= 1F)
        {
            this.fromPage = -1;
        }

        if (target == ENCODER && this.encoder == 0)
        {
            this.checkEncoder();
        }
    }

    private void next()
    {
        if (this.page == DONE)
        {
            this.finish();
        }
        else
        {
            this.go(this.page + 1);
        }
    }

    private void checkEncoder()
    {
        this.encoder = 1;

        Thread thread = new Thread(() ->
        {
            Optional<String> version = FFMpegUtils.version();

            this.encoderVersion = version.orElse(null);
            this.encoder = version.isPresent() ? 2 : 3;
        }, "BBS ffmpeg check");

        thread.setDaemon(true);
        thread.start();
    }

    /** One of the seven layouts into the film editor's setting (the last puts back what the user had) */
    private void chooseLayout(int index)
    {
        this.layout = index;

        BBSSettings.editorLayoutSettings.setLayout(ValueEditorLayout.FILM, NleLayouts.tree(index, this.original));
    }

    /** Written down that it was walked, then the card melts away and the studio comes up */
    private void finish()
    {
        if (this.finished)
        {
            return;
        }

        this.finished = true;
        this.closePicker();
        this.closing = animations() ? 0F : 1F;
        WelcomeManager.setSeenWelcomeScreen(true);
    }

    @Override
    protected void closeMenu()
    {
        /* Escape does not leave it: the way out is the skip link or the end */
    }

    /* Input and drawing, on one element covering the screen */

    private class UIOobeRoot extends UIElement
    {
        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            UIOobeMenu menu = UIOobeMenu.this;

            if (menu.closing >= 0F)
            {
                return true;
            }

            menu.focusedField = -1;

            for (int i = menu.hits.size() - 1; i >= 0; i--)
            {
                Hit hit = menu.hits.get(i);

                if (context.mouseButton == 0 && hit.contains(context.mouseX, context.mouseY))
                {
                    UIUtils.playClick();
                    hit.action.run();

                    return true;
                }
            }

            return true;
        }

        @Override
        protected boolean subMouseReleased(UIContext context)
        {
            UIOobeMenu.this.dragging = -1;

            return true;
        }

        @Override
        protected boolean subMouseScrolled(UIContext context)
        {
            UIOobeMenu menu = UIOobeMenu.this;

            if (menu.page == LANGUAGE && context.mouseX >= menu.listX && context.mouseX < menu.listX + menu.listW && context.mouseY >= menu.listY && context.mouseY < menu.listY + menu.listH)
            {
                menu.languageTarget -= (float) context.mouseWheel * 27F;
            }

            return true;
        }

        @Override
        protected boolean subKeyPressed(UIContext context)
        {
            return UIOobeMenu.this.keyPressed(context);
        }

        @Override
        protected boolean subTextInput(UIContext context)
        {
            UIOobeMenu menu = UIOobeMenu.this;
            char character = context.getInputCharacter();

            if (menu.focusedField >= 0)
            {
                HexField field = menu.fields[menu.focusedField];

                if (field.text.length() < field.digits + 1 && (Character.digit(character, 16) >= 0 || (character == '#' && field.text.isEmpty())))
                {
                    field.text += Character.toLowerCase(character);
                    field.apply();
                }
            }

            return true;
        }

        @Override
        public void render(UIContext context)
        {
            UIOobeMenu.this.draw(context, this.area);
        }
    }

    private static class Hit
    {
        public final float x;
        public final float y;
        public final float w;
        public final float h;
        public final Runnable action;

        public Hit(float x, float y, float w, float h, Runnable action)
        {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.action = action;
        }

        public boolean contains(int mx, int my)
        {
            return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
        }
    }

    private boolean keyPressed(UIContext context)
    {
        if (context.getKeyAction() != KeyAction.PRESSED)
        {
            return true;
        }

        int code = context.getKeyCode();
        boolean keyboard = this.page == EDITOR_KEYS || this.page == VEHICLE_KEYS;

        if (this.focusedField >= 0)
        {
            HexField field = this.fields[this.focusedField];

            if (code == GLFW.GLFW_KEY_BACKSPACE && !field.text.isEmpty())
            {
                field.text = field.text.substring(0, field.text.length() - 1);
            }
            else if (code == GLFW.GLFW_KEY_ENTER || code == GLFW.GLFW_KEY_ESCAPE || code == GLFW.GLFW_KEY_TAB)
            {
                field.apply();
                this.focusField(code == GLFW.GLFW_KEY_TAB ? (this.focusedField + 1) % this.fields.length : -1);
            }

            return true;
        }

        if (keyboard && this.listening >= 0)
        {
            if (code == GLFW.GLFW_KEY_ESCAPE)
            {
                this.listening = -1;
            }
            else
            {
                this.assign(code);
            }

            return true;
        }

        if (code == GLFW.GLFW_KEY_ENTER && !keyboard)
        {
            this.next();
        }

        return true;
    }

    /* Drawing helpers: every one adds the slide and fades by the page's own alpha */

    private int fade(int color)
    {
        int a = Math.round(((color >>> 24) & 0xFF) * this.alpha);

        return (a << 24) | (color & 0xFFFFFF);
    }

    private static float ease(float t)
    {
        float inverse = 1F - t;

        return 1F - inverse * inverse * inverse;
    }

    private static int blend(int a, int b, float t)
    {
        int r = Math.round(((a >> 16) & 0xFF) * (1F - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1F - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1F - t) + (b & 0xFF) * t);

        return (r << 16) | (g << 8) | bl;
    }

    private static float luminance(int rgb)
    {
        return (0.2126F * ((rgb >> 16) & 0xFF) + 0.7152F * ((rgb >> 8) & 0xFF) + 0.0722F * (rgb & 0xFF)) / 255F;
    }

    private void rect(UIContext c, float x, float y, float w, float h, int color)
    {
        int fill = this.fade(color);

        if ((fill >>> 24) == 0)
        {
            return;
        }

        c.batcher.box(Math.round(x + this.ox), Math.round(y), Math.round(x + this.ox + w), Math.round(y + h), fill);
    }

    private void round(UIContext c, float x, float y, float w, float h, int radius, int color)
    {
        int fill = this.fade(color);

        if ((fill >>> 24) == 0 || w <= 0F || h <= 0F)
        {
            return;
        }

        int r = Math.max(0, Math.min(radius, (int) Math.min(w, h) / 2));
        float left = x + this.ox;

        for (int i = 0; i < r; i++)
        {
            float dy = r - i - 0.5F;
            float inset = r - (float) Math.sqrt(r * r - dy * dy);

            c.batcher.box(Math.round(left + inset), Math.round(y + i), Math.round(left + w - inset), Math.round(y + i + 1), fill);
            c.batcher.box(Math.round(left + inset), Math.round(y + h - i - 1), Math.round(left + w - inset), Math.round(y + h - i), fill);
        }

        c.batcher.box(Math.round(left), Math.round(y + r), Math.round(left + w), Math.round(y + h - r), fill);
    }

    /** A raised control: the shadow under it and the light line on it, as the studio's settings want them */
    private void raised(UIContext c, float x, float y, float w, float h, int radius, int color)
    {
        if (BBSSettings.interfaceShadows.get())
        {
            this.round(c, x, y + 2F, w, h, radius, this.light ? 0x30000000 : 0x60000000);
        }

        this.round(c, x, y, w, h, radius, color);

        if (BBSSettings.interfaceHighlights.get())
        {
            this.rect(c, x + radius, y, w - radius * 2, 1F, this.light ? 0xA0FFFFFF : 0x40FFFFFF);
        }
    }

    private void text(UIContext c, String text, float x, float y, int color, float scale)
    {
        int faded = this.fade(color);

        /* Almost gone is gone: the text renderer draws a nearly transparent colour opaque */
        if ((faded >>> 24) < MIN_TEXT_ALPHA || text.isEmpty())
        {
            return;
        }

        if (scale == 1F)
        {
            c.batcher.text(text, Math.round(x + this.ox), Math.round(y), faded, false);

            return;
        }

        MatrixStack stack = c.batcher.getContext().getMatrices();

        stack.push();
        stack.translate(x + this.ox, y, 0F);
        stack.scale(scale, scale, 1F);
        c.batcher.text(text, 0, 0, faded, false);
        stack.pop();
    }

    private float width(UIContext c, String text, float scale)
    {
        return c.batcher.getFont().getWidth(text) * scale;
    }

    /** Wrapped text; the height it took (the same whether it was drawn or had faded out) */
    private int paragraph(UIContext c, String text, float x, float y, float w, int color)
    {
        int faded = this.fade(color);

        if ((faded >>> 24) < MIN_TEXT_ALPHA)
        {
            return c.batcher.getFont().wrap(text, Math.round(w)).size() * 11;
        }

        return c.batcher.wallText(text, Math.round(x + this.ox), Math.round(y), faded, Math.round(w), 11);
    }

    /** Wrapped text, every line centred on mx; the height it took */
    private int centred(UIContext c, String text, float mx, float y, float w, int color)
    {
        List<String> lines = c.batcher.getFont().wrap(text, Math.round(w));

        for (int i = 0; i < lines.size(); i++)
        {
            this.text(c, lines.get(i), mx - this.width(c, lines.get(i), 1F) / 2F, y + i * 11F, color, 1F);
        }

        return lines.size() * 11;
    }

    private boolean hover(float x, float y, float w, float h)
    {
        return this.interactive && this.mouseX >= x + this.ox && this.mouseX < x + this.ox + w && this.mouseY >= y && this.mouseY < y + h;
    }

    private void hit(float x, float y, float w, float h, Runnable action)
    {
        if (this.interactive)
        {
            this.hits.add(new Hit(x + this.ox, y, w, h, action));
        }
    }

    private void button(UIContext c, String label, float x, float y, float w, float h, boolean primary, Runnable action)
    {
        boolean hover = this.hover(x, y, w, h);
        int fill = primary ? (hover ? accentHover() : accent()) : (hover ? this.optionHover : this.option);
        FontRenderer font = c.batcher.getFont();

        this.raised(c, x, y, w, h, 4, fill);
        this.text(c, label, x + w / 2F - font.getWidth(label) / 2F, y + h / 2F - font.getHeight() / 2F, primary ? ON_ACCENT : this.ink, 1F);
        this.hit(x, y, w, h, action);
    }

    /** A switch with its label: on in the accent, off in grey, flipping the setting it stands for */
    private void toggle(UIContext c, float x, float y, float w, String label, ValueBoolean value)
    {
        boolean on = value.get();
        boolean hover = this.hover(x, y, w, 18F);

        this.raised(c, x, y, 30F, 16F, 8, on ? (hover ? accentHover() : accent()) : (hover ? 0xFF8A8A96 : 0xFF6A6A76));
        this.round(c, on ? x + 16F : x + 2F, y + 2F, 12F, 12F, 6, 0xFFFFFFFF);
        this.text(c, c.batcher.getFont().limitToWidth(label, (int) w - 40), x + 38F, y + 4F, this.ink, 1F);
        this.hit(x, y, w, 18F, () -> value.set(!value.get()));
    }

    /** A slider with its label and value; the knob eases toward the value at the studio's scroll smoothing */
    private void slider(UIContext c, int index, float x, float y, float w, String label)
    {
        Slider slider = this.sliders[index];
        float target = Math.max(0F, Math.min(1F, slider.fraction()));
        double value = slider.get.getAsDouble();
        String shown = slider.integer ? String.valueOf((int) Math.round(value)) : String.format("%.2f", value);

        slider.shown = Float.isNaN(slider.shown) || this.dragging == index ? target : slider.shown + (target - slider.shown) * this.smoothing();

        this.text(c, c.batcher.getFont().limitToWidth(label, (int) (w - this.width(c, shown, 1F) - 8)), x, y, this.ink, 1F);
        this.text(c, shown, x + w - this.width(c, shown, 1F), y, this.muted, 1F);

        float ty = y + 16F;
        float knob = x + slider.shown * w;

        this.round(c, x, ty, w, 4F, 2, this.light ? 0xFFC7C7CC : 0xFF4A4A5A);
        this.round(c, x, ty, knob - x, 4F, 2, accent());
        this.raised(c, knob - 6F, ty - 4F, 12F, 12F, 6, this.dragging == index || this.hover(knob - 6F, ty - 4F, 12F, 12F) ? 0xFFFFFFFF : 0xFFE8E8EE);

        if (this.interactive)
        {
            slider.trackX = x + this.ox;
            slider.trackW = w;
        }

        this.hit(x - 6F, ty - 6F, w + 12F, 16F, () ->
        {
            this.dragging = index;
            slider.drag(this.mouseX);
        });
    }

    /** A soft round light: its colour in the middle, nothing at its edge */
    private void glow(UIContext c, float x, float y, float r, int color)
    {
        int middle = this.fade(color);

        if ((middle >>> 24) == 0)
        {
            return;
        }

        c.batcher.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Matrix4f matrix = c.batcher.getContext().getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        int edge = middle & 0xFFFFFF;
        int segments = 40;

        builder.vertex(matrix, x + this.ox, y, 0F).color(middle)/*$ nx*/.next()/**/;

        for (int i = 0; i <= segments; i++)
        {
            double a = Math.PI * 2D * i / segments;

            builder.vertex(matrix, (float) (x + this.ox + Math.cos(a) * r), (float) (y + Math.sin(a) * r), 0F).color(edge)/*$ nx*/.next()/**/;
        }

        Bufs.draw(builder);
    }

    /**
     * A soft light (or shadow) around a rounded rectangle: a band from its outline, in the colour,
     * out to the same outline grown by the spread, transparent. The corners are quarter circles of
     * the rectangle's own radius plus the spread, so the band hugs the corners instead of squaring them.
     */
    private void halo(UIContext c, float x, float y, float w, float h, float radius, float spread, int color)
    {
        if ((color >>> 24) == 0 || w <= 0F || h <= 0F)
        {
            return;
        }

        c.batcher.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Matrix4f matrix = c.batcher.getContext().getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Bufs.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        float r = Math.min(radius, Math.min(w, h) / 2F);
        float left = x + this.ox;
        float[][] corners = {{left + w - r, y + r}, {left + w - r, y + h - r}, {left + r, y + h - r}, {left + r, y + r}};
        int edge = color & 0xFFFFFF;
        int segments = 10;

        for (int k = 0; k <= 4; k++)
        {
            float[] corner = corners[k % 4];
            int steps = k == 4 ? 0 : segments;

            for (int s = 0; s <= steps; s++)
            {
                double angle = Math.toRadians(-90D + 90D * k + 90D * s / segments);
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);

                builder.vertex(matrix, corner[0] + cos * r, corner[1] + sin * r, 0F).color(color)/*$ nx*/.next()/**/;
                builder.vertex(matrix, corner[0] + cos * (r + spread), corner[1] + sin * (r + spread), 0F).color(edge)/*$ nx*/.next()/**/;
            }
        }

        Bufs.draw(builder);
    }

    /* The frame */

    private void palette()
    {
        int secondary = BBSSettings.secondaryColor.get() & 0xFFFFFF;

        this.light = luminance(secondary) > 0.6F;

        if (this.light)
        {
            this.card = 0xF5F5F5F7;
            this.edge = 0xFFD1D1D6;
            this.option = 0xFFE5E5EA;
            this.optionHover = 0xFFD8D8DE;
            this.ink = 0xFF1C1C1E;
            this.muted = 0xFF6E6E73;
        }
        else
        {
            this.card = 0xF0201F2A;
            this.edge = 0xFF3A3850;
            this.option = 0xFF2E2D3A;
            this.optionHover = 0xFF3A3948;
            this.ink = 0xFFFFFFFF;
            this.muted = 0xFFB4B4C4;
        }
    }

    private void draw(UIContext c, Area area)
    {
        if (this.gone)
        {
            return;
        }

        long now = System.nanoTime();

        this.dt = this.lastNanos == 0L ? 0F : Math.min((now - this.lastNanos) / 1.0E9F, 0.1F);
        this.lastNanos = now;
        this.clock += this.dt;
        this.mouseX = c.mouseX;
        this.mouseY = c.mouseY;
        this.hits.clear();
        this.palette();

        if (this.slide < 1F)
        {
            this.slide = Math.min(1F, this.slide + this.dt / SLIDE_SECONDS);
        }

        /* The slide is over: the old page is gone that very frame, not one later */
        if (this.slide >= 1F)
        {
            this.fromPage = -1;
        }

        /* A slider follows the cursor while it is held */
        if (this.dragging >= 0)
        {
            this.sliders[this.dragging].drag(this.mouseX);
        }

        float closeFade = 1F;

        if (this.closing >= 0F)
        {
            this.closing += this.dt / CLOSE_SECONDS;
            closeFade = 1F - ease(Math.min(this.closing, 1F));

            if (this.closing >= 1F)
            {
                /* Nothing of it is drawn again, whatever the screen does next */
                this.gone = true;
                this.hits.clear();

                if (this.onComplete != null)
                {
                    this.onComplete.run();
                }
                else
                {
                    MinecraftClient.getInstance().setScreen(null);
                }

                return;
            }
        }

        this.ox = 0F;
        this.alpha = 1F;
        this.interactive = false;

        /* The Central Hub's own ground: lights in the accent that drift and answer the cursor */
        this.screen.copy(area);
        this.backdrop.render(c, this.screen);

        /* Then the glass and the dimming the panels get, so the settings show here as they are set */
        c.batcher.flush();
        InterfaceBlur.apply();
        c.batcher.beginBatch();
        this.rect(c, area.x, area.y, area.w, area.h, BBSSettings.overlayBackground());

        /* The card: a good deal of the screen, never more than it needs */
        float cw = Math.min(780F, area.w - 40F);
        float ch = Math.min(440F, area.h - 130F);
        float cx = area.x + (area.w - cw) / 2F;
        float cy = area.y + (area.h - ch) / 2F + 28F;

        this.alpha = closeFade;
        this.logo(c, area.x, area.w, cy);

        if (BBSSettings.interfaceShadows.get())
        {
            this.halo(c, cx, cy + 6F, cw, ch, 9F, 18F, this.fade(this.light ? 0x30000000 : 0x70000000));
        }

        if (BBSSettings.hasInterfaceGlow())
        {
            this.halo(c, cx - 1F, cy - 1F, cw + 2F, ch + 2F, 9F, 16F, this.fade(0x58000000 | (BBSSettings.primaryColor.get() & 0xFFFFFF)));
        }

        this.round(c, cx - 1, cy - 1, cw + 2, ch + 2, 9, this.edge);
        this.round(c, cx, cy, cw, ch, 8, this.card);

        float travel = cw * 0.35F;

        c.batcher.clip(Math.round(cx + 2F), Math.round(cy + 2F), Math.round(cw - 4F), Math.round(ch - 4F), c);

        if (this.fromPage >= 0)
        {
            float t = ease(this.slide);

            /* The old page slides off and fades, the new one comes in from the other side and settles */
            this.ox = -travel * t * this.direction;
            this.alpha = closeFade * (1F - t);
            this.page(c, this.fromPage, cx, cy, cw, ch);
            this.ox = travel * (1F - t) * this.direction;
            this.alpha = closeFade * t;
            this.page(c, this.page, cx, cy, cw, ch);
        }
        else
        {
            this.ox = 0F;
            this.alpha = closeFade;
            this.interactive = this.closing < 0F;
            this.page(c, this.page, cx, cy, cw, ch);
        }

        c.batcher.unclip(c);

        this.ox = 0F;
        this.alpha = closeFade;
        this.interactive = this.closing < 0F && this.fromPage < 0;

        if (this.page > WELCOME)
        {
            this.dots(c, cx, cy, cw, ch);
        }

        this.skip(c, cx, cy, cw);

        this.interactive = false;
        c.batcher.endBatch();

        this.updateKeys(this.dt);
    }

    private void logo(UIContext c, int vx, int vw, float cardY)
    {
        String name = "BBS Catalyst Studio";
        float scale = 2F;

        this.text(c, name, vx + vw / 2F - this.width(c, name, scale) / 2F, cardY - 38F, this.light ? 0xFF1C1C1E : 0xFFFFFFFF, scale);
    }

    private void dots(UIContext c, float cx, float cy, float cw, float ch)
    {
        float total = STEPS * 6F + (STEPS - 1) * 6F + 8F;
        float x = cx + cw / 2F - total / 2F;

        for (int i = 0; i < STEPS; i++)
        {
            boolean current = i == this.page - 1;

            this.rect(c, x, cy + ch + 14F, current ? 14F : 6F, 6F, this.light ? (current ? 0xFF1C1C1E : 0x50000000) : (current ? 0xFFFFFFFF : 0x66FFFFFF));
            x += (current ? 14F : 6F) + 6F;
        }
    }

    private void skip(UIContext c, float cx, float cy, float cw)
    {
        String label = key("skip").get();
        float w = this.width(c, label, 1F);

        this.text(c, label, cx + cw - w - 4F, cy - 12F, this.hover(cx + cw - w - 8F, cy - 14F, w + 8F, 14F) ? (this.light ? 0xFF000000 : 0xFFFFFFFF) : this.muted, 1F);
        this.hit(cx + cw - w - 8F, cy - 14F, w + 8F, 14F, this::finish);
    }

    /* A page */

    private void page(UIContext c, int page, float cx, float cy, float cw, float ch)
    {
        if (page == WELCOME)
        {
            this.welcomePage(c, cx, cy, cw, ch);

            return;
        }

        float pad = 22F;
        boolean wide = page == EDITOR_KEYS || page == VEHICLE_KEYS || page == LAYOUT || page == THEME;
        float leftW = wide ? 0F : cw * 0.34F;
        float rx = wide ? cx + pad : cx + pad + leftW + 14F;
        float rw = cx + cw - pad - rx;

        if (!wide)
        {
            this.illustration(c, page, cx + pad, cy + pad, leftW, ch - pad * 2F - 30F);
        }

        this.text(c, key(NAMES[page] + ".title").get(), rx, cy + pad, this.ink, 1.6F);

        int used = this.paragraph(c, key(NAMES[page] + ".subtitle").get(), rx, cy + pad + 24F, rw, this.muted);
        float top = cy + pad + 24F + Math.max(used, 12) + 12F;
        float bottom = cy + ch - pad - 34F;

        switch (page)
        {
            case LANGUAGE -> this.languagePage(c, rx, top, rw, bottom - top);
            case THEME -> this.themePage(c, rx, top, rw);
            case LAYOUT -> this.layoutPage(c, rx, top, rw, bottom - top);
            case EDITOR_KEYS, VEHICLE_KEYS -> this.keysPage(c, page, rx, top, rw, bottom - top);
            case ENCODER -> this.encoderPage(c, rx, top, rw);
            default -> this.paragraph(c, key("done.detail").get(), rx, top, rw, this.ink);
        }

        /* The ways on and back */
        float by = cy + ch - pad - 24F;

        if (page > LANGUAGE)
        {
            this.button(c, key("back").get(), cx + pad, by, 90F, 24F, false, () -> this.go(page - 1));
        }

        this.button(c, key(page == DONE ? "start" : "next").get(), cx + cw - pad - 130F, by, 130F, 24F, true, this::next);
    }

    /* 0: the welcome, with the player's face */

    private void welcomePage(UIContext c, float cx, float cy, float cw, float ch)
    {
        float mx = cx + cw / 2F;
        float size = Math.min(72F, ch * 0.2F);
        float hy = cy + ch * 0.16F;

        /* The face in a ring of the accent, a light behind it */
        this.glow(c, mx, hy + size / 2F, size * 1.3F, 0x50000000 | (BBSSettings.primaryColor.get() & 0xFFFFFF));
        this.round(c, mx - size / 2F - 4F, hy - 4F, size + 8F, size + 8F, 8, accent());
        this.round(c, mx - size / 2F - 2F, hy - 2F, size + 4F, size + 4F, 7, this.card | 0xFF000000);
        this.head(c, Math.round(mx - size / 2F), Math.round(hy), Math.round(size));

        float ty = hy + size + 22F;
        String title = key("welcome.title").format(this.playerName()).get();

        this.text(c, title, mx - this.width(c, title, 2F) / 2F, ty, this.ink, 2F);
        ty += 30F;
        ty += this.centred(c, key("welcome.subtitle").get(), mx, ty, cw * 0.6F, this.muted);

        float bw = 220F;
        float bh = 32F;
        float by = Math.max(ty + 18F, cy + ch - 34F - bh - 22F);

        this.button(c, key("welcome.start").get(), mx - bw / 2F, by, bw, bh, true, this::next);
        this.centred(c, key("welcome.hint").get(), mx, by + bh + 10F, cw * 0.6F, this.muted);
    }

    /** The player's face, straight off their skin, faded with the page */
    private void head(UIContext c, int x, int y, int size)
    {
        int a = Math.round(255 * this.alpha);

        if (a < MIN_TEXT_ALPHA)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        GameProfile profile = this.profile;
        SkinTextures skin;

        try
        {
            skin = profile == null ? null : mc.getSkinProvider().getSkinTextures(profile);
        }
        catch (Throwable e)
        {
            skin = null;
        }

        if (skin == null)
        {
            return;
        }

        c.batcher.flush();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1F, 1F, 1F, a / 255F);
        PlayerSkinDrawer.draw(c.batcher.getContext(), skin, Math.round(x + this.ox), y, size);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /* 1: the language, every one BBS has */

    private void languagePage(UIContext c, float x, float y, float w, float h)
    {
        if (this.languages == null)
        {
            this.languages = BBSModClient.getL10n().getSupportedLanguageLabels();
        }

        float rowH = 24F;
        float content = this.languages.size() * (rowH + 3F);
        String current = BBSSettings.language.get();
        float max = Math.max(0F, content - h);

        /* The wheel moves the target; the list eases after it */
        this.languageTarget = Math.max(0F, Math.min(this.languageTarget, max));
        this.languageScroll += (this.languageTarget - this.languageScroll) * this.smoothing();
        this.languageScroll = Math.max(0F, Math.min(this.languageScroll, max));

        if (this.interactive)
        {
            this.listX = x + this.ox;
            this.listY = y;
            this.listW = w;
            this.listH = h;
        }

        c.batcher.clip(Math.round(x + this.ox), Math.round(y), Math.round(w), Math.round(h), c);

        for (int i = 0; i < this.languages.size(); i++)
        {
            Label<String> label = this.languages.get(i);
            float ry = y + i * (rowH + 3F) - this.languageScroll;

            if (ry + rowH < y || ry > y + h)
            {
                continue;
            }

            /* Only the part of the row inside the list takes the click */
            float top = Math.max(ry, y);
            float bottom = Math.min(ry + rowH, y + h);
            boolean chosen = label.value.equals(current);
            boolean hover = bottom > top && this.hover(x, top, w - 8F, bottom - top);

            this.round(c, x, ry, w - 8F, rowH, 4, chosen ? accent() : hover ? this.optionHover : this.option);
            this.text(c, label.title.get(), x + 12F, ry + 8F, chosen ? ON_ACCENT : this.ink, 1F);

            if (chosen)
            {
                this.text(c, "✓", x + w - 26F, ry + 8F, ON_ACCENT, 1F);
            }

            if (bottom > top)
            {
                this.hit(x, top, w - 8F, bottom - top, () -> BBSSettings.language.set(label.value));
            }
        }

        /* The scrollbar */
        if (content > h)
        {
            float bar = Math.max(16F, h * h / content);
            float at = (h - bar) * this.languageScroll / (content - h);

            this.round(c, x + w - 4F, y + at, 3F, bar, 1, this.light ? 0x80000000 : 0x80FFFFFF);
        }

        c.batcher.unclip(c);
    }

    /* 2: the look, all of the studio's personalisation in two columns */

    private void themePage(UIContext c, float x, float y, float w)
    {
        float gap = 24F;
        float colW = (w - gap) / 2F;
        float rx = x + colW + gap;
        FontRenderer font = c.batcher.getFont();

        /* The four ready looks */
        this.text(c, key("theme.presets").get(), x, y, this.muted, 1F);

        float tilesY = y + 13F;
        float tileW = (colW - 3 * 6F) / 4F;

        for (int i = 0; i < THEMES.length; i++)
        {
            float tx = x + i * (tileW + 6F);
            boolean chosen = (BBSSettings.primaryColor.get() & 0xFFFFFF) == THEMES[i][1] && (BBSSettings.secondaryColor.get() & 0xFFFFFF) == THEMES[i][0];
            boolean hover = this.hover(tx, tilesY, tileW, 40F);
            boolean bright = luminance(THEMES[i][0]) > 0.6F;
            final int index = i;

            if (chosen || hover)
            {
                this.round(c, tx - 1, tilesY - 1, tileW + 2, 42F, 6, chosen ? accent() : (this.light ? 0x60000000 : 0x80FFFFFF));
            }

            this.round(c, tx, tilesY, tileW, 40F, 5, 0xFF000000 | THEMES[i][0]);
            this.round(c, tx + 6F, tilesY + 7F, tileW - 12F, 6F, 3, 0xFF000000 | THEMES[i][1]);
            this.text(c, font.limitToWidth(key("theme." + THEME_NAMES[i]).get(), (int) tileW - 10), tx + 6F, tilesY + 24F, bright ? 0xFF1C1C1E : 0xFFE8E8F0, 1F);
            this.hit(tx, tilesY, tileW, 40F, () -> this.applyTheme(index));
        }

        /* The three colours, typed */
        float fy = tilesY + 52F;
        float fieldW = (colW - 2 * 6F) / 3F;
        String[] labels = {"primary_color", "secondary_color", "stencil_highlight_color"};

        for (int i = 0; i < this.fields.length; i++)
        {
            HexField field = this.fields[i];
            float fx = x + i * (fieldW + 6F);
            boolean focused = this.focusedField == i;
            final int index = i;

            if (!focused)
            {
                field.read();
            }

            int swatch = field.digits == 8 ? field.get.getAsInt() | 0xFF000000 : 0xFF000000 | field.get.getAsInt();
            String shown = field.text + (focused && (this.clock % 1F) < 0.5F ? "_" : "");

            this.text(c, font.limitToWidth(L10n.lang("bbs.config.personalization." + labels[i]).get(), (int) fieldW), fx, fy, this.muted, 1F);
            this.round(c, fx - 1, fy + 12F, fieldW + 2, 24F, 5, focused ? accent() : this.edge);
            this.round(c, fx, fy + 13F, fieldW, 22F, 4, this.option);
            this.round(c, fx + 5F, fy + 18F, 12F, 12F, 3, swatch);
            this.text(c, font.limitToWidth(shown, (int) fieldW - 26), fx + 22F, fy + 20F, this.ink, 1F);
            this.hit(fx, fy + 13F, fieldW, 22F, () -> this.focusField(index));

            /* The swatch opens the picker; drawn after the box, it is hit before it */
            boolean overSwatch = this.hover(fx + 3F, fy + 16F, 16F, 16F);

            if (overSwatch)
            {
                this.round(c, fx + 4F, fy + 17F, 14F, 14F, 3, 0xFFFFFFFF);
                this.round(c, fx + 5F, fy + 18F, 12F, 12F, 3, swatch);
            }

            this.hit(fx + 3F, fy + 16F, 16F, 16F, () -> this.openPicker(index, fx + this.ox, fy + 38F));
        }

        /* The interface scale, a step at a time */
        float sy = fy + 48F;
        float scale = BBSSettings.userIntefaceScale.get();
        String value = scale <= 0F ? key("theme.auto").get() : (scale % 1F == 0F ? String.valueOf((int) scale) : String.valueOf(scale)) + "x";

        this.text(c, L10n.lang("bbs.config.appearance.ui_scale").get(), x, sy + 6F, this.ink, 1F);
        this.button(c, "−", x + colW - 110F, sy, 24F, 20F, false, () -> this.stepScale(-1));
        this.round(c, x + colW - 82F, sy, 54F, 20F, 4, this.option);
        this.text(c, value, x + colW - 55F - this.width(c, value, 1F) / 2F, sy + 6F, this.ink, 1F);
        this.button(c, "+", x + colW - 24F, sy, 24F, 20F, false, () -> this.stepScale(1));

        /* Animations and smooth scrolling */
        this.toggle(c, x, sy + 32F, colW, key("theme.animations").get(), BBSSettings.popupAnimations);
        this.toggle(c, x, sy + 56F, colW, L10n.lang("bbs.config.scrollbars.smoothness").get(), BBSSettings.scrollingSmoothness);

        /* The other column: the panels' dimming and glass, and the light on the controls */
        this.slider(c, 0, rx, y, colW, L10n.lang("bbs.config.personalization.overlay_background_opacity").get());
        this.toggle(c, rx, y + 34F, colW, L10n.lang("bbs.config.personalization.interface_blur").get(), BBSSettings.interfaceBlur);
        this.slider(c, 1, rx, y + 60F, colW, L10n.lang("bbs.config.personalization.interface_blur_radius").get());
        this.toggle(c, rx, y + 94F, colW, L10n.lang("bbs.config.personalization.interface_shadows").get(), BBSSettings.interfaceShadows);
        this.toggle(c, rx, y + 118F, colW, L10n.lang("bbs.config.personalization.interface_highlights").get(), BBSSettings.interfaceHighlights);
        this.toggle(c, rx, y + 142F, colW, L10n.lang("bbs.config.personalization.interface_glow").get(), BBSSettings.interfaceGlow);
        this.paragraph(c, key("theme.live").get(), rx, y + 170F, colW, this.muted);
    }

    /* 3: the layout, seven of them as tabs, the chosen one drawn below */

    private void layoutPage(UIContext c, float x, float y, float w, float h)
    {
        float tabW = (w - 6 * 4F) / 7F;
        FontRenderer font = c.batcher.getFont();

        for (int i = 0; i < NleLayouts.IDS.length; i++)
        {
            float tx = x + i * (tabW + 4F);
            boolean chosen = this.layout == i;
            boolean hover = this.hover(tx, y, tabW, 22F);
            String label = font.limitToWidth(NleLayouts.name(i).get(), (int) tabW - 6);
            final int index = i;

            this.raised(c, tx, y, tabW, 22F, 4, chosen ? accent() : hover ? this.optionHover : this.option);
            this.text(c, label, tx + tabW / 2F - font.getWidth(label) / 2F, y + 7F, chosen ? ON_ACCENT : this.ink, 1F);
            this.hit(tx, y, tabW, 22F, () -> this.chooseLayout(index));
        }

        float py = y + 30F;
        float descH = 24F;
        float ph = h - 30F - descH;
        float pw = Math.min(w, ph * 1.9F);
        float px = x + (w - pw) / 2F;

        this.round(c, px - 2F, py - 2F, pw + 4F, ph + 4F, 5, 0xFF14131C);

        NleLayouts.schematic(c, NleLayouts.tree(this.layout, this.original), px + this.ox, py, pw, ph, this.layout == NleLayouts.REELS, this.clock, this.alpha, accent());
        this.paragraph(c, NleLayouts.description(this.layout).get(), x, py + ph + 8F, w, this.muted);
    }

    /* 4 and 5: a keyboard */

    private void keysPage(UIContext c, int page, float x, float y, float w, float h)
    {
        List<Bind> list = this.binds(page);
        float listW = Math.min(210F, w * 0.34F);
        float rowH = 24F;

        for (int i = 0; i < list.size(); i++)
        {
            Bind bind = list.get(i);
            float ry = y + i * (rowH + 4F);
            boolean listening = this.listening == i;
            boolean conflict = this.conflicts(list, bind);
            boolean hover = this.hover(x, ry, listW, rowH);
            float pulse = 0.5F + 0.5F * (float) Math.sin(this.clock * 8F);

            if (listening)
            {
                this.round(c, x - 1, ry - 1, listW + 2, rowH + 2, 5, accent());
            }
            else if (conflict)
            {
                this.round(c, x - 1, ry - 1, listW + 2, rowH + 2, 5, 0xFF000000 | blend(WARN & 0xFFFFFF, BAD & 0xFFFFFF, pulse));
            }

            this.round(c, x, ry, listW, rowH, 4, hover ? this.optionHover : this.option);
            this.rect(c, x + 6F, ry + 6F, 4F, rowH - 12F, bind.color());

            final int index = i;
            String name = c.batcher.getFont().limitToWidth(bind.name().get(), (int) listW - 70);
            String combo = listening ? key("keys.listen").get() : bind.combo();

            this.text(c, name, x + 16F, ry + 8F, this.ink, 1F);
            this.text(c, combo, x + listW - this.width(c, combo, 1F) - 8F, ry + 8F, listening ? accent() : conflict ? WARN : this.muted, 1F);
            this.hit(x, ry, listW, rowH, () -> this.listening = this.listening == index ? -1 : index);
        }

        boolean anyConflict = false;

        for (Bind bind : list)
        {
            anyConflict |= this.conflicts(list, bind);
        }

        String hint = (anyConflict ? key("keys.conflict") : this.listening >= 0 ? key("keys.listen_hint") : key("keys.idle_hint")).get();

        this.paragraph(c, hint, x, y + list.size() * (rowH + 4F) + 6F, listW, anyConflict ? WARN : this.muted);

        /* The keyboard itself, as large as the room allows */
        float kx = x + listW + 16F;
        float kw = x + w - kx;
        float unit = Math.max(14F, Math.min(kw / 15F, (h - 4F) / 5.2F));
        float gap = Math.max(1.5F, unit * 0.08F);

        for (Cap cap : this.caps)
        {
            this.cap(c, cap, list, kx + cap.x * unit, y + cap.y * unit, cap.w * unit - gap, unit - gap, unit);
        }
    }

    private void cap(UIContext c, Cap cap, List<Bind> list, float x, float y, float w, float h, float unit)
    {
        float press = cap.code >= 0 && cap.code < this.pressed.length ? this.pressed[cap.code] : 0F;
        int tint = 0;
        boolean main = false;
        boolean conflict = false;

        for (Bind bind : list)
        {
            if (bind.code() == cap.code)
            {
                tint = bind.color();
                main = true;
                conflict |= this.conflicts(list, bind);
            }
            else if (tint == 0 && bind.modifiers().contains(cap.code))
            {
                tint = bind.color();
            }
        }

        int baseFace = this.light ? 0xFFFFFF : 0x3E3D4A;
        int baseSide = this.light ? 0xC7C7CC : 0x24232E;
        int face = 0xFF000000 | baseFace;
        int side = 0xFF000000 | baseSide;

        if (tint != 0)
        {
            face = 0xFF000000 | blend(baseFace, tint & 0xFFFFFF, main ? 0.75F : 0.35F);
            side = 0xFF000000 | blend(baseSide, tint & 0xFFFFFF, main ? 0.4F : 0.15F);
        }

        if (conflict)
        {
            float pulse = 0.5F + 0.5F * (float) Math.sin(this.clock * 8F);

            face = 0xFF000000 | blend(WARN & 0xFFFFFF, BAD & 0xFFFFFF, pulse);
            side = 0xFF000000 | blend(0x7A4A00, 0x7A1A1A, pulse);
        }

        /* Pressed, the cap sinks onto its base */
        float depth = Math.max(2F, unit * 0.1F);
        float drop = depth * press;

        this.round(c, x, y + depth, w, h - depth, 3, side);
        this.round(c, x, y + drop, w, h - depth, 3, face);

        if (!cap.label.isEmpty())
        {
            FontRenderer font = c.batcher.getFont();
            String label = font.limitToWidth(cap.label, (int) w - 2);
            int color = main ? 0xFFFFFFFF : this.light ? 0xFF3A3A3C : 0xFFD8D8DE;

            this.text(c, label, x + w / 2F - font.getWidth(label) / 2F, y + drop + (h - depth) / 2F - font.getHeight() / 2F, color, 1F);
        }

        if (this.hover(x, y, w, h))
        {
            this.rect(c, x, y + drop, w, 1F, this.light ? 0x40000000 : 0x40FFFFFF);
        }

        final int code = cap.code;

        this.hit(x, y, w, h, () ->
        {
            if (this.listening >= 0)
            {
                this.assign(code);
            }
            else if (code >= 0 && code < this.virtualHeld.length)
            {
                this.virtualHeld[code] = System.nanoTime() + 180_000_000L;
            }
        });
    }

    /** Each cap sinks while its key is held, on the real keyboard or by a click on this one */
    private void updateKeys(float dt)
    {
        long now = System.nanoTime();

        for (Cap cap : this.caps)
        {
            int code = cap.code;
            boolean down = Window.isKeyPressed(code) || this.virtualHeld[code] > now;
            float target = down ? 1F : 0F;

            this.pressed[code] += (target - this.pressed[code]) * Math.min(1F, dt * 22F);

            if (Math.abs(this.pressed[code] - target) < 0.01F)
            {
                this.pressed[code] = target;
            }
        }
    }

    /* 6: the encoder */

    private void encoderPage(UIContext c, float x, float y, float w)
    {
        int state = this.encoder;
        int color = state == 2 ? GOOD : state == 3 ? WARN : this.muted;
        String line = state == 2 ? key("ffmpeg.found").format(this.encoderVersion == null ? "" : this.encoderVersion).get()
            : state == 3 ? key("ffmpeg.internal").get() : key("ffmpeg.checking").get();

        this.round(c, x, y, w, 52F, 6, this.option);
        this.round(c, x + 12F, y + 14F, 24F, 24F, 12, color);
        this.text(c, state == 2 ? "✓" : state == 3 ? "!" : "…", x + 20F, y + 21F, 0xFF101014, 1.2F);
        this.paragraph(c, line, x + 48F, y + 10F, w - 60F, this.ink);

        if (state == 3)
        {
            this.button(c, key("ffmpeg.guide").get(), x, y + 62F, Math.min(200F, w), 24F, true, () -> UIUtils.openWebLink(DOWNLOAD_LINK));
            this.button(c, key("ffmpeg.recheck").get(), x, y + 92F, Math.min(200F, w), 24F, false, this::checkEncoder);
        }
    }

    /* The pictures on the left */

    private void illustration(UIContext c, int page, float x, float y, float w, float h)
    {
        float mx = x + w / 2F;
        float my = y + h / 2F;
        float r = Math.min(w, h) * 0.42F;

        switch (page)
        {
            case LANGUAGE ->
            {
                /* A globe of dots, turning */
                this.glow(c, mx, my, r * 1.2F, 0x50000000 | (BBSSettings.primaryColor.get() & 0xFFFFFF));

                for (int lat = -60; lat <= 60; lat += 30)
                {
                    for (int lon = 0; lon < 360; lon += 15)
                    {
                        double a = Math.toRadians(lon) + this.clock * 0.6D;
                        double b = Math.toRadians(lat);
                        double depth = Math.cos(b) * Math.cos(a);

                        if (depth > 0D)
                        {
                            float px = mx + (float) (Math.cos(b) * Math.sin(a) * r);
                            float py = my - (float) (Math.sin(b) * r);
                            int dot = 0xFF000000 | blend(BBSSettings.primaryColor.get() & 0xFFFFFF, this.light ? 0x1C1C1E : 0xFFFFFF, (float) (1D - depth) * 0.6F);

                            this.rect(c, px - 1F, py - 1F, 2.5F, 2.5F, dot);
                        }
                    }
                }
            }
            case ENCODER ->
            {
                /* A film reel, turning */
                this.round(c, mx - r, my - r, r * 2F, r * 2F, (int) r, this.light ? 0xFF9A9AA2 : 0xFF4A4A58);
                this.round(c, mx - r * 0.18F, my - r * 0.18F, r * 0.36F, r * 0.36F, (int) (r * 0.18F), this.card | 0xFF000000);

                for (int i = 0; i < 5; i++)
                {
                    double a = Math.PI * 2D * i / 5D + this.clock * 1.4D;
                    float hx = mx + (float) Math.cos(a) * r * 0.6F;
                    float hy = my + (float) Math.sin(a) * r * 0.6F;

                    this.round(c, hx - r * 0.16F, hy - r * 0.16F, r * 0.32F, r * 0.32F, (int) (r * 0.16F), this.card | 0xFF000000);
                }
            }
            default ->
            {
                /* A check mark in a ring */
                this.round(c, mx - r, my - r, r * 2F, r * 2F, (int) r, accent());
                this.round(c, mx - r * 0.92F, my - r * 0.92F, r * 1.84F, r * 1.84F, (int) (r * 0.92F), this.card | 0xFF000000);
                this.text(c, "✓", mx - this.width(c, "✓", 4F) / 2F, my - 14F, GOOD, 4F);
            }
        }
    }
}
