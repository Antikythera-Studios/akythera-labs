package com.yesman.akytheralabs.gui.panel.animation;

import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.akythera.animation.AnimationMontageHolder;
import com.yesman.akytheralabs.gui.widget.AdvancedComboBox;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/// "Montage Sections" tab page: edits the section playing orders as stacked sequence rows
/// of [AdvancedComboBox]. Each row is one play sequence:
///
/// > `[▶] combo→combo→combo [✕]` (a looping / complete sequence)
/// > `[▶] combo→combo→[▼ pick…]` (an open sequence — trailing append combo, no ✕ yet)
/// > `[+]` (add-sequence button, always last, on its own line)
///
/// The ▶ play button only renders at a sequence's first line. A sequence that overflows the
/// panel width wraps to indented continuation lines that start with a combo (no ▶) — so the
/// ▶ is the cue distinguishing a *new sequence* from an *overflow wrap*.
public final class MontageSectionsPage extends AbstractContainerWidget implements AnchoredWidget {
    private static final int PAD = 8;
    private static final int COMBO_W = 84;
    private static final int COMBO_H = 16;
    private static final int ICON_W = 16;
    private static final int ARROW_GAP = 16;
    private static final int ROW_GAP = 24;
    /// Continuation (wrapped) lines start at the first-combo column — aligned under the
    /// sequence's first combo, past the ▶ button and its gap. A leading "→" fills the gap.
    private static final int CONT_INDENT = ICON_W + ARROW_GAP;
    private static final int MAX_ROWS = 8;

    private final Font font;
    private final AnimationMontageHolder montage;
    private final Consumer<String> onPlaySequence;

    /// Flat list of every interactive widget (combos + play/remove/add buttons) for
    /// hit-testing, focus and rendering. Rebuilt on each edit.
    private final List<AbstractWidget> widgets = new ArrayList<>();

    /// Per-sequence grouping, parallel to [#widgets], used for layout and arrow drawing.
    private final List<Row> rows = new ArrayList<>();

    @Nullable
    private AbstractWidget addSeqButton;

    public MontageSectionsPage(
        Font font,
        AnimationMontageHolder montage,
        Consumer<String> onPlaySequence,
        Unit x1, Unit x2, Unit y1, Unit y2,
        HorizontalAnchorType horizontalAnchorType,
        VerticalAnchorType verticalAnchorType
    ) {
        super(0, 0, 0, 0, Component.empty(), AbstractScrollArea.defaultSettings(0));

        this.font = font;
        this.montage = montage;
        this.onPlaySequence = onPlaySequence;
        this.x1 = x1;
        this.x2 = x2;
        this.y1 = y1;
        this.y2 = y2;
        this.horizontalAnchorType = horizontalAnchorType;
        this.verticalAnchorType = verticalAnchorType;

        rebuildChain();
    }

    /// Rebuild every sequence row from the live orders + section names. Called on any edit so
    /// the rows always mirror the holder.
    public void rebuildChain() {
        widgets.clear();
        rows.clear();

        List<List<String>> orders = montage.getPlayingOrders();

        for (int si = 0; si < orders.size(); si++) {
            int idx = si;
            List<String> seq = orders.get(si);
            boolean looping = isLooping(seq);
            List<String> candidates = candidatesFor(si);
            Row row = new Row();

            // ▶ play — first section of this sequence; only meaningful when it has one.
            if (!seq.isEmpty()) {
                String first = seq.get(0);
                row.play = makeIconButton(AkytheraLabsButtonTheme.MONTAGE_SEQ_PLAY_BUTTON, () -> onPlaySequence.accept(first));
            }

            // One combo per existing slot.
            for (int i = 0; i < seq.size(); i++) {
                int slot = i;
                row.combos.add(makeCombo(candidates, seq.get(i), picked -> {
                    if (picked == null) seq.remove(slot);
                    else seq.set(slot, picked);
                    rebuildChain();
                }));
            }

            // Looping sequence → ✕ remove; open sequence → trailing append combo.
            if (looping) {
                row.remove = makeIconButton(AkytheraLabsButtonTheme.MONTAGE_SEQ_REMOVE_BUTTON, () -> {
                    orders.remove(idx);
                    rebuildChain();
                });
            } else if (!candidates.isEmpty()) {
                row.combos.add(makeCombo(candidates, null, picked -> {
                    if (picked != null) {
                        seq.add(picked);
                        rebuildChain();
                    }
                }));
            }

            rows.add(row);
            collectWidgets(row);
        }

        // Gray-cross add-sequence button — always present, on its own line.
        addSeqButton = makeIconButton(AkytheraLabsButtonTheme.MONTAGE_SEQ_ADD_BUTTON, () -> {
            orders.add(new ArrayList<>());
            rebuildChain();
        });

        widgets.add(addSeqButton);

        positionRows();
    }

    /// A sequence loops once its last entry repeats an earlier entry (cycle closed).
    private boolean isLooping(List<String> seq) {
        if (seq.size() < 2) return false;

        String last = seq.getLast();

        return seq.indexOf(last) < seq.size() - 1;
    }

