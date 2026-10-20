package com.yesman.akytheralabs.gui.widget;

import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.gui.widget.script.CompileFailure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// A toast popped when the editor's "Compile" action finishes: a success / failure icon, a title, and (on
/// failure) a second line naming the page the error came from, e.g. "Compile Failed" / "Event Graph". Popped
/// through [#show].
public final class CompileResultToast implements Toast {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 32;
    private static final int ICON_SIZE = 20;
    private static final int PAD = 6;
    /// How long the toast stays up before sliding away (scaled by the user's notification multiplier).
    private static final long DURATION_MS = 5000L;

    private static final int COLOR_BG = 0xF0140C24;
    private static final int COLOR_BORDER = 0xFF3A2A6A;
    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_SUBTITLE = 0xFFBFBFBF;

    private final Identifier icon;
    private final Component title;
    @Nullable
    private final Component subtitle;

    private Toast.Visibility wantedVisibility = Toast.Visibility.SHOW;

    private CompileResultToast(Identifier icon, Component title, @Nullable Component subtitle) {
        this.icon = icon;
        this.title = title;
        this.subtitle = subtitle;
    }

    /// Records the outcome of compiling `holder` on it ([DataHolder#markCompileFailed]) and pops the toast,
    /// naming the page `failure` failed on. Null is a success.
    public static void show(DataHolder<?> holder, @Nullable CompileFailure failure) {
        boolean success = failure == null;
        Identifier icon = AkytheraLabs.identifier(success ? "widget/compile_success" : "widget/compile_fail");
        Component title = Component.literal(success ? "Compile Succeeded" : "Compile Failed");

        holder.markCompileFailed(failure);
        Minecraft.getInstance().gui.toastManager().addToast(new CompileResultToast(icon, title, success ? null : failure.page()));
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public Toast.@NonNull Visibility getWantedVisibility() {
        return wantedVisibility;
    }

    @Override
    public void update(@NonNull ToastManager manager, long visibleTime) {
        this.wantedVisibility = visibleTime >= DURATION_MS * manager.getNotificationDisplayTimeMultiplier()
            ? Toast.Visibility.HIDE
            : Toast.Visibility.SHOW;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, @NonNull Font font, long time) {
        g.fill(0, 0, WIDTH, HEIGHT, COLOR_BG);
        g.outline(0, 0, WIDTH, HEIGHT, COLOR_BORDER);

        g.blitSprite(RenderPipelines.GUI_TEXTURED, icon, PAD, (HEIGHT - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE);

        int textX = PAD + ICON_SIZE + PAD;
        if (subtitle == null) {
            g.text(font, title, textX, (HEIGHT - font.lineHeight) / 2, COLOR_TITLE, false);
        } else {
            g.text(font, title, textX, PAD + 1, COLOR_TITLE, false);
            g.text(font, subtitle, textX, PAD + 1 + font.lineHeight + 2, COLOR_SUBTITLE, false);
        }
    }
}