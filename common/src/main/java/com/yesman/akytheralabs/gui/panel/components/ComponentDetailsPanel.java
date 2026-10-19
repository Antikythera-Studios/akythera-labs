package com.yesman.akytheralabs.gui.panel.components;

import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/// Right-hand "Details" panel of the entity-patch editor: shows the focused component's [EditorEditable]
/// fields as [EditorWidgetFactory]-generated rows (asset accessors and registry keys become search
/// PopupBoxes, booleans become checkboxes, ...). Nested editor objects and `List` fields render inline, one
/// indent level per depth — the rendering (and list add/remove history) lives in the factory; this panel
/// supplies the rebuild callback and the placeholder look (a centered grey "Details" label when nothing is
/// focused).
public final class ComponentDetailsPanel extends AdjustablePanelManager.PanelArea {
    private static final Component TITLE = Component.literal("Details");
    private static final int ROW_H = 20;

    private final Font font;
    private final PanelHeader header;
    private final WidgetTable table;
    private final EditorWidgetFactory factory;

    /// Run after the rows re-render themselves — a list add or remove and its undo. What sweeps an
    /// override back off a patch once it matches what it overrides.
    private final Runnable afterChange;

    @Nullable
    private Object shown;

    /// What [#shown] hangs off, handed to the factory as the rows' context and kept for the rebuilds.
    @Nullable
    private Object shownContext;

    /// The component [#shown] overrides, filling the rows it leaves unset. Kept for the rebuilds.
    @Nullable
    private Object shownInherited;

    /// Reported before the first value written into [#shown] lands. Kept for the rebuilds.
    @Nullable
    private Runnable shownOnWrite;

    @Nullable
    private ScreenRectangle lastRect;

    /// For an editor with no patch inheritance, where no override can fall back to nothing.
    public ComponentDetailsPanel(Font font, Screen parentScreen) {
        this(font, parentScreen, () -> {});
    }

    public ComponentDetailsPanel(Font font, Screen parentScreen, Runnable afterChange) {
        this.font = font;
        this.afterChange = afterChange;
        this.header = new PanelHeader(font, TITLE);
        this.factory = new EditorWidgetFactory(font);
        this.table = new WidgetTable(
            parentScreen,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        addWidget(header);
        addWidget(table);
    }

    /// Hands the rows the blueprint holder they belong to, see [EditorWidgetFactory#root].
    public ComponentDetailsPanel root(final Object root) {
        factory.root(root);
        return this;
    }

    /// Hands the rows a viewer, see [EditorWidgetFactory#viewer].
    public ComponentDetailsPanel viewer(final Model3DViewer viewer) {
        factory.viewer(viewer);
        return this;
    }

    /// Fills the table with `componentHolder`'s editor rows, or clears back to the placeholder
    /// when null (no component focused / component without editor support).
    public void showComponent(@Nullable Object componentHolder) {
        showComponent(componentHolder, null);
    }

    /// Variant carrying what the shown holder hangs off — its host in the component tree. Rows whose
    /// candidates come from their surroundings read it ([EditorWidgetFactory#fillWidgetTable]), which
    /// is how a bone socket lists the bones of its parent component's armature.
    public void showComponent(@Nullable Object componentHolder, @Nullable Object context) {
        showComponent(componentHolder, context, null, null);
    }

    /// Variant carrying the component this one overrides — its values are what the rows grey against,
    /// see [EditorEditable#inheritable] — and the hook that attaches `componentHolder` once something
    /// is written into it.
    public void showComponent(
        @Nullable Object componentHolder, @Nullable Object context, @Nullable Object inherited, @Nullable Runnable onWrite
    ) {
        shown = componentHolder;
        shownContext = context;
        shownInherited = inherited;
        shownOnWrite = onWrite;

        if (componentHolder == null) {
            table.clearWidgets();
        } else {
            factory.fillWidgetTable(
                table, componentHolder, () -> refreshIfShown(componentHolder), context, inherited, onWrite);
        }

        if (lastRect != null) table.relocate(lastRect);
    }

    /// Fills the table with the rows of every slot, or clears back to the placeholder when `slots` is
    /// empty. An element added to or removed from a slot's value re-fills the rows while they are still
    /// the ones on display.
    public void showSlots(List<? extends EditorWidgetFactory.ValueSlot> slots) {
        shown = slots.isEmpty() ? null : slots;
        shownContext = null;
        shownInherited = null;
        shownOnWrite = null;

        if (slots.isEmpty()) {
            table.clearWidgets();
        } else {
            factory.fillSlots(table, slots, () -> {
                if (shown == slots) showSlots(slots);
            });
        }

        if (lastRect != null) table.relocate(lastRect);
    }

    /// Fills the table through `fill`, which appends its own rows. [#isShowing] answers for `owner` until
    /// something else is shown.
    public void showRows(Object owner, Consumer<WidgetTable> fill) {
        shown = owner;
        shownContext = null;
        shownInherited = null;
        shownOnWrite = null;

        table.clearWidgets();
        fill.accept(table);
        table.initialize(false);

        if (lastRect != null) table.relocate(lastRect);
    }

    /// Whether the rows on display are the ones [#showRows] filled for `owner`.
    public boolean isShowing(Object owner) {
        return shown == owner;
    }

    /// Section add/remove (and its undo/redo replay) rebuilds the rows only when this holder is
    /// still the one on display.
    private void refreshIfShown(Object owner) {
        if (shown != owner) return;

        showComponent(owner, shownContext, shownInherited, shownOnWrite);
        afterChange.run();
    }

    @Override
    protected void onPanelResized(ScreenRectangle screenRectangle) {
        lastRect = screenRectangle;
        header.relocate(screenRectangle);
        table.relocate(screenRectangle);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(getX(), getY(), getRight(), getBottom(), bgColor);
        header.extractRenderState(graphics, mouseX, mouseY, a);
        table.extractRenderState(graphics, mouseX, mouseY, a);

        if (shown == null) {
            int bodyTop = getY() + PanelHeader.DEFAULT_HEADER_HEIGHT;
            int cx = getX() + (getWidth() - font.width(TITLE)) / 2;
            int cy = bodyTop + (getBottom() - bodyTop - font.lineHeight) / 2;
            graphics.text(font, TITLE, cx, cy, 0xFF666666, false);
        }
    }
}