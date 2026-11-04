package com.yesman.akytheralabs.gui.panel.animation;

import com.yesman.akythera.client.api.gui.Static;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.akythera.animation.NotifyTrackHolder;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/// Right-hand details pane for the Notify Track editor. Shows the selected
/// mutableNotify or mutableNotify-state's editable fields, driven by [EditorWidgetFactory]
/// and `@EditorEditable` annotations on the holder classes.
public final class NotifyDetailsPanel extends AdjustablePanelManager.PanelArea {
    private static final int INDENT = 8;
    private static final int ROW_H = 20;

    private final PanelHeader header;
    private final WidgetTable table;
    private final EditorWidgetFactory factory;
    private final Font font;

    private NotifyTrackHolder.@Nullable NotifyRef lastRef;

    public NotifyDetailsPanel(Screen owner) {
        this.font = owner.getFont();
        this.header = new PanelHeader(font, Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_DETAILS_TITLE));
        this.table = new WidgetTable(
            owner,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT + 2), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );
        this.factory = new EditorWidgetFactory(font);

        addWidget(header);
        addWidget(table);

        showEmpty();
    }

    public void onSelectionChanged(NotifyTrackHolder.@Nullable NotifyRef ref) {
        if (ref == null) {
            lastRef = null;
            showEmpty();
            return;
        }

        // Fast path: same selection — drag tick or other in-place mutation.
        // Skip the full table teardown and just re-read bound widget values.
        if (ref.equals(lastRef)) {
            table.initialize(false);
            return;
        }

        lastRef = ref;

        switch (ref) {
            case NotifyTrackHolder.NotifyRef.Point point -> {
                header.setMessage(Component.literal(ParseUtils.camelToSpacedPascal(point.mutableNotify().holder.notifyName())));
                fillNotifyProperties(point.mutableNotify(), point.mutableNotify().holder);
                table.initialize(false);
            }
            case NotifyTrackHolder.NotifyRef.State state -> {
                header.setMessage(Component.literal(ParseUtils.camelToSpacedPascal(state.mutableNotifyState().holder.notifyName())));
                fillNotifyProperties(state.mutableNotifyState(), state.mutableNotifyState().holder);
                table.initialize(false);
            }
        }
    }

    /// Populates the table with the annotated fields of the notify's timing and of its type holder.
    private void fillNotifyProperties(Object notifyInstance, Object notifyTypeHolder) {
        table.clearWidgets();

        factory.appendRowsFrom(table, notifyInstance);
        factory.appendRowsFrom(table, notifyTypeHolder);

        table.relocate(getRectangle());
    }

    private void showEmpty() {
        header.setMessage(Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_DETAILS_TITLE));
        table.clearWidgets();
        table.newRow()
            .addWidget(new Static(
            font,
            AnchoredWidget.pixel(INDENT), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_DETAILS_EMPTY)
        ));

        table.relocate(getRectangle());
        table.initialize(false);
    }

    @Override
    protected void onPanelResized(ScreenRectangle rect) {
        header.relocate(rect);
        table.relocate(rect);
    }
}