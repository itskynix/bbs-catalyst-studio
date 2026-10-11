package mchorse.bbs_mod.ui.film;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import net.minecraft.client.gl.Framebuffer;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.controller.UIMotionPathContextMenu;
import mchorse.bbs_mod.ui.film.controller.UIOnionSkinContextMenu;
import mchorse.bbs_mod.ui.film.controller.OrbitViewGizmo;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.film.utils.UICameraUtils;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageFolderOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCodes;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.ScreenshotRecorder;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector2i;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class UIFilmPreview extends UIElement
{
    /**
     * Layers an addon put over the preview.
     *
     * <p>Factories rather than elements: a preview is built anew every time a film editor is
     * opened, and an element belongs to exactly one of them.</p>
     */
    private static final List<Function<UIFilmPreview, UIElement>> OVERLAYS = new ArrayList<>();

    private List<AudioClip> clips = new ArrayList<>();
    private UIFilmPanel panel;
    private UIPlacementGizmo placementGizmo;

    public UIElement icons;
    public UIElement leftWing;
    public UIElement centerCapsule;
    public UIElement rightWing;

    public UIIcon onionSkin;
    public UIIcon teleport;
    public UIIcon motionPath;
    public UIIcon flight;
    public UIIcon plause;
    public UIIcon screenshot;
    public UIIcon recordReplay;
    public UIIcon loop;
    public UIIcon recordVideo;

    public UIIcon control;
    public UIIcon perspective;

    /** Whether the icon bar is currently shown - see {@link #updateIconsVisibility(UIContext)} */
    private boolean iconsVisible = true;

    /** The side of the axes crosshair's block, matching {@link RenderSystem#renderCrosshair}'s reach. */
    private static final int CURSOR_SIZE = 24;

    /** Everything drawn over the frame goes through here - see {@link PreviewHud}. */
    private final PreviewHud hud = new PreviewHud();

    /** Where the axes crosshair or the navigation ball landed this frame, for drawing and clicking. */
    private final Area navBlock = new Area();

    public UIFilmPreview(UIFilmPanel filmPanel)
    {
        this.panel = filmPanel;
        this.placementGizmo = new UIPlacementGizmo(filmPanel);

        this.icons = new UIElement()
        {
            @Override
            public void resize()
            {
                UIFilmPreview.this.updateToolbarLayout();
            }

            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                if (this.area.isInside(context))
                {
                    return true;
                }

                return super.subMouseClicked(context);
            }
        };
        this.icons.noCulling();

        this.leftWing = new UICapsule(false);
        this.leftWing.noCulling();

        this.centerCapsule = new UICapsule(true)
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                if (this.area.isInside(context))
                {
                    if (context.mouseButton == 0)
                    {
                        UIFilmPreview.this.plause.clickItself();
                    }
                    return true;
                }

                return super.subMouseClicked(context);
            }
        };
        this.centerCapsule.noCulling();

        this.rightWing = new UICapsule(false);
        this.rightWing.noCulling();

        /* Left wing buttons: [Onion Skin, Teleport, Motion Path, Flight Mode] */
        this.onionSkin = new UIIcon(Icons.ONION_SKIN, (b) -> this.openOnionSkin());
        this.onionSkin.highlight(() -> this.panel.getController().getOnionSkin().enabled.get(), Direction.BOTTOM);
        this.onionSkin.tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_TITLE);

        this.teleport = new UIIcon(Icons.MOVE_TO, (b) -> this.panel.teleportToCamera());
        this.teleport.tooltip(UIKeys.FILM_TELEPORT_TITLE);
        this.teleport.context((menu) ->
        {
            menu.action(Icons.MOVE_TO, UIKeys.FILM_TELEPORT_CONTEXT_PLAYER, this.panel.playerToCamera, () -> this.panel.setPlayerToCamera(!this.panel.playerToCamera));
            menu.action(Icons.COPY, UIKeys.CAMERA_PANELS_CONTEXT_COPY_POSITION, () ->
            {
                Position current = new Position(this.panel.getCamera());

                Map<String, Double> map = new LinkedHashMap<>();

                UICameraUtils.copyPoint(map, current.point);
                UICameraUtils.copyAngle(map, current.angle);

                Window.setClipboard(UICameraUtils.mapToString(map));
            });

            Map<String, Double> map = UICameraUtils.stringToMap(Window.getClipboard());

            if (!map.isEmpty())
            {
                menu.action(Icons.PASTE, UIKeys.CAMERA_PANELS_CONTEXT_PASTE_POSITION, () ->
                {
                    Position position = new Position();
                    Point point = UICameraUtils.createPoint(map);
                    Angle angle = UICameraUtils.createAngle(map);

                    if (point != null && angle != null)
                    {
                        position.point.set(point);
                        position.angle.set(angle);
                    }

                    this.panel.cameraEditor.editClip(position);
                });
            }
        });

        this.motionPath = new UIIcon(Icons.CURVES, (b) -> this.openMotionPath());
        this.motionPath.highlight(() -> this.panel.getController().getMotionPath().enabled.get(), Direction.BOTTOM);
        this.motionPath.tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_TITLE);

        this.flight = new UIIcon(Icons.PLANE, (b) -> this.panel.toggleFlight());
        this.flight.highlight(this.panel::isFlying, Direction.BOTTOM);
        this.flight.tooltip(UIKeys.CAMERA_EDITOR_KEYS_MODES_FLIGHT);
        this.flight.context((menu) ->
        {
            menu.action(Icons.POSE, UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_CONTROL, this.panel.getController().isControlling(), () -> this.panel.getController().toggleControl());
            menu.action(this.panel.getController().getOrbitModeIcon(), UIKeys.FILM_CONTROLLER_KEYS_CHANGE_CAMERA_MODE, () -> this.panel.getController().toggleOrbitMode());

            UIFilmController controller = this.panel.getController();

            if (controller.getPovMode() == UIFilmController.CAMERA_MODE_ORBIT)
            {
                menu.action(Icons.MOVE_TO, UIKeys.FILM_REPLAY_ORBIT_TELEPORT_TO_RECORDING, controller::teleportOrbitPivotToReplay);
                menu.action(Icons.LINK, UIKeys.FILM_CONTROLLER_KEYS_ATTACH_ORBIT, controller.orbit.isAttached(), controller::toggleOrbitAttachment);
                menu.action(Icons.FRUSTUM, UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_ORTHO, controller.orbit.isOrtho(), controller.orbit::toggleOrtho);
            }
        });

        /* Center capsule button: [Prominent Play / Pause scaled from center] */
        this.plause = new UIIcon(() -> this.panel.isRunning() ? Icons.PAUSE : Icons.PLAY, (b) -> this.panel.togglePlayback())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                Icon icon = this.getIcon();
                int color;

                if (this.isEnabled())
                {
                    if (this.isActive())
                    {
                        color = this.activeColor;
                    }
                    else
                    {
                        boolean isHovered = this.area.isInside(context.mouseX, context.mouseY)
                            || UIFilmPreview.this.centerCapsule.area.isInside(context.mouseX, context.mouseY);
                        color = isHovered ? this.hoverColor : this.iconColor;
                    }
                }
                else
                {
                    color = this.disabledColor;
                }

                /* Scaled Play / Pause icon centered in 26px capsule */
                float size = 19F;
                context.batcher.scaledIcon(icon, color, this.area.mx() - size / 2F, this.area.my() - size / 2F, size);
            }
        };
        this.plause.noCulling();
        this.plause.tooltip(UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAUSE);
        this.plause.context((menu) ->
        {
            menu.action(Icons.PLAY, UIKeys.CAMERA_EDITOR_KEYS_EDITOR_PLAY_FILM, () ->
            {
                if (!this.panel.checkShowNoCamera())
                {
                    this.panel.dashboard.closeThisMenu();

                    Films.playFilm(this.panel.getData().getId(), true);
                }
            });

            menu.action(Icons.PAUSE, UIKeys.CAMERA_EDITOR_KEYS_EDITOR_FREEZE_PAUSED, !this.panel.getController().isPaused(), () ->
            {
                this.panel.getController().setPaused(!this.panel.getController().isPaused());
            });
        });

        /* Right wing buttons: [Screenshot, Record / Replay, Loop, Video Export] */
        this.screenshot = new UIIcon(Icons.CAMERA, (b) -> this.takeScreenshot());
        this.screenshot.tooltip(UIKeys.FILM_SCREENSHOT);
        this.screenshot.context((menu) ->
        {
            menu.action(Icons.FOLDER, UIKeys.TEXTURE_OPEN_FOLDER, () -> UIUtils.openFolder(BBSModClient.getScreenshotRecorder().getScreenshots()));
        });

        this.recordReplay = new UIIcon(Icons.SPHERE, (b) -> this.panel.getController().pickRecording());
        this.recordReplay.highlight(() -> this.panel.getController().isRecording(), Direction.BOTTOM);
        this.recordReplay.tooltip(UIKeys.FILM_REPLAY_RECORD);
        this.recordReplay.context((menu) ->
        {
            menu.action(Icons.KEY, UIKeys.FILM_AUTO_KEYFRAME, BBSSettings.autoKeyframe.get(), this::toggleAutoKeyframe);

            menu.action(Icons.DOWNLOAD, UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_INSTANT_KEYFRAMES, this.panel.getController().isInstantKeyframes(), () ->
            {
                this.panel.getController().toggleInstantKeyframes();
            });

            menu.action(Icons.MOVE_TO, UIKeys.FILM_REPLAY_TELEPORT_TO_PLAYER, () -> this.panel.getController().keyframes.insertPlayerFrame());
            menu.action(Icons.SOUND, UIKeys.FILM_RENDER_AUDIO, this::renderAudio);
        });

        this.loop = new UIIcon(Icons.REFRESH, (b) ->
        {
            BBSSettings.editorLoop.set(!BBSSettings.editorLoop.get());
            UIUtils.playClick();
        });
        this.loop.highlight(() -> BBSSettings.editorLoop.get(), Direction.BOTTOM);
        this.loop.tooltip(UIKeys.CAMERA_EDITOR_KEYS_LOOPING_TITLE);
        this.loop.context((menu) ->
        {
            menu.action(Icons.REFRESH, UIKeys.CAMERA_EDITOR_KEYS_LOOPING_TITLE, BBSSettings.editorLoop.get(), () ->
            {
                BBSSettings.editorLoop.set(!BBSSettings.editorLoop.get());
                UIUtils.playClick();
            });
            menu.action(Icons.IN, UIKeys.CAMERA_EDITOR_KEYS_LOOPING_SET_MIN, () -> this.panel.cameraEditor.clips.setLoopMin());
            menu.action(Icons.OUT, UIKeys.CAMERA_EDITOR_KEYS_LOOPING_SET_MAX, () -> this.panel.cameraEditor.clips.setLoopMax());
            menu.action(Icons.LEFT_HANDLE, mchorse.bbs_mod.l10n.keys.IKey.raw("Mark In"), () -> this.panel.cameraEditor.clips.setInPoint());
            menu.action(Icons.RIGHT_HANDLE, mchorse.bbs_mod.l10n.keys.IKey.raw("Mark Out"), () -> this.panel.cameraEditor.clips.setOutPoint());
            menu.action(Icons.FULLSCREEN, mchorse.bbs_mod.l10n.keys.IKey.raw("Mark Clip"), () -> this.panel.cameraEditor.clips.markClip());
            menu.action(Icons.CLOSE, mchorse.bbs_mod.l10n.keys.IKey.raw("Clear In Out"), () -> this.panel.cameraEditor.clips.clearInOut());
        });

        this.recordVideo = new UIIcon(Icons.VIDEO_CAMERA, (b) ->
        {
            if (!this.canExport())
            {
                return;
            }

            mchorse.bbs_mod.ui.film.export.UIDeliverOverlayPanel deliver = new mchorse.bbs_mod.ui.film.export.UIDeliverOverlayPanel(this.panel);
            UIOverlay.addOverlay(this.getContext(), deliver, 720, 450);
        });
        this.recordVideo.tooltip(UIKeys.CAMERA_TOOLTIPS_RECORD);
        this.recordVideo.context((menu) ->
        {
            menu.action(Icons.CAMERA, UIKeys.FILM_SCREENSHOT, this::takeScreenshot);

            menu.action(Icons.FILM, UIKeys.CAMERA_TOOLTIPS_OPEN_VIDEOS, () -> this.panel.recorder.openMovies());
            menu.action(Icons.GEAR, UIKeys.CAMERA_TOOLTIPS_OPEN_VIDEO_SETTINGS, () ->
            {
                UISettingsOverlayPanel panel = new UISettingsOverlayPanel();

                panel.showCategory("bbs", "video");
                UIOverlay.addOverlay(this.getContext(), panel, 430, 400);
            });

            menu.action(Icons.VIDEO_CAMERA, UIKeys.FILM_RENDER_QUEUE, this::exportQueueFromTabs);
            menu.action(Icons.SOUND, UIKeys.FILM_RENDER_AUDIO, this::renderAudio);
            menu.action(Icons.REFRESH, UIKeys.FILM_RESET_REPLAYS, this.panel.recorder.resetReplays, () ->
            {
                this.panel.recorder.resetReplays = !this.panel.recorder.resetReplays;
            });
        });

        /* Compatibility buttons */
        this.control = new UIIcon(Icons.POSE, (b) -> this.panel.getController().toggleControl());
        this.control.highlight(() -> this.panel.getController().isControlling(), Direction.BOTTOM);
        this.control.tooltip(UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_CONTROL);
        this.control.area.set(-100, -100, 0, 0);
        this.control.setVisible(false);
        this.control.setEnabled(false);

        this.perspective = new UIIcon(this.panel.getController()::getOrbitModeIcon, (b) -> this.panel.getController().toggleOrbitMode());
        this.perspective.tooltip(UIKeys.FILM_CONTROLLER_KEYS_CHANGE_CAMERA_MODE);
        this.perspective.area.set(-100, -100, 0, 0);
        this.perspective.setVisible(false);
        this.perspective.setEnabled(false);
        this.perspective.context((menu) ->
        {
            UIFilmController controller = this.panel.getController();

            if (controller.getPovMode() == UIFilmController.CAMERA_MODE_ORBIT)
            {
                menu.action(Icons.MOVE_TO, UIKeys.FILM_REPLAY_ORBIT_TELEPORT_TO_RECORDING, controller::teleportOrbitPivotToReplay);
                menu.action(Icons.LINK, UIKeys.FILM_CONTROLLER_KEYS_ATTACH_ORBIT, controller.orbit.isAttached(), controller::toggleOrbitAttachment);
                menu.action(Icons.FRUSTUM, UIKeys.FILM_CONTROLLER_KEYS_TOGGLE_ORTHO, controller.orbit.isOrtho(), controller.orbit::toggleOrtho);
            }
        });

        /* Prevent culling on toolbar buttons */
        this.onionSkin.noCulling();
        this.teleport.noCulling();
        this.motionPath.noCulling();
        this.flight.noCulling();
        this.screenshot.noCulling();
        this.recordReplay.noCulling();
        this.loop.noCulling();
        this.recordVideo.noCulling();

        /* Assemble the 3-part symmetric control center */
        this.leftWing.add(this.onionSkin, this.teleport, this.motionPath, this.flight);
        this.centerCapsule.add(this.plause);
        this.rightWing.add(this.screenshot, this.recordReplay, this.loop, this.recordVideo);

        this.icons.add(this.leftWing, this.centerCapsule, this.rightWing);
        this.add(this.icons);

        this.updateToolbarLayout();

        for (Function<UIFilmPreview, UIElement> factory : OVERLAYS)
        {
            this.add(factory.apply(this));
        }
    }

    public class UICapsule extends UIElement
    {
        public boolean rounded = false;

        public UICapsule()
        {}

        public UICapsule(boolean rounded)
        {
            this.rounded = rounded;
        }

        @Override
        public void resize()
        {
            /* Explicitly managed by updateToolbarLayout() */
        }

        @Override
        public void render(UIContext context)
        {
            if (!this.isVisible())
            {
                return;
            }

            Area a = this.area;
            int bgColor = BBSSettings.lightSurfaces() ? 0xDDEDEDED : 0xDD181818;
            int outlineColor = BBSSettings.lightSurfaces() ? 0x44000000 : 0x22FFFFFF;

            if (this.rounded)
            {
                /* 2px rounded / softened corners for the central Play capsule */
                context.batcher.dropShadow(a.x, a.y, a.ex(), a.ey(), 3, 0x00000000, 0x33000000);
                context.batcher.box(a.x + 1, a.y, a.ex() - 1, a.ey(), bgColor);
                context.batcher.box(a.x, a.y + 1, a.x + 1, a.ey() - 1, bgColor);
                context.batcher.box(a.ex() - 1, a.y + 1, a.ex(), a.ey() - 1, bgColor);

                context.batcher.box(a.x + 2, a.y, a.ex() - 2, a.y + 1, outlineColor);
                context.batcher.box(a.x + 2, a.ey() - 1, a.ex() - 2, a.ey(), outlineColor);
                context.batcher.box(a.x, a.y + 2, a.x + 1, a.ey() - 2, outlineColor);
                context.batcher.box(a.ex() - 1, a.y + 2, a.ex(), a.ey() - 2, outlineColor);

                context.batcher.box(a.x + 1, a.y + 1, a.x + 2, a.y + 2, outlineColor);
                context.batcher.box(a.ex() - 2, a.y + 1, a.ex() - 1, a.y + 2, outlineColor);
                context.batcher.box(a.x + 1, a.ey() - 2, a.x + 2, a.ey() - 1, outlineColor);
                context.batcher.box(a.ex() - 2, a.ey() - 2, a.ex() - 1, a.ey() - 1, outlineColor);
            }
            else
            {
                context.batcher.dropShadow(a.x, a.y, a.ex(), a.ey(), 3, 0x00000000, 0x33000000);
                context.batcher.box(a.x, a.y, a.ex(), a.ey(), bgColor);
                context.batcher.outline(a.x, a.y, a.ex(), a.ey(), outlineColor);
            }

            /* Subtle hover tile behind hovered buttons */
            for (IUIElement child : this.getChildren())
            {
                if (child instanceof UIIcon && child.isEnabled())
                {
                    Area ca = ((UIIcon) child).area;
                    boolean hovered = ca.isInside(context.mouseX, context.mouseY)
                        || (this == UIFilmPreview.this.centerCapsule && this.area.isInside(context.mouseX, context.mouseY));

                    if (hovered)
                    {
                        int hoverTile = BBSSettings.lightSurfaces() ? 0x22000000 : 0x22FFFFFF;

                        if (this.rounded)
                        {
                            context.batcher.box(ca.x + 1, ca.y, ca.ex() - 1, ca.ey(), hoverTile);
                            context.batcher.box(ca.x, ca.y + 1, ca.x + 1, ca.ey() - 1, hoverTile);
                            context.batcher.box(ca.ex() - 1, ca.y + 1, ca.ex(), ca.ey() - 1, hoverTile);
                        }
                        else
                        {
                            context.batcher.box(ca.x, ca.y, ca.ex(), ca.ey(), hoverTile);
                        }
                    }
                }
            }

            super.render(context);
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (this.area.isInside(context))
            {
                return true;
            }

            return super.subMouseClicked(context);
        }
    }

    /** Puts a layer of an addon's over the preview — one that takes the mouse, not just draws. */
    public static void registerOverlay(Function<UIFilmPreview, UIElement> factory)
    {
        OVERLAYS.add(factory);
    }

    public void takeScreenshot()
    {
        ScreenshotRecorder recorder = BBSModClient.getScreenshotRecorder();
        File output = Window.isAltPressed() ? null : recorder.getScreenshotFile();

        UIFilmPanel.applyExportSizeToBBS();
        BBSRendering.scheduleAfterNextExportFrame(() ->
        {
            Texture texture = BBSRendering.getTexture();
            int w = BBSRendering.getVideoWidth();
            int h = BBSRendering.getVideoHeight();
            recorder.takeScreenshot(output, texture.id, w, h);
            this.panel.restorePreviewSize();

            UIBaseMenu currentMenu = UIScreen.getCurrentMenu();
            if (currentMenu != null)
            {
                UIMessageFolderOverlayPanel overlayPanel = new UIMessageFolderOverlayPanel(
                    UIKeys.FILM_SCREENSHOT_TITLE,
                    UIKeys.FILM_SCREENSHOT_DESCRIPTION,
                    recorder.getScreenshots()
                );
                UIOverlay.addOverlay(currentMenu.context, overlayPanel);
            }
        });
    }

    /**
     * Auto-keyframing: with it on, every value edit keys the playhead instead of rewriting the
     * keyframe it was made on. Kept in the settings rather than on the film, so it stays where the
     * animator left it across films and sessions, the way looping does.
     */
    private void toggleAutoKeyframe()
    {
        BBSSettings.autoKeyframe.set(!BBSSettings.autoKeyframe.get());
        UIUtils.playClick();
    }

    public void openOnionSkin()
    {
        this.getContext().replaceContextMenu(new UIOnionSkinContextMenu(this.panel, this.panel.getController().getOnionSkin()));
    }

    public void openMotionPath()
    {
        this.getContext().replaceContextMenu(new UIMotionPathContextMenu(this.panel, this.panel.getController().getMotionPath()));
    }

    private void exportQueueFromTabs()
    {
        if (this.canExport())
        {
            this.panel.startQueueExportFromOpenTabs();
        }
    }

    /**
     * Whether an export can start at all: there has to be a camera, and ffmpeg has to be
     * installed. Both refusals say so on screen, ffmpeg's with a link to the guide.
     */
    private boolean canExport()
    {
        if (this.panel.checkShowNoCamera())
        {
            return false;
        }

        if (!FFMpegUtils.checkFFMPEG())
        {
            UIMessageOverlayPanel panel = new UIMessageOverlayPanel(UIKeys.GENERAL_WARNING, UIKeys.GENERAL_FFMPEG_ERROR_DESCRIPTION);
            UIIcon guide = new UIIcon(Icons.HELP, (bb) -> UIUtils.openWebLink(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE_LINK.get()));

            guide.tooltip(UIKeys.GENERAL_FFMPEG_ERROR_GUIDE, Direction.LEFT);
            panel.icons.add(guide);

            UIOverlay.addOverlay(this.getContext(), panel);

            return false;
        }

        return true;
    }

    private void renderAudio()
    {
        Clips camera = this.panel.getData().camera;
        List<AudioClip> audioClips = camera.getClips(AudioClip.class);

        String name = StringUtils.createTimestampFilename() + ".wav";
        File videos = BBSRendering.getVideoFolder();
        UIContext context = this.getContext();
        Vector2i range = BBSSettings.editorLoop.get() ? this.panel.getLoopingRange() : new Vector2i();

        if (AudioRenderer.renderAudio(new File(videos, name), audioClips, camera.calculateDuration(), 48000, TimeUtils.toSeconds(range.x), TimeUtils.toSeconds(range.y)))
        {
            UIOverlay.addOverlay(context, new UIMessageFolderOverlayPanel(UIKeys.GENERAL_SUCCESS, UIKeys.FILM_RENDER_AUDIO_SUCCESS, videos));
        }
        else
        {
            UIOverlay.addOverlay(context, new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, UIKeys.FILM_RENDER_AUDIO_ERROR));
        }
    }

    public Area getViewport()
    {
        int width = BBSRendering.getVideoWidth();
        int height = BBSRendering.getVideoHeight();
        int w = this.area.w;
        int h = this.area.h;

        Camera camera = new Camera();

        camera.copy(this.panel.getWorldCamera());
        camera.updatePerspectiveProjection(width, height);

        Vector2i size = Vectors.resize(width / (float) height, w, h);
        Area area = new Area();

        area.setSize(size.x, size.y);
        area.setPos(this.area.mx() - area.w / 2, this.area.my() - area.h / 2);

        return area;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        Area area = this.getViewport();

        if (area.isInside(context))
        {
            if (this.panel.getController().picker.pickTarget(context))
            {
                return true;
            }

            if (this.panel.getController().orbitGizmo.mouseClicked(context, this.navBlock))
            {
                return true;
            }

            if (this.placementGizmo.mouseClicked(context, area))
            {
                return true;
            }

            return this.panel.replayEditor.clickViewport(context, area);
        }

        return super.subMouseClicked(context);
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.placementGizmo.mouseReleased(context))
        {
            return true;
        }

        return super.subMouseReleased(context);
    }

    @Override
    protected boolean subMouseScrolled(UIContext context)
    {
        Area area = this.getViewport();

        if (area.isInside(context) && !this.panel.isFlying() && this.panel.getController().getPovMode() == UIFilmController.CAMERA_MODE_ORBIT)
        {
            return this.panel.getController().zoomOrbit(context.mouseWheel);
        }

        return super.subMouseScrolled(context);
    }

    public void updateToolbarLayout()
    {
        int leftWingW = 74;
        int rightWingW = 74;
        int playW = 26;
        int playH = 26;
        int wingH = 20;
        int gap = 3;

        int totalW = leftWingW + gap + playW + gap + rightWingW;
        int toolbarX = this.area.x + (this.area.w - totalW) / 2;
        int toolbarY = this.area.y + 8;

        int wingY = toolbarY + (playH - wingH) / 2;
        int playX = toolbarX + leftWingW + gap;
        int playY = toolbarY;
        int rightX = playX + playW + gap;

        this.icons.area.set(toolbarX, toolbarY, totalW, playH);
        this.leftWing.area.set(toolbarX, wingY, leftWingW, wingH);
        this.centerCapsule.area.set(playX, playY, playW, playH);
        this.rightWing.area.set(rightX, wingY, rightWingW, wingH);

        int btnW = 18;
        int btnH = 18;
        int btnY = wingY + (wingH - btnH) / 2;

        /* Left wing buttons: [Onion Skin, Teleport, Motion Path, Flight Mode] */
        int bx = toolbarX + 1;
        this.onionSkin.area.set(bx, btnY, btnW, btnH);
        bx += btnW;
        this.teleport.area.set(bx, btnY, btnW, btnH);
        bx += btnW;
        this.motionPath.area.set(bx, btnY, btnW, btnH);
        bx += btnW;
        this.flight.area.set(bx, btnY, btnW, btnH);

        /* Center play button: 22x22 centered inside 26x26 */
        int px = playX + (playW - 22) / 2;
        int py = playY + (playH - 22) / 2;
        this.plause.area.set(px, py, 22, 22);

        /* Right wing buttons: [Screenshot, Record/Replay, Loop, Video Export] */
        bx = rightX + 1;
        this.screenshot.area.set(bx, btnY, btnW, btnH);
        bx += btnW;
        this.recordReplay.area.set(bx, btnY, btnW, btnH);
        bx += btnW;
        this.loop.area.set(bx, btnY, btnW, btnH);
        bx += btnW;
        this.recordVideo.area.set(bx, btnY, btnW, btnH);
    }

    @Override
    public void resize()
    {
        super.resize();
        this.updateToolbarLayout();
    }

    /**
     * The icon bar can be set to stay out of the way until the mouse comes over the
     * preview. A context menu opened from an icon takes the mouse out of the preview,
     * so while any menu is up the bar keeps the visibility it had when it opened.
     */
    private void updateIconsVisibility(UIContext context)
    {
        if (BBSModClient.getVideoRecorder().isRecording())
        {
            this.iconsVisible = false;
            this.icons.setVisible(false);
            this.icons.setEnabled(false);
            return;
        }

        if (!BBSSettings.editorPreviewIconsAutoHide.get())
        {
            this.iconsVisible = true;
        }
        else if (!context.hasContextMenu())
        {
            this.iconsVisible = this.area.isInside(context.mouseX, context.mouseY);
        }

        this.icons.setVisible(this.iconsVisible);
        this.icons.setEnabled(this.iconsVisible);
    }

    @Override
    public void render(UIContext context)
    {
        Framebuffer fb = BBSRendering.getFramebuffer();
        Texture texture = BBSRendering.getTexture();
        Area area = this.getViewport();
        Camera camera = this.panel.getCamera();

        camera.copy(this.panel.getWorldCamera());
        camera.view.set(this.panel.lastView);
        camera.projection.set(this.panel.lastProjection);
        int fogColor = new Color(BBSRendering.getFogRed(), BBSRendering.getFogGreen(), BBSRendering.getFogBlue(), 1F).getARGBColor();
        context.batcher.box(area.x, area.y, area.ex(), area.ey(), fogColor);

        if (fb != null && fb.getColorAttachment() > 0)
        {
            context.batcher.texturedBox(fb.getColorAttachment(), Colors.WHITE, area.x, area.y, area.w, area.h, 0, fb.textureHeight, fb.textureWidth, 0, fb.textureWidth, fb.textureHeight);
        }
        else if (texture != null && texture.id > 0)
        {
            context.batcher.texturedBox(texture.id, Colors.WHITE, area.x, area.y, area.w, area.h, 0, texture.height, texture.width, 0, texture.width, texture.height);
        }

        this.updateToolbarLayout();
        this.updateIconsVisibility(context);
        this.hud.begin(area);

        if (this.iconsVisible && this.icons.area.ey() > area.y)
        {
            this.hud.reserve(PreviewHud.Anchor.TOP_CENTER, this.icons.area.ey() - area.y);
        }

        /* The navigation widget claims the bottom left corner before anything else does, so
         * that whatever the editor stacks there later (the stick guide) ends up above it
         * rather than on top of it. The ball replaces the axes crosshair when it is on, and
         * the block is reserved for whichever of the two is showing. Its click test reads
         * the same block, hence the field. */
        OrbitViewGizmo orbitGizmo = this.panel.getController().orbitGizmo;
        boolean ball = orbitGizmo.isActive();
        int navSize = ball ? orbitGizmo.getSize() : CURSOR_SIZE;

        this.navBlock.copy(this.hud.push(PreviewHud.Anchor.BOTTOM_LEFT, navSize, navSize));

        if (!ball)
        {
            this.renderCursor(context);
        }

        boolean needGuides = BBSSettings.editorRuleOfThirds.get()
            || BBSSettings.editorGoldenRatio.get()
            || BBSSettings.editorCenterLines.get()
            || BBSSettings.editorCrosshair.get();
        if (needGuides)
        {
            if (BBSSettings.editorRuleOfThirds.get())
            {
                int guidesColor = BBSSettings.editorGuidesColor.get();

                context.batcher.box(area.x + area.w / 3 - 1, area.y, area.x + area.w / 3, area.y + area.h, guidesColor);
                context.batcher.box(area.x + area.w - area.w / 3, area.y, area.x + area.w - area.w / 3 + 1, area.y + area.h, guidesColor);

                context.batcher.box(area.x, area.y + area.h / 3 - 1, area.x + area.w, area.y + area.h / 3, guidesColor);
                context.batcher.box(area.x, area.y + area.h - area.h / 3, area.x + area.w, area.y + area.h - area.h / 3 + 1, guidesColor);
            }

            if (BBSSettings.editorGoldenRatio.get())
            {
                int guidesColor = BBSSettings.editorGuidesColor.get();
                int gx1 = area.x + (int) Math.round(area.w * 0.381966F);
                int gx2 = area.x + (int) Math.round(area.w * 0.618034F);
                int gy1 = area.y + (int) Math.round(area.h * 0.381966F);
                int gy2 = area.y + (int) Math.round(area.h * 0.618034F);

                context.batcher.box(gx1 - 1, area.y, gx1, area.y + area.h, guidesColor);
                context.batcher.box(gx2 - 1, area.y, gx2, area.y + area.h, guidesColor);

                context.batcher.box(area.x, gy1 - 1, area.x + area.w, gy1, guidesColor);
                context.batcher.box(area.x, gy2 - 1, area.x + area.w, gy2, guidesColor);
            }

            if (BBSSettings.editorCenterLines.get())
            {
                int guidesColor = BBSSettings.editorGuidesColor.get();
                int x = area.mx();
                int y = area.my();

                context.batcher.box(area.x, y, area.ex(), y + 1, guidesColor);
                context.batcher.box(x, area.y, x + 1, area.ey(), guidesColor);
            }

            if (BBSSettings.editorCrosshair.get())
            {
                int x = area.mx() + 1;
                int y = area.my() + 1;

                context.batcher.box(x - 4, y - 1, x + 3, y, Colors.setA(Colors.WHITE, 0.5F));
                context.batcher.box(x - 1, y - 4, x, y + 3, Colors.setA(Colors.WHITE, 0.5F));
            }
        }

        int ratioMode = BBSSettings.editorCinematicAspectRatio.get();
        if (ratioMode > 0)
        {
            float targetAspect = 1F;
            if (ratioMode == 1) targetAspect = 2.39F;
            else if (ratioMode == 2) targetAspect = 1.85F;
            else if (ratioMode == 3) targetAspect = 4.0F / 3.0F;

            float currentAspect = (float) area.w / (float) Math.max(1, area.h);
            int barColor = 0xd0000000;

            if (currentAspect > targetAspect)
            {
                int targetW = Math.round(area.h * targetAspect);
                int barW = (area.w - targetW) / 2;
                context.batcher.box(area.x, area.y, area.x + barW, area.ey(), barColor);
                context.batcher.box(area.ex() - barW, area.y, area.ex(), area.ey(), barColor);
            }
            else if (currentAspect < targetAspect)
            {
                int targetH = Math.round(area.w / targetAspect);
                int barH = (area.h - targetH) / 2;
                context.batcher.box(area.x, area.y, area.ex(), area.y + barH, barColor);
                context.batcher.box(area.x, area.ey() - barH, area.ex(), area.ey(), barColor);
            }
        }

        this.placementGizmo.render(context, area);

        this.panel.getController().renderHUD(context, this.hud, this.navBlock);

        if (this.panel.replayEditor.isVisible() && BBSSettings.audioWaveformVisibleInPreview.get())
        {
            RunnerCameraController runner = this.panel.getRunner();
            int w = (int) (area.w * BBSSettings.audioWaveformWidth.get());
            int x = area.x(0.5F, w);
            float tick = this.panel.getCursor() + (runner.isRunning() ? context.getTransition() : 0);

            this.clips.clear();

            if (this.panel.getData() != null && this.panel.getData().camera != null)
            {
                for (Clip clip : this.panel.getData().camera.get())
                {
                    if (clip instanceof AudioClip)
                    {
                        this.clips.add((AudioClip) clip);
                    }
                }
            }

            int h = BBSSettings.audioWaveformHeight.get();
            int waveY = area.y + (this.iconsVisible && this.icons.area.ey() > area.y ? Math.max(10, this.icons.area.ey() - area.y + 4) : 10);

            if (BBSSettings.audioWaveformPreviewCombined.get())
            {
                AudioRenderer.renderPreviewCombined(context.batcher, this.clips, tick, x, waveY, w, h, context.menu.width, context.menu.height);
            }
            else
            {
                AudioRenderer.renderAll(context.batcher, this.clips, tick, x, waveY, w, h, context.menu.width, context.menu.height);
            }
        }

        /* Outside the bar's visibility: hiding the icons must not take away the one line
         * that says how to leave control mode. */
        if (this.panel.getController().isControlling())
        {
            String s = UIKeys.FILM_CONTROLLER_CONTROL_MODE_TOOLTIP.format(KeyCodes.getName(Keys.FILM_CONTROLLER_TOGGLE_CONTROL.getMainKey())).get();

            this.hud.label(context, PreviewHud.Anchor.BOTTOM_CENTER, s, Colors.WHITE, Colors.A50);
        }
        /* Free look holds the mouse the same way control mode does, so it owes the same line -
         * the pointer is gone, and this is what says how to get it back. */
        else if (this.panel.dashboard.orbitUI.isFreeLook())
        {
            String s = UIKeys.FILM_CONTROLLER_FREE_LOOK_TOOLTIP.format(KeyCodes.getName(Keys.FLIGHT.getMainKey())).get();

            this.hud.label(context, PreviewHud.Anchor.BOTTOM_CENTER, s, Colors.WHITE, Colors.A50);
        }

        /* Everything the corners collected this frame goes down here, each zone's wash first. */
        this.hud.flush(context);

        this.renderLoadingGate(context, area);

        context.batcher.clip(this.area, context);
        super.render(context);
        context.batcher.unclip(context);
    }

    private void renderLoadingGate(UIContext context, Area area)
    {
        if (this.panel == null || !this.panel.isSceneWarmingUp() || BBSModClient.getVideoRecorder().isRecording())
        {
            return;
        }

        /* 1. Semi-transparent dark scrim over preview viewport */
        context.batcher.box(area.x, area.y, area.ex(), area.ey(), Colors.A50 | 0x000000);

        /* 2. Centered Loading Badge */
        String baseText = UIKeys.FILM_PREVIEW_LOADING_SCENE.get();
        long now = System.currentTimeMillis();
        int dotIndex = (int) ((now / 350L) % 4);
        String label = baseText + ".".repeat(dotIndex);

        FontRenderer font = context.batcher.getFont();
        int textW = font.getWidth(label);
        int badgeW = Math.max(140, textW + 36);
        int badgeH = 24;
        int bx = area.x + (area.w - badgeW) / 2;
        int by = area.y + (area.h - badgeH) / 2;

        context.batcher.box(bx, by, bx + badgeW, by + badgeH, Colors.A75 | 0x141414);
        context.batcher.outline(bx, by, bx + badgeW, by + badgeH, Colors.A50 | 0x484848);

        /* Icon + Text */
        context.batcher.icon(Icons.REFRESH, bx + 8, by + (badgeH - 16) / 2);
        context.batcher.text(label, bx + 28, by + (badgeH - font.getHeight()) / 2, Colors.WHITE);
    }

    private void renderCursor(UIContext context)
    {
        net.minecraft.client.render.Camera /* NECESSARY */ mcCamera = MinecraftClient.getInstance().gameRenderer.getCamera();
        MatrixStack stack = RenderSystem.getModelViewStack();

        stack.push();

        stack.multiplyPositionMatrix(context.batcher.getContext().getMatrices().peek().getPositionMatrix());
        stack.translate(this.navBlock.mx(), this.navBlock.my(), 0F);
        stack.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(mcCamera.getPitch()));
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(mcCamera.getYaw()));
        stack.scale(-1F, -1F, -1F);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.renderCrosshair(10);

        stack.pop();
        RenderSystem.applyModelViewMatrix();
    }
}
