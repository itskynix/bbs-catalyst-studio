package mchorse.bbs_mod.copilot.ui;

import mchorse.bbs_mod.copilot.CopilotApiKeyValue;
import mchorse.bbs_mod.copilot.CopilotModelValue;
import mchorse.bbs_mod.copilot.CopilotTestConnectionValue;
import mchorse.bbs_mod.copilot.GeminiClient;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.ui.UIValueFactory;
import mchorse.bbs_mod.settings.ui.UIValueMap;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;

import java.util.List;

public final class UICopilotSetting {
    private UICopilotSetting() {
    }

    public static void register() {
        UIValueMap.register(CopilotApiKeyValue.class, (value, parent) -> {
            UIPasswordTextbox textbox = new UIPasswordTextbox((str) -> {
                value.set(str);
            });
            textbox.setText(value.get());
            textbox.w(150);

            UIIcon toggleMask = new UIIcon(() -> textbox.masked ? Icons.VISIBLE : Icons.INVISIBLE, (b) -> {
                textbox.masked = !textbox.masked;
            });
            toggleMask.wh(20, 20);
            toggleMask.tooltip(L10n.lang("bbs.copilot.toggle_visibility"));

            UIElement row = UI.row(2, 0, 20, textbox, toggleMask).h(20);
            row.w(175);
            row.valueBinding(() -> {
                if (!textbox.isFocused()) {
                    textbox.setText(value.get());
                }
            });

            return List.of(UIValueFactory.column(row, value));
        });

        UIValueMap.register(CopilotModelValue.class, (value, parent) -> {
            java.util.List<String> models = CopilotModelValue.MODELS;
            UICirculate circulate = new UICirculate((b) -> {
                int idx = b.getValue();
                if (idx >= 0 && idx < models.size()) {
                    value.set(models.get(idx));
                }
            });
            for (String m : models) {
                circulate.addLabel(IKey.constant(m));
            }
            int initialIdx = Math.max(0, models.indexOf(value.get()));
            circulate.setValue(initialIdx);
            circulate.w(175);

            circulate.context((menu) -> {
                for (int i = 0; i < models.size(); i++) {
                    final int idx = i;
                    final String m = models.get(i);
                    menu.action(Icons.PROCESSOR, IKey.constant(m), () -> {
                        value.set(m);
                        circulate.setValue(idx);
                    });
                }
            });

            circulate.valueBinding(() -> {
                int idx = models.indexOf(value.get());
                circulate.setValue(idx >= 0 ? idx : 0);
            });

            return List.of(UIValueFactory.column(circulate, value));
        });

        UIValueMap.register(CopilotTestConnectionValue.class, (value, parent) -> {
            UILabel statusLabel = UI.label(IKey.constant(""), 0).labelAnchor(0, 0.5f);
            statusLabel.color(Colors.WHITE);

            UIButton testBtn = new UIButton(L10n.lang("bbs.copilot.test"), (b) -> {
                statusLabel.label = L10n.lang("bbs.copilot.testing");
                statusLabel.color = 0xffff55;
                b.setEnabled(false);

                GeminiClient.getInstance().testConnection().thenAccept(result -> {
                    MinecraftClient.getInstance().execute(() -> {
                        b.setEnabled(true);
                        if (result.success) {
                            statusLabel.label = L10n.lang("bbs.copilot.connected");
                            statusLabel.removeTooltip();
                            statusLabel.color = 0x55ff55;
                        } else {
                            IKey friendly = GeminiClient.getUserFriendlyError(result.statusCode != -1 ? result.statusCode : GeminiClient.extractStatusCode(result.message));
                            statusLabel.label = friendly;
                            statusLabel.tooltip(IKey.constant(result.message));
                            statusLabel.color = 0xff5555;
                        }
                    });
                }).exceptionally(ex -> {
                    MinecraftClient.getInstance().execute(() -> {
                        b.setEnabled(true);
                        String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        if (msg == null || msg.isBlank()) {
                            msg = "Error";
                        }
                        IKey friendly = GeminiClient.getUserFriendlyError(msg);
                        statusLabel.label = friendly;
                        statusLabel.tooltip(IKey.constant(msg));
                        statusLabel.color = 0xff5555;
                    });
                    return null;
                });
            });
            testBtn.w(120).h(20);

            UIElement row = UI.row(6, 0, 20, testBtn, statusLabel).h(20);
            row.w(280);

            return List.of(UIValueFactory.column(row, value));
        });

        UIValueMap.register(mchorse.bbs_mod.copilot.CopilotClearChatsValue.class, (value, parent) -> {
            UILabel statusLabel = UI.label(IKey.constant(""), 0).labelAnchor(0, 0.5f);
            statusLabel.color(Colors.WHITE);

            UIButton clearBtn = new UIButton(L10n.lang("bbs.copilot.clear_chats"), (b) -> {
                mchorse.bbs_mod.copilot.chat.CopilotChatManager.getInstance().clearAllSessions();
                statusLabel.label = L10n.lang("bbs.copilot.chats_cleared");
                statusLabel.color = 0x55ff55;
            });
            clearBtn.w(120).h(20);

            UIElement row = UI.row(6, 0, 20, clearBtn, statusLabel).h(20);
            row.w(280);

            return List.of(UIValueFactory.column(row, value));
        });
    }
}
