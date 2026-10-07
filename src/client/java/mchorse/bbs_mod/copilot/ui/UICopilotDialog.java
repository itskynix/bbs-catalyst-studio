package mchorse.bbs_mod.copilot.ui;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.overwrite.AICameraClip;
import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.copilot.CopilotConfig;
import mchorse.bbs_mod.copilot.CopilotPromptManager;
import mchorse.bbs_mod.copilot.GeminiClient;
import mchorse.bbs_mod.copilot.chat.CopilotChatMessage;
import mchorse.bbs_mod.copilot.chat.CopilotChatManager;
import mchorse.bbs_mod.copilot.chat.CopilotChatSession;
import mchorse.bbs_mod.copilot.chat.CopilotVisionHelper;
import mchorse.bbs_mod.data.DataParser;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.utils.UIFileDialogs;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class UICopilotDialog extends UIOverlayPanel {
    private final UIFilmPanel filmPanel;
    private KeyframeClip targetClip;

    public UIElement sidebar;
    public UIElement chatArea;

    public UIScrollView sessionsScroll;
    public UIScrollView messagesScroll;

    public UIElement inputContainer;
    public UIElement attachmentContainer;
    public UIThumbnailCard thumbnailCard;
    public UIElement inputRow;
    public UIIcon addImageBtn;
    public UITextbox promptInput;
    public UIButton sendBtn;
    public UILabel statusLabel;

    private CopilotVisionHelper.CapturedImage attachedScreenshot;
    private CompletableFuture<CopilotVisionHelper.CapturedImage> captureFuture;
    private boolean isCapturingImage = false;
    private boolean pendingSend = false;
    private boolean isSending = false;

    public UICopilotDialog(UIFilmPanel filmPanel) {
        this(filmPanel, null);
    }

    public UICopilotDialog(UIFilmPanel filmPanel, KeyframeClip targetClip) {
        super(L10n.lang("bbs.copilot.dialog_title"));

        this.filmPanel = filmPanel;
        this.targetClip = targetClip;

        // Left sidebar (sessions)
        this.sidebar = new UIElement();
        this.sidebar.relative(this.content).xy(4, 4).w(150).h(1F, -8);

        UIButton newChatBtn = new UIButton(L10n.lang("bbs.copilot.new_chat"), (b) -> this.startNewChat());
        newChatBtn.relative(this.sidebar).xy(0, 0).w(1F).h(20);

        this.sessionsScroll = new UIScrollView();
        this.sessionsScroll.relative(this.sidebar).xy(0, 24).w(1F).h(1F, -46).column(2).vertical().stretch().scroll().padding(2);

        UIButton clearAllBtn = new UIButton(L10n.lang("bbs.copilot.clear_all"), (b) -> this.clearAllChats());
        clearAllBtn.relative(this.sidebar).x(0).y(1F, -20).w(1F).h(20);

        this.sidebar.add(newChatBtn, this.sessionsScroll, clearAllBtn);

        // Right chat area
        this.chatArea = new UIElement();
        this.chatArea.relative(this.content).xy(158, 4).w(1F, -162).h(1F, -8);

        this.messagesScroll = new UIScrollView();
        this.messagesScroll.relative(this.chatArea).xy(0, 0).w(1F).h(1F, -42).column(4).vertical().stretch().scroll().padding(4);

        this.inputContainer = new UIElement();
        this.inputContainer.relative(this.chatArea).x(0).y(1F, -38).w(1F).h(38);

        // Attachment preview container
        this.attachmentContainer = new UIElement();
        this.attachmentContainer.relative(this.inputContainer).xy(0, 0).w(1F).h(40);

        this.thumbnailCard = new UIThumbnailCard(
            () -> this.attachedScreenshot != null ? this.attachedScreenshot.base64 : null,
            () -> this.isCapturingImage,
            () -> {
                this.attachedScreenshot = null;
                this.isCapturingImage = false;
                this.pendingSend = false;
                if (this.captureFuture != null) {
                    this.captureFuture.cancel(true);
                }
                this.updateAttachmentUI();
            }
        );
        this.thumbnailCard.relative(this.attachmentContainer).x(4).y(0).wh(56, 38);
        this.attachmentContainer.add(this.thumbnailCard);
        this.attachmentContainer.setVisible(false);

        // Input row
        this.inputRow = new UIElement();
        this.inputRow.relative(this.inputContainer).x(0).y(0).w(1F).h(20);
        this.inputRow.row(4).height(20);

        this.addImageBtn = new UIIcon(Icons.ADD, (b) -> this.pickImageFile());
        this.addImageBtn.wh(20, 20);
        this.addImageBtn.tooltip(L10n.lang("bbs.copilot.attach_image"));

        this.promptInput = new UITextbox(1000, null);
        this.promptInput.placeholder(L10n.lang("bbs.copilot.dialog_placeholder"));
        this.promptInput.h(20);

        this.sendBtn = new UIButton(L10n.lang("bbs.copilot.send"), (b) -> this.send());
        this.sendBtn.w(60).h(20);

        this.inputRow.add(this.addImageBtn, this.promptInput, this.sendBtn);

        // Status row
        this.statusLabel = UI.label(IKey.constant(""), 0).labelAnchor(0, 0.5f);
        this.statusLabel.relative(this.inputContainer).x(0).y(22).w(1F).h(12);

        this.inputContainer.add(this.attachmentContainer, this.inputRow, this.statusLabel);
        this.chatArea.add(this.messagesScroll, this.inputContainer);

        this.content.add(this.sidebar, this.chatArea);

        this.keys().register(Keys.CONFIRM, this::send);

        this.rebuildSessions();
        this.rebuildMessages();
    }

    @Override
    public int getPreferredWidth() {
        return 620;
    }

    @Override
    public int getContentHeight() {
        return 380;
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    public int getMessagesWidth() {
        return this.messagesScroll != null && this.messagesScroll.area.w > 0 ? this.messagesScroll.area.w : 420;
    }

    public void rebuildSessions() {
        this.sessionsScroll.removeAll();
        List<CopilotChatSession> sessions = CopilotChatManager.getInstance().getSessions();
        CopilotChatSession current = CopilotChatManager.getInstance().getCurrentSession();

        for (CopilotChatSession s : sessions) {
            boolean isCurrent = current != null && s.id.equals(current.id);
            String title = s.title != null && !s.title.isBlank() ? s.title : "Yeni Sohbet";
            if (title.length() > 18) {
                title = title.substring(0, 17) + "…";
            }
            String labelText = (isCurrent ? "• " : "  ") + title;

            UIButton btn = new UIButton(IKey.constant(labelText), (b) -> {
                CopilotChatManager.getInstance().setCurrentSession(s);
                this.rebuildSessions();
                this.rebuildMessages();
            });
            btn.h(18);

            btn.context((menu) -> {
                menu.action(Icons.EDIT, L10n.lang("bbs.copilot.rename"), () -> {
                    UIPromptOverlayPanel prompt = new UIPromptOverlayPanel(
                        L10n.lang("bbs.copilot.rename_title"),
                        L10n.lang("bbs.copilot.rename_desc"),
                        (newName) -> {
                            if (newName != null && !newName.isBlank()) {
                                CopilotChatManager.getInstance().renameSession(s.id, newName.trim());
                                this.rebuildSessions();
                            }
                        }
                    );
                    prompt.text.setText(s.title);
                    UIOverlay.addOverlay(this.getContext(), prompt);
                });

                menu.action(Icons.CLOSE, L10n.lang("bbs.copilot.delete"), () -> {
                    CopilotChatManager.getInstance().deleteSession(s.id);
                    this.rebuildSessions();
                    this.rebuildMessages();
                });
            });

            this.sessionsScroll.add(btn);
        }

        this.sessionsScroll.resize();
    }

    public void rebuildMessages() {
        this.messagesScroll.removeAll();
        CopilotChatSession current = CopilotChatManager.getInstance().getCurrentSession();

        if (current != null) {
            for (CopilotChatMessage msg : current.messages) {
                this.messagesScroll.add(new UIMessageCard(msg, this));
            }
        }

        this.messagesScroll.resize();
        this.scrollToBottom();
    }

    public void scrollToBottom() {
        this.messagesScroll.resize();
        this.messagesScroll.scroll.scrollTo(this.messagesScroll.scroll.scrollSize);
        this.messagesScroll.scroll.clamp();
    }

    private void startNewChat() {
        CopilotChatManager.getInstance().createSession("Yeni Sohbet");
        this.rebuildSessions();
        this.rebuildMessages();
    }

    private void clearAllChats() {
        CopilotChatManager.getInstance().clearAllSessions();
        this.rebuildSessions();
        this.rebuildMessages();
    }

    private void pickImageFile() {
        if (this.isCapturingImage || this.isSending) {
            return;
        }

        File screenshotsDir = new File(MinecraftClient.getInstance().runDirectory, "screenshots");
        if (!screenshotsDir.exists()) {
            screenshotsDir.mkdirs();
        }

        String[] filters = new String[]{"*.png", "*.jpg", "*.jpeg", "*.webp"};
        UIFileDialogs.pickFile(
            L10n.lang("bbs.copilot.pick_image_title"),
            screenshotsDir,
            filters,
            L10n.lang("bbs.copilot.image_files"),
            (file) -> {
                if (file != null && file.exists() && file.isFile()) {
                    this.loadImageFromFile(file);
                }
            }
        );
    }

    private void loadImageFromFile(File file) {
        if (this.isCapturingImage || this.isSending) {
            return;
        }

        this.isCapturingImage = true;
        this.attachedScreenshot = null;
        this.updateAttachmentUI();
        this.statusLabel.label = IKey.constant("");

        this.captureFuture = CopilotVisionHelper.loadImageAsync(file);
        this.captureFuture.thenAccept(img -> {
            MinecraftClient.getInstance().execute(() -> {
                this.isCapturingImage = false;
                this.attachedScreenshot = img;

                if (img == null) {
                    this.statusLabel.label = L10n.lang("bbs.copilot.image_load_failed");
                    this.statusLabel.color = 0xff5555;
                    if (this.pendingSend) {
                        this.pendingSend = false;
                        this.sendBtn.setEnabled(true);
                    }
                    this.updateAttachmentUI();
                    return;
                }

                this.updateAttachmentUI();

                if (this.pendingSend) {
                    this.pendingSend = false;
                    this.executeSend();
                }
            });
        }).exceptionally(ex -> {
            MinecraftClient.getInstance().execute(() -> {
                this.isCapturingImage = false;
                this.statusLabel.label = L10n.lang("bbs.copilot.image_load_failed");
                this.statusLabel.color = 0xff5555;
                if (this.pendingSend) {
                    this.pendingSend = false;
                    this.sendBtn.setEnabled(true);
                }
                this.updateAttachmentUI();
            });
            return null;
        });
    }

    private void updateAttachmentUI() {
        boolean hasAttachment = this.attachedScreenshot != null || this.isCapturingImage;
        this.attachmentContainer.setVisible(hasAttachment);

        if (hasAttachment) {
            this.attachmentContainer.xy(0, 0).w(1F).h(40);
            this.inputRow.xy(0, 42).w(1F).h(20);
            this.statusLabel.xy(0, 64).w(1F).h(12);
            this.inputContainer.relative(this.chatArea).x(0).y(1F, -78).w(1F).h(78);
            this.messagesScroll.relative(this.chatArea).xy(0, 0).w(1F).h(1F, -82);
        } else {
            this.inputRow.xy(0, 0).w(1F).h(20);
            this.statusLabel.xy(0, 22).w(1F).h(12);
            this.inputContainer.relative(this.chatArea).x(0).y(1F, -38).w(1F).h(38);
            this.messagesScroll.relative(this.chatArea).xy(0, 0).w(1F).h(1F, -42);
        }

        this.chatArea.resize();
        this.scrollToBottom();
    }

    private void send() {
        if (this.isSending) {
            return;
        }

        String apiKey = CopilotConfig.getInstance().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            this.statusLabel.label = L10n.lang("bbs.copilot.api_key_missing");
            this.statusLabel.color = 0xff5555;
            return;
        }

        String userPrompt = this.promptInput.textbox.getText().trim();
        if (userPrompt.isEmpty() && this.attachedScreenshot == null && !this.isCapturingImage) {
            this.statusLabel.label = L10n.lang("bbs.copilot.empty_prompt");
            this.statusLabel.color = 0xff5555;
            return;
        }

        if (this.isCapturingImage) {
            this.pendingSend = true;
            this.sendBtn.setEnabled(false);
            this.statusLabel.label = L10n.lang("bbs.copilot.sending");
            this.statusLabel.removeTooltip();
            this.statusLabel.color = 0xffff55;
            return;
        }

        this.executeSend();
    }

    private void executeSend() {
        if (this.isSending) {
            return;
        }
        this.isSending = true;

        String userPrompt = this.promptInput.textbox.getText().trim();
        CopilotChatSession session = CopilotChatManager.getInstance().getCurrentSession();
        if (session == null) {
            session = CopilotChatManager.getInstance().createSession("Yeni Sohbet");
        }

        if (("Yeni Sohbet".equals(session.title) || session.messages.isEmpty()) && !userPrompt.isEmpty()) {
            String autoTitle = userPrompt.length() > 22 ? userPrompt.substring(0, 22).trim() + "..." : userPrompt;
            session.title = autoTitle;
            CopilotChatManager.getInstance().save();
            this.rebuildSessions();
        }

        String promptToSend = userPrompt;
        if (promptToSend.isEmpty() && this.attachedScreenshot != null) {
            promptToSend = "Describe and choreograph camera for this scene.";
        }

        String imgBase64 = this.attachedScreenshot != null ? this.attachedScreenshot.base64 : null;
        CopilotChatMessage userMessage = new CopilotChatMessage("user", promptToSend, imgBase64);
        session.addMessage(userMessage);

        this.promptInput.textbox.setText("");
        this.attachedScreenshot = null;
        this.updateAttachmentUI();

        this.sendBtn.setEnabled(false);
        this.statusLabel.label = L10n.lang("bbs.copilot.generating");
        this.statusLabel.removeTooltip();
        this.statusLabel.color = 0xffff55;

        this.rebuildMessages();

        GeminiClient.getInstance().generateChat(new ArrayList<>(session.messages))
            .thenAccept(reply -> {
                MinecraftClient.getInstance().execute(() -> {
                    this.isSending = false;
                    this.sendBtn.setEnabled(true);
                    this.statusLabel.label = IKey.constant("");

                    CopilotChatMessage modelMessage = new CopilotChatMessage("model", reply);
                    CopilotChatSession activeSession = CopilotChatManager.getInstance().getCurrentSession();
                    if (activeSession != null) {
                        activeSession.addMessage(modelMessage);
                        CopilotChatManager.getInstance().save();
                    }

                    this.rebuildMessages();
                });
            })
            .exceptionally(ex -> {
                MinecraftClient.getInstance().execute(() -> {
                    this.isSending = false;
                    this.sendBtn.setEnabled(true);
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String errorMsg = cause.getMessage() != null ? cause.getMessage() : "Error";
                    IKey friendly = GeminiClient.getUserFriendlyError(errorMsg);
                    this.statusLabel.label = friendly;
                    this.statusLabel.tooltip(IKey.constant(errorMsg));
                    this.statusLabel.color = 0xff5555;
                });
                return null;
            });
    }

    @Override
    protected void renderBackground(UIContext context) {
        super.renderBackground(context);

        // Divider between sidebar and chat area
        int divX = this.sidebar.area.ex() + 3;
        context.batcher.box(divX, this.content.area.y + 2, divX + 1, this.content.area.ey() - 2, BBSSettings.dividerColor());
    }

    public int applyKeyframes(BaseType parsed) {
        if (this.filmPanel == null || this.filmPanel.getData() == null) {
            return 0;
        }

        KeyframeClip clip = this.targetClip;
        if (clip == null) {
            Clip current = this.filmPanel.cameraEditor.getClip();
            if (current instanceof KeyframeClip kc) {
                clip = kc;
            } else {
                for (Clip c : this.filmPanel.getData().camera.get()) {
                    if (c instanceof AICameraClip ac) {
                        clip = ac;
                        break;
                    } else if (clip == null && c instanceof KeyframeClip kc) {
                        clip = kc;
                    }
                }
                if (clip == null) {
                    AICameraClip aiClip = new AICameraClip();
                    aiClip.tick.set(this.filmPanel.getCursor());
                    aiClip.duration.set(80);
                    if (this.filmPanel.cameraEditor.getCamera() != null) {
                        aiClip.fromCamera(this.filmPanel.cameraEditor.getCamera());
                    }
                    clip = aiClip;
                    this.filmPanel.getData().camera.addClip(clip);
                }
            }
        }

        double ox = 0.0;
        double oy = 0.0;
        double oz = 0.0;
        double oYaw = 0.0;
        double oPitch = 0.0;

        if (clip instanceof AICameraClip aiClip) {
            Point originPoint = aiClip.origin.get();
            if (originPoint != null) {
                ox = originPoint.x;
                oy = originPoint.y;
                oz = originPoint.z;
            }
            Angle originAngle = aiClip.angle.get();
            if (originAngle != null) {
                oYaw = originAngle.yaw;
                oPitch = originAngle.pitch;
            }
            if (ox == 0.0 && oy == 0.0 && oz == 0.0 && this.filmPanel.cameraEditor.getCamera() != null) {
                Camera cam = this.filmPanel.cameraEditor.getCamera();
                ox = cam.position.x;
                oy = cam.position.y;
                oz = cam.position.z;
                aiClip.origin.set(new Point(ox, oy, oz));
                Angle a = new Angle(0, 0);
                a.set(cam);
                oYaw = a.yaw;
                oPitch = a.pitch;
                aiClip.angle.set(a);
            }
        } else if (this.filmPanel.cameraEditor.getCamera() != null) {
            Camera cam = this.filmPanel.cameraEditor.getCamera();
            ox = cam.position.x;
            oy = cam.position.y;
            oz = cam.position.z;
            Angle a = new Angle(0, 0);
            a.set(cam);
            oYaw = a.yaw;
            oPitch = a.pitch;
        }

        ListType kfList = null;
        if (parsed instanceof MapType map) {
            if (map.has("keyframes")) {
                kfList = map.getList("keyframes");
            } else if (map.has("tracks")) {
                kfList = map.getList("tracks");
            } else if (map.has("camera")) {
                kfList = map.getList("camera");
            }
        } else if (parsed instanceof ListType list) {
            kfList = list;
        }

        if (kfList == null || kfList.isEmpty()) {
            return 0;
        }

        float maxTick = 0f;
        int count = 0;

        for (int i = 0; i < kfList.size(); i++) {
            BaseType item = kfList.get(i);
            if (!(item instanceof MapType kf)) {
                continue;
            }

            float tick = 0f;
            if (kf.has("tick")) {
                tick = kf.getFloat("tick");
            } else if (kf.has("time")) {
                tick = kf.getFloat("time") * 20f;
            } else if (kf.has("t")) {
                tick = kf.getFloat("t");
            }

            if (tick > maxTick) {
                maxTick = tick;
            }

            if (kf.has("x")) clip.x.insert(tick, ox + kf.getDouble("x"));
            if (kf.has("y")) clip.y.insert(tick, oy + kf.getDouble("y"));
            if (kf.has("z")) clip.z.insert(tick, oz + kf.getDouble("z"));
            if (kf.has("yaw")) clip.yaw.insert(tick, oYaw + kf.getDouble("yaw"));
            if (kf.has("pitch")) clip.pitch.insert(tick, Math.max(-90.0, Math.min(90.0, oPitch + kf.getDouble("pitch"))));
            if (kf.has("roll")) clip.roll.insert(tick, kf.getDouble("roll"));
            if (kf.has("fov")) clip.fov.insert(tick, kf.getDouble("fov"));

            count++;
        }

        if (maxTick > clip.duration.get()) {
            clip.duration.set((int) Math.ceil(maxTick));
        }

        clip.postNotify();
        this.filmPanel.cameraEditor.pickClip(clip);

        return count;
    }

    public static class UIMessageCard extends UIElement {
        private final CopilotChatMessage message;
        private final UICopilotDialog dialog;
        private final UIText bodyText;
        private UIButton applyBtn;

        public UIMessageCard(CopilotChatMessage message, UICopilotDialog dialog) {
            this.message = message;
            this.dialog = dialog;

            this.column(4).vertical().stretch().padding(6);

            boolean isUser = message.isUser();

            // Header row
            UIElement header = new UIElement();
            header.row(4).height(12);

            if (isUser) {
                UILabel sender = UI.label(L10n.lang("bbs.copilot.you")).labelAnchor(0, 0.5f);
                sender.color(0x8ab4f8);
                header.add(sender);

                if (message.hasImage()) {
                    UILabel imgBadge = UI.label(L10n.lang("bbs.copilot.screenshot_attached")).labelAnchor(0, 0.5f);
                    imgBadge.color(0x55ffff);
                    header.add(imgBadge);
                }
            } else {
                UIIcon aiIcon = new UIIcon(Icons.SPARKLES, null);
                aiIcon.wh(12, 12);
                UILabel sender = UI.label(IKey.constant("Gemini")).labelAnchor(0, 0.5f);
                sender.color(0x4285f4);
                header.add(aiIcon, sender);
            }
            this.add(header);

            if (isUser && message.hasImage()) {
                UIThumbnailCard msgThumb = new UIThumbnailCard(() -> message.imageBase64, null, null);
                msgThumb.wh(80, 48);
                this.add(msgThumb);
            }

            // Message text
            this.bodyText = new UIText(message.text != null ? message.text : "");
            this.bodyText.color(Colors.WHITE, true);
            this.bodyText.padding(0, 2);
            this.add(this.bodyText);

            // Timeline apply action
            if (message.containsKeyframes()) {
                this.applyBtn = new UIButton(L10n.lang("bbs.copilot.apply_to_timeline"), (b) -> {
                    try {
                        String json = CopilotPromptManager.extractJson(message.text);
                        BaseType parsed = DataParser.parse(json);
                        int count = dialog.applyKeyframes(parsed);
                        if (count > 0) {
                            b.label = IKey.constant("✓ " + count + " " + L10n.lang("bbs.copilot.keyframes_applied").get());
                            b.setEnabled(false);
                        } else {
                            b.label = L10n.lang("bbs.copilot.no_keyframes");
                        }
                    } catch (Exception e) {
                        String err = e.getMessage() != null ? e.getMessage() : "Parse error";
                        b.label = IKey.constant("Hata: " + err);
                    }
                });
                this.applyBtn.h(18);
                this.add(this.applyBtn);
            }

            int w = dialog.getMessagesWidth();
            int innerWidth = Math.max(50, w - 16);
            FontRenderer font = Batcher2D.getDefaultTextRenderer();
            List<String> wrapped = font != null ? font.wrap(message.text != null ? message.text : "", innerWidth) : List.of();
            int textH = Math.max(1, wrapped.size()) * 12;
            int totalH = 12 + 4 + textH + 12;
            if (isUser && message.hasImage()) {
                totalH += 48 + 4;
            }
            if (this.applyBtn != null) {
                totalH += 4 + 18;
            }
            this.h(totalH);
        }

        @Override
        public void resize() {
            int w = this.area.w > 0 ? this.area.w : this.dialog.getMessagesWidth();
            int innerWidth = Math.max(50, w - 16);
            FontRenderer font = Batcher2D.getDefaultTextRenderer();
            List<String> wrapped = font != null ? font.wrap(this.message.text != null ? this.message.text : "", innerWidth) : List.of();
            int textH = Math.max(1, wrapped.size()) * 12;
            int totalH = 12 + 4 + textH + 12;
            if (this.message.isUser() && this.message.hasImage()) {
                totalH += 48 + 4;
            }
            if (this.applyBtn != null) {
                totalH += 4 + 18;
            }
            if (this.flex.h.value != totalH) {
                this.h(totalH);
            }
            super.resize();
        }

        @Override
        public void render(UIContext context) {
            boolean isUser = this.message.isUser();
            int bg = isUser ? 0xcc1a2a3a : 0xcc202228;
            int border = isUser ? 0xff2b4b70 : 0xff32363e;

            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), bg);
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), border);

            super.render(context);
        }
    }

    public static class UIThumbnailCard extends UIElement {
        private final Supplier<String> base64Supplier;
        private final Supplier<Boolean> loadingSupplier;
        private final UIIcon closeBtn;

        public UIThumbnailCard(Supplier<String> base64Supplier, Supplier<Boolean> loadingSupplier, Runnable onClose) {
            this.base64Supplier = base64Supplier;
            this.loadingSupplier = loadingSupplier;

            if (onClose != null) {
                this.closeBtn = new UIIcon(Icons.CLOSE, (b) -> onClose.run());
                this.closeBtn.relative(this).x(1F, -14).y(2).wh(12, 12);
                this.add(this.closeBtn);
            } else {
                this.closeBtn = null;
            }
        }

        @Override
        public void render(UIContext context) {
            int x = this.area.x;
            int y = this.area.y;
            int w = this.area.w;
            int h = this.area.h;

            context.batcher.box(x, y, x + w, y + h, 0xee161b22);
            context.batcher.outline(x, y, x + w, y + h, 0xff2b4b70);

            boolean loading = this.loadingSupplier != null && Boolean.TRUE.equals(this.loadingSupplier.get());
            String b64 = this.base64Supplier != null ? this.base64Supplier.get() : null;

            if (loading) {
                FontRenderer font = Batcher2D.getDefaultTextRenderer();
                String dots = "...";
                int tw = font != null ? font.getWidth(dots) : 10;
                context.batcher.text(dots, x + (w - tw) / 2, y + (h - 8) / 2, 0x8ab4f8);
            } else if (b64 != null && !b64.isEmpty()) {
                Texture tex = CopilotVisionHelper.getTexture(b64);
                if (tex != null && tex.width > 0 && tex.height > 0) {
                    int pad = 2;
                    int maxDrawW = w - pad * 2;
                    int maxDrawH = h - pad * 2;
                    float aspect = (float) tex.width / (float) tex.height;

                    int drawW = maxDrawW;
                    int drawH = (int) (drawW / aspect);
                    if (drawH > maxDrawH) {
                        drawH = maxDrawH;
                        drawW = (int) (drawH * aspect);
                    }
                    int drawX = x + (w - drawW) / 2;
                    int drawY = y + (h - drawH) / 2;

                    context.batcher.texturedBox(tex, Colors.WHITE, drawX, drawY, drawW, drawH, 0, 0, tex.width, tex.height);
                }
            }

            if (this.closeBtn != null) {
                context.batcher.box(this.closeBtn.area.x - 1, this.closeBtn.area.y - 1, this.closeBtn.area.ex() + 1, this.closeBtn.area.ey() + 1, 0xcc000000);
                context.batcher.outline(this.closeBtn.area.x - 1, this.closeBtn.area.y - 1, this.closeBtn.area.ex() + 1, this.closeBtn.area.ey() + 1, 0x88ffffff);
            }

            super.render(context);
        }
    }
}