    /// Candidates for sequence `si`: every section name, minus the ones any OTHER sequence
    /// (above or below) already uses — a section used elsewhere would break the
    /// one-transition-per-section rule. Same-sequence names stay available so a loop can be
    /// closed by re-picking an earlier section.
    private List<String> candidatesFor(int si) {
        Set<String> avail = new LinkedHashSet<>(montage.getSections().keySet());
        List<List<String>> orders = montage.getPlayingOrders();
        
        for (int j = 0; j < orders.size(); j++) {
            if (j == si) continue;
            avail.removeAll(orders.get(j));
        }
        
        return new ArrayList<>(avail);
    }

    private void collectWidgets(Row row) {
        if (row.play != null) widgets.add(row.play);

        widgets.addAll(row.combos);

        if (row.remove != null) widgets.add(row.remove);
    }

    private AdvancedComboBox<String> makeCombo(Collection<String> names, @Nullable String value, Consumer<String> onChange) {
        AdvancedComboBox<String> box = new AdvancedComboBox<>(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(COMBO_W),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(COMBO_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> value, onChange, MAX_ROWS, Component.empty(), names, s -> s
        );

        box.setValueQuiet(value);

        return box;
    }

    private AnchoredButton makeIconButton(AkytheraLabsButtonTheme theme, Runnable onPress) {
        return AnchoredButton
            .buttonBuilder(font, Component.empty(), _ -> onPress.run())
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(ICON_W))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(COMBO_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .theme(theme)
            .build();
    }

    /// Lay out each sequence: pen starts at the left, ▶ first (sequence start only), then the
    /// combos wrapping onto indented continuation lines when they overflow, then ✕. A fresh
    /// line per sequence; the [+] add button lands on the last line.
    private void positionRows() {
        int left = getX() + PAD;
        int right = getRight() - PAD;
        int y = getY() + PAD;

        for (Row row : rows) {
            int x = left;
            boolean lineHasCombo = false;

            if (row.play != null) {
                place(row.play, x, y, ICON_W);
                x += ICON_W + ARROW_GAP;
            }

            for (AbstractWidget combo : row.combos) {
                if (lineHasCombo && x + COMBO_W > right) {
                    y += ROW_GAP;
                    x = left + CONT_INDENT;
                }

                place(combo, x, y, COMBO_W);
                x += COMBO_W + ARROW_GAP;
                lineHasCombo = true;
            }

            if (row.remove != null) {
                if (lineHasCombo && x + ICON_W > right) {
                    y += ROW_GAP;
                    x = left + CONT_INDENT;
                }

                place(row.remove, x, y, ICON_W);
            }

            y += ROW_GAP;
        }

        if (addSeqButton != null) place(addSeqButton, left, y, ICON_W);
    }

    private void place(AbstractWidget w, int x, int y, int width) {
        w.setX(x);
        w.setY(y);
        w.setWidth(width);
        w.setHeight(COMBO_H);
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return widgets;
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);
        // Re-read live sections/orders so switching to this tab always reflects the latest edits.
        rebuildChain();
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);

        if (montage.getSections().isEmpty()) {
            g.text(font, Component.literal("Add sections in the Sections track first."), getX() + PAD, getY() + PAD, 0xFF888888, false);
        }

        // Arrows between consecutive combos within the same sequence. Same line → arrow sits
        // after the current combo; wrapped line → arrow leads the continuation combo.
        for (Row row : rows) {
            for (int i = 0; i + 1 < row.combos.size(); i++) {
                AbstractWidget cur = row.combos.get(i);
                AbstractWidget next = row.combos.get(i + 1);

                if (next.getY() == cur.getY()) {
                    int gy = cur.getY() + (COMBO_H - font.lineHeight) / 2;
                    g.text(font, Component.literal("→"), cur.getX() + cur.getWidth() + 4, gy, 0xFFAAAAAA, false);
                } else {
                    int gy = next.getY() + (COMBO_H - font.lineHeight) / 2;
                    g.text(font, Component.literal("→"), next.getX() - font.width("→") - 4, gy, 0xFFAAAAAA, false);
                }
            }
        }

        for (AbstractWidget widget : widgets) {
            widget.extractRenderState(g, mouseX, mouseY, a);
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        for (AbstractWidget widget : widgets) {
            if (widget.isMouseOver(event.x(), event.y()) && widget.mouseClicked(event, doubleClick)) {
                setFocused(widget);
                return true;
            }
        }

        return false;
    }

    @Override
    public void setFocused(final @Nullable GuiEventListener focused) {
        if (getFocused() == focused) return;
        super.setFocused(focused);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput narrationElementOutput) {
    }

    /// One sequence's widgets: optional ▶ play, its combos, optional ✕ remove.
    private static final class Row {
        @Nullable AbstractWidget play;
        final List<AbstractWidget> combos = new ArrayList<>();
        @Nullable AbstractWidget remove;
    }

    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2;
    private final HorizontalAnchorType horizontalAnchorType;
    private final VerticalAnchorType verticalAnchorType;

    @Override public Unit getX1() { return x1; }
    @Override public Unit getX2() { return x2; }
    @Override public Unit getY1() { return y1; }
    @Override public Unit getY2() { return y2; }
    @Override public void setX1(Unit unit) { x1 = unit; }
    @Override public void setX2(Unit unit) { x2 = unit; }
    @Override public void setY1(Unit unit) { y1 = unit; }
    @Override public void setY2(Unit unit) { y2 = unit; }
    @Override public HorizontalAnchorType getHorizontalAnchorType() { return horizontalAnchorType; }
    @Override public VerticalAnchorType getVerticalAnchorType() { return verticalAnchorType; }
}