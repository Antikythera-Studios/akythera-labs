package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.entitypatch.component.AnimatorScriptRef;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.FrameResizer;
import com.yesman.akytheralabs.gui.widget.Modal;
import com.yesman.akytheralabs.gui.widget.ResizableFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/// Search-and-pick modal over animator scripts, listing **both** class-based scripts (frozen
/// `SCRIPT_TYPE` entries whose type implements [AnimatorScript]) and data pack script blueprints
/// (the project's entries merged with the loaded world's registry access). The two sources are
/// split under `Class` / `Data` category header rows (mirroring [ModelSelectModal]) so the tags no
/// longer overlap long identifiers. Confirm reports the picked [AnimatorScriptRef]. Opens at the
/// cursor, single-click confirms — mirrors [RegistryLookupModal].
public final class AnimatorScriptSelectModal extends AbstractContainerWidget
    implements Modal, AnchoredWidget, ResizableFrame {

    private static final int TITLE_H = 18;
    private static final int SEARCH_H = 16;
    private static final int ROW_H = 14;
    private static final int SCROLLBAR_W = 5;
    private static final int MODAL_H = 200;

    /// Width of the popped-up page, matched to the [com.yesman.akytheralabs.gui.widget.PopupBox] it drops from.
    private final int modalW;
    private final Font font;
    private final Consumer<AnimatorScriptRef> onConfirm;
    private final AdvancedEditBox<String> searchBox;
    private final EntryList list;

    /// The page is resizable by dragging its frame; the opening size is its floor, and a
    /// [com.yesman.akytheralabs.gui.widget.DropdownSizeMemory] anchor keeps the dragged one for the
    /// next open.
    private final FrameResizer resizer;

    public AnimatorScriptSelectModal(Font font, int width, Consumer<AnimatorScriptRef> onConfirm) {
        super(0, 0, width, MODAL_H,
            Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_ANIM_SCRIPT),
            AbstractScrollArea.defaultSettings(0));

        this.modalW = width;
        this.resizer = new FrameResizer(this, width, MODAL_H);
        this.x2 = AnchoredWidget.pixel(width);
        this.font = font;
        this.onConfirm = onConfirm;
        this.list = new EntryList(collectChoices());
        this.list.rebuild("");

        this.searchBox = AdvancedEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            list::rebuild,
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );
    }

    /// Class scripts first (frozen `SCRIPT_TYPE` entries assignable to [AnimatorScript]), then data
    /// pack script blueprints (project ∪ loaded registry access). Each choice keeps its display id,
    /// source tag, and the [AnimatorScriptRef] to report on confirm.
    private static List<Choice> collectChoices() {
        List<Choice> choices = new ArrayList<>();

        AkytheraRegistries.Frozen.SCRIPT_TYPE.entrySet().stream()
            .filter(e -> AnimatorScript.class.isAssignableFrom(e.getValue().type()))
            .forEach(e -> choices.add(new Choice(
                e.getKey().identifier(), Source.CLASS, new AnimatorScriptRef.ClassScript(e.getValue()))));

        AkytheraProject.getCurrentProject().getMergedEntries(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY)
            .forEach(e -> choices.add(new Choice(
                e.getKey().identifier(), Source.DATA, AnimatorScriptRef.dataPack(e.getKey()))));

        return choices;
    }

    /// The two script sources, each rendered under its own category header row. Declaration order is
    /// listing order (class scripts first, then data pack blueprints).
    private enum Source {
        CLASS(LangKeys.GUI_ANIMATOR_SCRIPT_SELECT_MODAL_CATEGORY_CLASS),
        DATA(LangKeys.GUI_ANIMATOR_SCRIPT_SELECT_MODAL_CATEGORY_DATA);

        private final String categoryKey;

        Source(String categoryKey) {
            this.categoryKey = categoryKey;
        }
    }

    private record Choice(Identifier id, Source source, AnimatorScriptRef value) {}

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        // Cover the screen so we always intercept clicks (close-on-outside).
        return isActive();
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // The frame grip is tested first: it lies just outside the page, where a press would otherwise
        // dismiss the modal.
        if (event.button() == 0 && resizer.mousePressed(event.x(), event.y())) {
            return true;
        }

        boolean overPage = event.x() >= getX() && event.x() < getRight() && event.y() >= getY() && event.y() < getBottom();

        if (!overPage) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
            return true;
        }

        super.mouseClicked(event, doubleClick);

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        return resizer.mouseDragged(event.x(), event.y()) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        return resizer.mouseReleased() || super.mouseReleased(event);
    }

    @Override
    public FrameResizer frameResizer() {
        return resizer;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return list.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            AkytheraEditorLayoutManager.getInstance().closeModal();
            return true;
        }

        GuiEventListener focused = getFocused();

        return focused != null && focused.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event) {
        GuiEventListener focused = getFocused();

        return focused != null && focused.charTyped(event);
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(searchBox, list);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Page background + 1-posX border (no dimmer)
        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
        graphics.fill(getX(), getY(), getRight(), getY() + 1, 0xFF3A3A3A);
        graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(), 0xFF3A3A3A);
        graphics.fill(getX(), getY(), getX() + 1, getBottom(), 0xFF3A3A3A);
        graphics.fill(getRight() - 1, getY(), getRight(), getBottom(), 0xFF3A3A3A);

        // Title bar
        graphics.fill(getX(), getY(), getRight(), getY() + TITLE_H, 0xFF2A2A2A);
        graphics.text(
            font,
            getMessage(),
            getX() + 6,
            getY() + (TITLE_H - font.lineHeight) / 2,
            0xFFCCCCCC,
            false
        );

        searchBox.extractRenderState(graphics, mouseX, mouseY, a);
        list.extractRenderState(graphics, mouseX, mouseY, a);

        resizer.requestCursor(graphics, mouseX, mouseY);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    private final class EntryList extends AbstractSelectionList<EntryList.@NonNull Entry> {
        private final List<Choice> choices;

        EntryList(List<Choice> choices) {
            super(Minecraft.getInstance(), modalW - 2, MODAL_H - TITLE_H - SEARCH_H - 1, 0, ROW_H);
            this.choices = choices;
        }

        void rebuild(String query) {
            clearEntries();

            String lower = query.toLowerCase();

            for (Source source : Source.values()) {
                List<Choice> matches = choices.stream()
                    .filter(choice -> choice.source() == source)
                    .filter(choice -> lower.isEmpty() || choice.id().toString().toLowerCase().contains(lower))
                    .sorted(Comparator.comparing((Choice choice) -> choice.id().toString()))
                    .toList();

                if (matches.isEmpty()) continue;

                addEntry(new TitleEntry(Component.translatable(source.categoryKey)));
                matches.forEach(choice -> addEntry(new ChoiceEntry(choice)));
            }

            setScrollAmount(0.0D);
            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
        }

        @Override
        public int getRowWidth() {
            return getWidth() - SCROLLBAR_W;
        }

        @Override
        public int scrollbarWidth() {
            return SCROLLBAR_W;
        }

        @Override
        protected int scrollBarX() {
            return getRight() - SCROLLBAR_W;
        }

        @Override
        protected void extractListBackground(@NonNull GuiGraphicsExtractor graphics) {
        }

        @Override
        protected void extractListSeparators(@NonNull GuiGraphicsExtractor graphics) {
        }

        @Override
        protected boolean entriesCanBeSelected() {
            return true;
        }

        @Override
        protected void extractScrollbar(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!scrollable()) return;

            int sbX = scrollBarX();
            int scrollerH = scrollerHeight();
            int scrollerY = scrollBarY();
            boolean hovered = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;

            graphics.fill(sbX, getY(), sbX + SCROLLBAR_W, getBottom(), 0xFF1A1A1A);
            graphics.fill(sbX + 1, scrollerY, sbX + SCROLLBAR_W - 1, scrollerY + scrollerH, hovered ? 0xFF6A6A6A : 0xFF505050);
        }

        @Override
        protected void extractSelection(GuiGraphicsExtractor graphics, Entry entry, int outlineColor) {
            int x = entry.getX();
            int y = entry.getY();
            int right = entry.getContentRight();
            int bottom = y + entry.getContentHeight();

            graphics.fill(x, y, right, bottom, 0xFF0A2833);
            graphics.outline(x, y, entry.getContentWidth(), entry.getHeight(), 0xFF37D5FF);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
        }

        abstract static class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
        }

        /// Non-selectable `Class` / `Data` category header, ignoring clicks so it can't be picked.
        final class TitleEntry extends Entry {
            private final Component label;

            TitleEntry(Component label) {
                this.label = label;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF141414);
                g.text(font, label, getX() + 6, getY() + (getHeight() - font.lineHeight) / 2, 0xFF888888, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent ev, boolean dc) {
                return false;
            }
        }

        final class ChoiceEntry extends Entry {
            private final Choice choice;

            ChoiceEntry(Choice choice) {
                this.choice = choice;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                if (getSelected() != this && hovered) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
                }

                g.text(
                    font,
                    Component.literal(choice.id().toString()),
                    getX() + 6,
                    getY() + (getHeight() - font.lineHeight) / 2,
                    getSelected() == this ? 0xFFFFFFFF : 0xFFCCCCCC,
                    false
                );
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClicked) {
                setSelected(this);
                onConfirm.accept(choice.value());
                AkytheraEditorLayoutManager.getInstance().closeModal();

                return true;
            }
        }
    }

    /// ***************************************************************
    /// [AnchoredWidget] implementations                              *
    /// ***************************************************************
    private Unit x1;
    private Unit x2;
    private Unit y1;
    private Unit y2 = AnchoredWidget.pixel(MODAL_H);
    private final AnchoredWidget.HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_WIDTH;
    private final AnchoredWidget.VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_HEIGHT;

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

    /// The constructor's `width` is only the starting size: the opener may widen the modal to its
    /// anchor (see [com.yesman.akytheralabs.AkytheraEditorLayoutManager#openModalWidgetUnder]), so the
    /// list follows the rect the units just produced.
    @Override
    public void relocate(ScreenRectangle screenRectangle) {
        AnchoredWidget.super.relocate(screenRectangle);

        searchBox.relocate(getRectangle());
        list.updateSizeAndPosition(
            getWidth() - 2, getHeight() - TITLE_H - SEARCH_H - 1, getX() + 1, getY() + TITLE_H + SEARCH_H);
    }
}
