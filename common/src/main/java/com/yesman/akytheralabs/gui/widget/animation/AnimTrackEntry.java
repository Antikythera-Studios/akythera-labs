package com.yesman.akytheralabs.gui.widget.animation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;

/// Base row for the [AnimationTrackList].
///
/// Each row is a container entry that owns its own editable child widgets and returns
/// them from [#children] — a decoupled parent→child hierarchy, not one flat widget bag.
/// The list is generic: it doesn't know about notifies/sections/segments. The editor
/// screens build the concrete rows ([NotifyRow], `SectionRow`, `SegmentRow`) and hand
/// them in via the host panel's row builder.
public abstract class AnimTrackEntry extends ContainerObjectSelectionList.Entry<@NonNull AnimTrackEntry> {
    /// Left name-block label.
    protected abstract Component rowLabel();

    /// Draw the part to the RIGHT of the name block (over the frame ruler), inside the track
    /// scissor and after the frame measures are painted. Default: nothing.
    protected void extractTrackArea(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
    }

    /// The inline editor (e.g. a rename box) currently capturing keyboard/char input on
    /// this row, or null when not editing. Lets the host panel route input to it without
    /// knowing the row type. Default: none.
    @Nullable
    protected AbstractWidget activeInlineEditor() {
        return null;
    }

    /// Commit any in-progress inline edit (panel calls this on click-outside or Esc).
    protected void commitInlineEdit() {
    }

    /// Begin this row's inline rename, if it has one. The host panel calls this when the
    /// RENAME key (F2) fires on the selected row. Default: nothing.
    protected void beginInlineEdit() {
    }

    /// Delete this row's currently selected item, if it has one. The host panel calls this
    /// when the DELETE key fires on the selected row. Default: nothing.
    protected void deleteSelected() {
    }

    /// Shared left name-block paint. Rows with an interactive name block (NotifyRow)
    /// override this; section/segment rows keep this simple default.
    @Override
    public void extractContent(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
        Font font = Minecraft.getInstance().font;
        int rowLeft = getContentX();
        int rowTop = getContentY();
        int rowH = getContentHeight();
        int blockRight = rowLeft + AnimationTrackList.NAME_BLOCK_W - (AnimationTrackEditorPanel.DIVIDER_W + 2);

        g.fill(rowLeft, rowTop, blockRight, rowTop + rowH, 0xFF363636);
        g.text(font, rowLabel(), rowLeft + 8, rowTop + (rowH - font.lineHeight) / 2, 0xFFCCCCCC, false);
    }

    @Override
    public @NonNull List<? extends NarratableEntry> narratables() {
        return List.of();
    }
}