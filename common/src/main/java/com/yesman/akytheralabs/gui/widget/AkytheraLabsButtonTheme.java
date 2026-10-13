package com.yesman.akytheralabs.gui.widget;

import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.ButtonTheme;
import com.yesman.akythera.client.api.gui.common.WidgetTheme;
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
            int color = !widget.active ? 0xFF1A1A1A
                : widget.isHighlighted() ? 0xFF1E4D72
                : widget.isHoveredOrFocused() ? 0xFF333333
                : 0xFF282828;

            graphics.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), color);
        },
        widget -> !widget.active ? 0xFF5A5A5A : widget.isHighlighted() ? 0xFFFFFFFF : 0xFFB6B6B6
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

    /// ■ Stop button for the sound play track. Drawn square.
    SOUND_TRACK_STOP_BUTTON(
        (widget, graphics, _, _, _) -> {
            int color = widget.isHighlighted() || widget.isHovered() ? 0xFF575757 : 0xFF383838;
            graphics.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), color);

            int half = 3;
            int cx = widget.getX() + widget.getWidth() / 2;
            int cy = widget.getY() + widget.getHeight() / 2;

            graphics.fill(cx - half, cy - half, cx + half, cy + half, 0xFFD0D0D0);
        },
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
    ),

    /// Translate mode of the [GizmoComponent] overlay
    GIZMO_TRANSLATE_BUTTON(
        iconBoxRenderer(new WidgetSprites(
            AkytheraLabs.identifier("widget/translate"),
            AkytheraLabs.identifier("widget/translate_highlight")
        )),
        _ -> 0xFFFFFFFF
    ),

    /// Rotate mode of the [GizmoComponent] overlay
    GIZMO_ROTATE_BUTTON(
        iconBoxRenderer(new WidgetSprites(
            AkytheraLabs.identifier("widget/rotate"),
            AkytheraLabs.identifier("widget/rotate_highlight")
        )),
        _ -> 0xFFFFFFFF
    ),

    /// Scale mode of the [GizmoComponent] overlay
    GIZMO_SCALE_BUTTON(
        iconBoxRenderer(new WidgetSprites(
            AkytheraLabs.identifier("widget/scale"),
            AkytheraLabs.identifier("widget/scale_highlight")
        )),
        _ -> 0xFFFFFFFF
    ),

    /// Transform basis of the [GizmoComponent] overlay — one button carrying the icon of the basis
    /// currently in use
    GIZMO_ORIENTATION_BUTTON(
        iconToggleRenderer(
            AkytheraLabs.identifier("widget/local_space"),
            AkytheraLabs.identifier("widget/world_space")
        ),
        _ -> 0xFFFFFFFF
    ),

    /// Wireframe view of the weight painter
    CLOTH_WIREFRAME_BUTTON(
        iconBoxRenderer(new WidgetSprites(
            AkytheraLabs.identifier("widget/wireframe"),
            AkytheraLabs.identifier("widget/wireframe")
        )),
        _ -> 0xFFFFFFFF
    ),

    /// Weight-paint view of the weight painter
    WEIGHT_PAINT_BUTTON(
        iconBoxRenderer(new WidgetSprites(
            AkytheraLabs.identifier("widget/weight_paint"),
            AkytheraLabs.identifier("widget/weight_paint")
        )),
        _ -> 0xFFFFFFFF
    ),

    /// Textured view of the weight painter
    CLOTH_TEXTURED_BUTTON(
        iconBoxRenderer(new WidgetSprites(
            AkytheraLabs.identifier("widget/textured"),
            AkytheraLabs.identifier("widget/textured")
        )),
        _ -> 0xFFFFFFFF
    );

    /// Side of the icon drawn by [#iconBoxRenderer] and [#iconToggleRenderer], in widget pixels.
    private static final int ICON = 16;

    /// Mono-tone box behind a [GizmoComponent] overlay button, keyed on its highlighted state.
    private static int boxColor(AkytheraButton widget) {
        return widget.isHighlighted() ? 0xFF575757
            : widget.isHoveredOrFocused() ? 0xFF3F3F3F
            : 0xFF282828;
    }

    /// Box behind a toggle-style overlay button: dark while disabled, else keyed on hover.
    private static int toggleBoxColor(AkytheraButton widget) {
        return !widget.active ? 0xFF1A1A1A
            : widget.isHoveredOrFocused() ? 0xFF3F3F3F
            : 0xFF282828;
    }

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

    /// [#boxColor] box with an [#ICON]-sized icon centered in it, both keyed on the highlighted state.
    private static ButtonTheme.ButtonRenderer iconBoxRenderer(WidgetSprites sprites) {
        return (widget, graphics, _, _, _) -> {
            graphics.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), boxColor(widget));
            blitIcon(graphics, widget, sprites.get(widget.active, widget.isHighlighted()));
        };
    }

    /// [#toggleBoxColor] box carrying `on` while the widget is highlighted and `off` otherwise — the
    /// two states of one button, not two buttons.
    private static ButtonTheme.ButtonRenderer iconToggleRenderer(Identifier off, Identifier on) {
        return (widget, graphics, _, _, _) -> {
            graphics.fill(widget.getX(), widget.getY(), widget.getX() + widget.getWidth(), widget.getY() + widget.getHeight(), toggleBoxColor(widget));
            blitIcon(graphics, widget, widget.isHighlighted() ? on : off);
        };
    }

    /// Draws `icon` at [#ICON] size, centered in the widget.
    private static void blitIcon(GuiGraphicsExtractor graphics, AkytheraButton widget, Identifier icon) {
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            icon,
            widget.getX() + (widget.getWidth() - ICON) / 2,
            widget.getY() + (widget.getHeight() - ICON) / 2,
            ICON, ICON
        );
    }

    final ButtonRenderer buttonRenderer;
    final Function<AkytheraButton, Integer> fontColorProvider;
    final int id;

    AkytheraLabsButtonTheme(ButtonRenderer buttonRenderer, Function<AkytheraButton, Integer> fontColorProvider) {
        this.buttonRenderer = buttonRenderer;
        this.fontColorProvider = fontColorProvider;
        this.id = WidgetTheme.ENUM_MANAGER.assign(this);
    }

    @Override
    public void renderBackground(AkytheraButton widget, final GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.buttonRenderer.renderBackground(widget, graphics, mouseX, mouseY, a);
    }

    @Override
    public int getFontColor(AkytheraButton widget) {
        return fontColorProvider.apply(widget);
    }

    @Override
    public int universalOrdinal() {
        return id;
    }
}
