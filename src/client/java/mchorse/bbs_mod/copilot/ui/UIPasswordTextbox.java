package mchorse.bbs_mod.copilot.ui;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.function.Consumer;

public class UIPasswordTextbox extends UITextbox {
    public boolean masked = true;

    public UIPasswordTextbox(Consumer<String> callback) {
        super(1000, callback);
        this.delayedInput();
    }

    @Override
    public void render(UIContext context) {
        super.render(context);

        if (this.masked && !this.textbox.getText().isEmpty()) {
            int bg = BBSSettings.inputSurface();
            int x = this.textbox.area.x + 4;
            int y = this.textbox.area.my() - this.textbox.font.getHeight() / 2;
            int w = this.textbox.area.w - 8;
            int h = this.textbox.font.getHeight();

            context.batcher.box(x, y - 1, x + w, y + h + 1, bg);

            int count = Math.min(this.textbox.getText().length(), 40);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                sb.append('*');
            }
            String maskedText = sb.toString();
            context.batcher.textShadow(maskedText, x, y, Colors.WHITE);

            if (this.textbox.isFocused()) {
                float alpha = (float) Math.sin(context.getTickTransition() / 2.0);
                int c = Colors.setA(0xffffff, alpha * 0.5F + 0.5F);
                int cursorX = x + this.textbox.font.getWidth(maskedText);
                context.batcher.box(cursorX, y - 1, cursorX + 1, y + h + 1, c);
            }
        }
    }
}
