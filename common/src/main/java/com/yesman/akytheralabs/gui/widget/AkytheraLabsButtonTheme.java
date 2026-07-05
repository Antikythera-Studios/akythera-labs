package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.ButtonTheme;
import com.yesman.akythera.client.gui.widget.common.WidgetTheme;
import com.yesman.akytheralabs.AkytheraLabs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

/// Names are suffixed by '_BUTTON' to avoid crash with [AkytheraLabsEditBoxTheme]
public enum AkytheraLabsButtonTheme implements ButtonTheme {
    AKYTHERA_EDITOR_BUTTON(
        (widget, graphics, _, _, _) -> {
            int color = widget.isHighlighted() ? 0xFF1E4D72
                : widget.isHoveredOrFocused() ? 0xFF333333
                : 0xFF282828;

            graphics.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), color);
        },
        widget -> widget.isHighlighted() ? 0xFFFFFFFF : widget.active ? 0xFFB6B6B6 : 0xFF1A1A1A
    ),

    /// Style for primary action buttons (e.g. "Open"). Always rendered in accent blue.
    AKYTHERA_EDITOR_PRIMARY_BUTTON(
        (widget, graphics, _, _, _) -> {
            int color = widget.isHoveredOrFocused() ? 0xFF2A6EAA : 0xFF1E5080;
            graphics.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), color);
        },
        _ -> 0xFFFFFFFF
    ),

    /// Style for top menu bar title buttons.
    /// Highlighted when its dropdown is open; uses bar background color when idle.
    TOP_MENU_BUTTON(
        (widget, graphics, _, _, _) -> {
            int color = widget.isHighlighted() || widget.isHovered()
                ? 0xFF2E4A6A
                : 0xFF1A1A1A;

            graphics.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), color);
        },
        _ -> 0xFFCCCCCC
    ),

    AKYTHERA_EXPLORER_BUTTON(
        (widget, graphics, _, _, _) -> {
            int color = widget.isActive() ?
                    widget.isHighlighted() || widget.isHovered()
                    ? 0xFF575757
                    : 0xFF383838
                : 0xFF1A1A1A;

            graphics.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), color);
        },
        widget -> widget.isHighlighted() ? 0xFFFFFFFF : widget.active ? 0xFFB6B6B6 : 0xFF747474
    ),

    AKYTHERA_CONTENT_ADD_BUTTON(
        (widget, graphics, _, _, _) -> {
            int color = widget.isHighlighted() || widget.isHovered()
                ? 0xFF575757
                : 0xFF383838;

            graphics.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), color);

            // Draw cross
            int plusColor = 0xFF8BC24A;
            int barThick = 2;
            int barLen = 10;
            int cx = widget.getX() + widget.getWidth() / 2;
            int cy = widget.getY() + widget.getHeight() / 2;

            graphics.fill(cx - barLen / 2, cy - barThick / 2, cx + barLen / 2, cy + barThick / 2, plusColor);
            graphics.fill(cx - barThick / 2, cy - barLen / 2, cx + barThick / 2, cy + barLen / 2, plusColor);
        },
        widget -> widget.isHighlighted() ? 0xFFFFFFFF : widget.active ? 0xFFB6B6B6 : 0xFF1A1A1A
    ),

    /// ▶ Montage Sections: play a sequence from its first section. Drawn right-pointing triangle.
    MONTAGE_SEQ_PLAY_BUTTON(
        (widget, graphics, _, _, _) -> {
            int color = widget.isHighlighted() || widget.isHovered() ? 0xFF575757 : 0xFF383838;
            graphics.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), color);

            int tri = 0xFF8BC24A;
            int h = 4;
            int left = widget.getX() + widget.getWidth() / 2 - 2;
            int cy = widget.getY() + widget.getHeight() / 2;

            // Width per row shrinks toward the top/bottom → tapers to a point on the right.
            for (int dy = -h; dy <= h; dy++) {
                int rowW = h - Math.abs(dy);
                graphics.fill(left, cy + dy, left + rowW + 1, cy + dy + 1, tri);
            }
        },
        _ -> 0xFFFFFFFF
    ),

    /// ✕ Montage Sections: remove a completed (looping) sequence. Drawn cross of two diagonals.
    MONTAGE_SEQ_REMOVE_BUTTON(
        (widget, graphics, _, _, _) -> {
            int color = widget.isHighlighted() || widget.isHovered() ? 0xFF6A2A2A : 0xFF383838;
            graphics.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), color);

            int x = 0xFFD08A8A;
            int r = 4;
            int cx = widget.getX() + widget.getWidth() / 2;
            int cy = widget.getY() + widget.getHeight() / 2;

            for (int d = -r; d <= r; d++) {
                graphics.fill(cx + d, cy + d, cx + d + 1, cy + d + 1, x);
                graphics.fill(cx + d, cy - d, cx + d + 1, cy - d + 1, x);
            }
        },
        _ -> 0xFFFFFFFF
    ),

    /// + Montage Sections: append a new empty sequence. Gray cross, always present at the bottom.
    MONTAGE_SEQ_ADD_BUTTON(
        (widget, graphics, _, _, _) -> {
            int color = widget.isHighlighted() || widget.isHovered() ? 0xFF575757 : 0xFF383838;
            graphics.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), color);

            int plusColor = 0xFFAAAAAA;
            int barThick = 2;
            int barLen = 10;
            int cx = widget.getX() + widget.getWidth() / 2;
            int cy = widget.getY() + widget.getHeight() / 2;

            graphics.fill(cx - barLen / 2, cy - barThick / 2, cx + barLen / 2, cy + barThick / 2, plusColor);
            graphics.fill(cx - barThick / 2, cy - barLen / 2, cx + barThick / 2, cy + barLen / 2, plusColor);
        },
        _ -> 0xFFFFFFFF
    ),

    /// Play (resume) button for the notify-track footer
    ANIM_TRACK_RESUME_BUTTON(
        spriteRenderer(new WidgetSprites(
            AkytheraLabs.identifier("widget/anim_track_resume"),
            AkytheraLabs.identifier("widget/anim_track_resume_disabled"),
            AkytheraLabs.identifier("widget/anim_track_resume")
        )),
        _ -> 0xFFFFFFFF
    ),

    /// Pause button for the notify-track footer
    ANIM_TRACK_PAUSE_BUTTON(
        spriteRenderer(new WidgetSprites(
            AkytheraLabs.identifier("widget/anim_track_pause"),
            AkytheraLabs.identifier("widget/anim_track_pause_disabled"),
            AkytheraLabs.identifier("widget/anim_track_pause")
        )),
        _ -> 0xFFFFFFFF
    ),

    /// Folder-picker button next to the export path field
    EXPORT_PATH_BROWSE_BUTTON(
        spriteRenderer(new WidgetSprites(
            AkytheraLabs.identifier("widget/file_explorer"),
            AkytheraLabs.identifier("widget/file_explorer"),
            AkytheraLabs.identifier("widget/file_explorer")
        )),
        _ -> 0xFFFFFFFF
    ),

    /// Magnifier button of a [PopupBox] — opens the search/selection modal for the bound value
    SEARCH_ITEM_BUTTON(
        spriteRenderer(new WidgetSprites(
            AkytheraLabs.identifier("widget/search_item"),
            AkytheraLabs.identifier("widget/search_item"),
            AkytheraLabs.identifier("widget/search_item")
        )),
        _ -> 0xFFFFFFFF
    );

    private static ButtonTheme.ButtonRenderer spriteRenderer(WidgetSprites sprites) {
        return (widget, graphics, _, _, _) -> {
            Identifier id = sprites.get(widget.active, widget.isHoveredOrFocused());
            graphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                id,
                widget.getX(), widget.getY(),
                widget.getWidth(), widget.getHeight()
            );
        };
    }

    final ButtonRenderer buttonRenderer;
    final Function<AnchoredButton, Integer> fontColorProvider;
    final int id;

    AkytheraLabsButtonTheme(ButtonRenderer buttonRenderer, Function<AnchoredButton, Integer> fontColorProvider) {
        this.buttonRenderer = buttonRenderer;
        this.fontColorProvider = fontColorProvider;
        this.id = WidgetTheme.ENUM_MANAGER.assign(this);
    }

    @Override
    public void renderBackground(AnchoredButton widget, final GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.buttonRenderer.renderBackground(widget, graphics, mouseX, mouseY, a);
    }

    @Override
    public int getFontColor(AnchoredButton widget) {
        return fontColorProvider.apply(widget);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
