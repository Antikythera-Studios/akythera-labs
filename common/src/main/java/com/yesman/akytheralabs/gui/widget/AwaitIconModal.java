package com.yesman.akytheralabs.gui.widget;

import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

/// A non-interactive [WidgetContainerModal] showing a spinning "await" icon plus a message — shown
/// while a background task (e.g. an asynchronous save) runs. Open it before the task, then close it
/// via [AkytheraEditorLayoutManager#closeModal] once the task completes.
///
/// The spinner is an [AwaitIconRow] (a [WidgetContainerModal.WidgetsContainerRow]) painting twelve
/// fading blades rotated around the row centre, advancing one step per frame — ported from the
/// 1.21.1 `AwaitIconMessageScreen` to 26.1's [GuiGraphicsExtractor] / 2D matrix stack (the old 3D
/// `PoseStack` + `Axis.ZP.rotationDegrees` becomes `pose().rotate(radians)`).
public final class AwaitIconModal {
    private static final int MODAL_WIDTH = 200;
    private static final int ICON_ROW_H = 48;

    private AwaitIconModal() {}

    /// Builds, centres, and opens an await modal carrying `message`, returning it so the caller can
    /// close exactly this modal when its task finishes (rather than whatever happens to be on top).
    public static WidgetContainerModal open(Font font, Component message) {
        WidgetContainerModal modal = WidgetContainerModal
            .builder(font, Component.empty(), MODAL_WIDTH)
            .textLines(message)
            .widgetContainerRow(ICON_ROW_H, new AwaitIconRow())
            .build();

        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(modal);
        return modal;
    }

    /// Convenience overload using the default "Saving..." message.
    public static WidgetContainerModal openSaving(Font font) {
        return open(font, Component.translatable(LangKeys.GUI_MESSAGE_SAVING));
    }

    /// The spinning-blades row. Drawn entirely in [#extractRowContent]; advances its own frame counter
    /// each render so it animates without needing a tick hook.
    private static final class AwaitIconRow extends WidgetContainerModal.WidgetsContainerRow {
        private static final int BLADES = 12;

        private int frame;

        @Override
        protected void extractRowContent(
            WidgetContainerModal popup, GuiGraphicsExtractor g, ScreenRectangle rowRectangle,
            int mouseX, int mouseY, float partialTick
        ) {
            int centerX = rowRectangle.left() + rowRectangle.width() / 2;
            int centerY = rowRectangle.top() + rowRectangle.height() / 2;

            g.pose().pushMatrix();
            g.pose().translate(centerX, centerY);

            for (int i = 0; i < BLADES; i++) {
                int mod = (i * 21 + frame) % 256;
                int color = ((mod << 16) | (mod << 8) | mod) | (254 << 24);

                g.pose().pushMatrix();
                g.pose().rotate((float) Math.toRadians(-30.0 * i));
                g.pose().translate(-1, 0);
                g.fill(0, 11, 2, 18, color);
                g.pose().popMatrix();
            }

            g.pose().popMatrix();

            frame += 3;
        }
    }
}
