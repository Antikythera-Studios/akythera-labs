package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

/// A non-interactive [WidgetContainerModal] showing a spinning "await" icon plus a message — shown
/// while a background task (e.g. an asynchronous save) runs. Open it before the task, then close it
/// via [ModalManager#closeModal] once the task completes.
///
/// The spinner is an [AwaitIconRow], a [WidgetContainerModal.WidgetsContainerRow] painting twelve
/// fading blades rotated around the row centre and advancing one step per frame, through
/// [GuiGraphicsExtractor]'s 2D matrix stack.
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

        ModalManager.getInstance().openModalWidgetAtCenter(modal);
        return modal;
    }

    /// Convenience overload using the default "Saving..." message.
    public static WidgetContainerModal openSaving(Font font) {
        return open(font, Component.translatable(LangKeys.GUI_MESSAGE_SAVING));
    }

    /// Runs `work` off the render thread behind a "Saving..." modal, closing it on the render thread
    /// once the work finishes. The modal blocks input for the duration, which is what makes it safe
    /// to encode holders off-thread — nothing can mutate them mid-write.
    ///
    /// With no editor screen open there is no editor to block, so
    /// the work runs on the caller's thread instead and this returns an already-completed future.
    public static CompletableFuture<Void> runSaving(Runnable work) {
        AkytheraEditorLayoutManager layout = AkytheraEditorLayoutManager.getInstance();
        Minecraft minecraft = Minecraft.getInstance();

        if (!layout.isActive()) {
            work.run();
            return CompletableFuture.completedFuture(null);
        }

        WidgetContainerModal modal = openSaving(minecraft.font);

        return CompletableFuture.runAsync(work).whenCompleteAsync((result, error) -> {
            if (ModalManager.getInstance().getModal() == modal) ModalManager.getInstance().closeModal();
            if (error != null) AkytheraLabs.LOGGER.error("Background save failed", error);
        }, minecraft);
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
