package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.client.api.gui.CloseableWidget;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.ProjectSounds;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import com.yesman.akytheralabs.gui.widget.OverlayingWindowModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/// Picks a sound event. Lists the open project's own events first, then every one the loaded resource
/// packs carry — vanilla's and the installed mods'. A project event outranks a pack event of the same
/// id, so that id is listed once, as the project's. Each row plays its sound from its `▶`.
public final class SoundSelectModal extends OverlayingWindowModal implements CloseableWidget {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 240;
    private static final int TITLE_H = 20;
    private static final int SEARCH_H = 18;
    private static final int LIST_TOP = TITLE_H + SEARCH_H;
    private static final int SCROLLBAR_W = 5;
    private static final int ROW_H = 16;
    private static final int PLAY_W = 14;

    private static final int PROJECT_COLOR = 0xFF8FC7FF;
    private static final int PACK_COLOR = 0xFFCCCCCC;

    private final Font font;
    private final Consumer<SoundEvent> onSoundSelected;
    private final EditorEditBox<String> searchBox;
    private SoundList soundList;

    /// The sound a row's `▶` started, stopped when another starts or the modal closes.
    private @Nullable SoundInstance preview;

    public SoundSelectModal(Font font, Consumer<SoundEvent> onSoundSelected) {
        super(AnchoredWidget.pixel(WIDTH), AnchoredWidget.pixel(HEIGHT), Component.translatable(LangKeys.GUI_SOUND_SELECT_MODAL_TITLE));

        this.font = font;
        this.onSoundSelected = onSoundSelected;

        this.searchBox = EditorEditBox.stringBox(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(TITLE_H), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            query -> soundList.rebuild(query),
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        );
        searchBox.setWidth(WIDTH);
        searchBox.setHeight(SEARCH_H);

        Set<Identifier> projectEvents = new LinkedHashSet<>(ProjectSounds.listProjectEvents());
        List<Identifier> packEvents = Minecraft.getInstance().getSoundManager().getAvailableSounds().stream()
            .filter(eventId -> !projectEvents.contains(eventId))
            .sorted(Comparator.comparing(Identifier::toString))
            .toList();

        this.soundList = new SoundList(projectEvents, packEvents);
    }

    /// The registered event of `eventId`, or a variable-range one for an event a resource pack alone
    /// defines.
    private static SoundEvent soundEventOf(Identifier eventId) {
        SoundEvent registered = BuiltInRegistries.SOUND_EVENT.getValue(eventId);

        return registered != null ? registered : SoundEvent.createVariableRangeEvent(eventId);
    }

    private void stopPreview() {
        if (preview != null) Minecraft.getInstance().getSoundManager().stop(preview);

        preview = null;
    }

    @Override
    public void destroy() {
        stopPreview();
    }

    @Override
    public EditorEditBox<String> searchBox() {
        return searchBox;
    }

    @Override
    public boolean confirmSoleResult() {
        if (soundList.children().size() != 1) return false;

        onSoundSelected.accept(soundEventOf(soundList.children().getFirst().eventId));

        return true;
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(searchBox, soundList);
    }

    @Override
    protected int contentHeight() {
        return HEIGHT;
    }

    @Override
    public void setFocused(final @Nullable GuiEventListener focused) {
        // Re-focusing the already-focused child clears the focus in vanilla's implementation.
        if (getFocused() == focused) {
            return;
        }

        super.setFocused(focused);
    }

    @Override
    public boolean isMouseOverHeader(double mouseX, double mouseY) {
        return mouseX >= (double)getX()
            && mouseY >= (double)getY()
            && mouseX < (double)getRight()
            && mouseY < (double)getY() + TITLE_H;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // Route to children, then consume to prevent click-through to the screen below
        super.mouseClicked(event, doubleClick);

        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return soundList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            ModalManager.getInstance().closeModal();
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
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);

        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), 0xFF3A3A3A);
        graphics.fill(getX(), getY(), getRight(), getY() + TITLE_H, 0xFF2A2A2A);

        graphics.text(
            font,
            getMessage(),
            getX() + (getWidth() - font.width(getMessage())) / 2,
            getY() + (TITLE_H - font.lineHeight) / 2,
            0xFFCCCCCC,
            false
        );

        searchBox.extractRenderState(graphics, mouseX, mouseY, a);
        soundList.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    @Override
    protected boolean closeOnPressBackground() {
        return true;
    }

    private final class SoundList extends AbstractSelectionList<SoundList.@NonNull SoundEntry> implements AnchoredWidget {
        private final Set<Identifier> projectEvents;
        private final List<Identifier> packEvents;

        /// The query the rows are listed for. Null until the first [#rebuild].
        private @Nullable String query;

        SoundList(Set<Identifier> projectEvents, List<Identifier> packEvents) {
            super(Minecraft.getInstance(), WIDTH, HEIGHT - LIST_TOP, LIST_TOP, ROW_H);

            this.projectEvents = projectEvents;
            this.packEvents = packEvents;

            rebuild("");
        }

        /// Shows the events whose id contains `query`, ignoring case. The same query again keeps the
        /// rows and the scroll as they are.
        void rebuild(String query) {
            if (query.equals(this.query)) return;

            this.query = query;
            String lower = query.toLowerCase();
            int rowIndex = 0;

            clearEntries();

            for (Identifier eventId : projectEvents) {
                if (eventId.toString().contains(lower)) addEntry(new SoundEntry(eventId, true, rowIndex++));
            }

            for (Identifier eventId : packEvents) {
                if (eventId.toString().contains(lower)) addEntry(new SoundEntry(eventId, false, rowIndex++));
            }

            setScrollAmount(0);
        }

        @Override
        public int getRowWidth() {
            return getWidth() - SCROLLBAR_WIDTH;
        }

        @Override
        public int scrollbarWidth() {
            return SCROLLBAR_W;
        }

        @Override
        protected int scrollerHeight() {
            return Mth.clamp((int)((float)(this.height * this.height) / (float)this.contentHeight()), 8, this.height - 8);
        }

        @Override
        protected int scrollBarX() {
            return getRight() - scrollbarWidth();
        }

        @Override
        protected void extractListBackground(@NonNull GuiGraphicsExtractor graphics) {
        }

        @Override
        protected void extractListSeparators(@NonNull GuiGraphicsExtractor graphics) {
        }

        @Override
        protected boolean entriesCanBeSelected() {
            return false;
        }

        @Override
        protected void extractScrollbar(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!scrollable()) return;
            int sbX = scrollBarX(), scrollerH = scrollerHeight(), scrollerY = scrollBarY();
            boolean hovered = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;

            graphics.fill(sbX, getY(), sbX + SCROLLBAR_W, getBottom(), 0xFF1A1A1A);
            graphics.fill(sbX + 1, scrollerY, sbX + SCROLLBAR_W - 1, scrollerY + scrollerH, hovered ? 0xFF6A6A6A : 0xFF505050);
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {}

        /// One event: a `▶` playing it, then its id, tinted for a project event.
        final class SoundEntry extends AbstractSelectionList.Entry<@NonNull SoundEntry> {
            private final Identifier eventId;
            private final boolean project;
            private final int rowIndex;

            SoundEntry(Identifier eventId, boolean project, int rowIndex) {
                this.eventId = eventId;
                this.project = project;
                this.rowIndex = rowIndex;
            }

            private boolean isOverPlay(double mouseX) {
                return mouseX < getX() + PLAY_W;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a) {
                boolean playHovered = hovered && isOverPlay(mouseX);
                int bg = hovered && !playHovered
                    ? 0xFF1E4D72
                    : rowIndex % 2 == 0
                        ? 0xFF000000
                        : 0xFF111111;

                g.fill(getX() - 2, getY(), getX() + getWidth(), getY() + getHeight(), bg);
                g.fill(getX() - 2, getY(), getX() + PLAY_W, getY() + getHeight(), playHovered ? 0xFF575757 : 0xFF2A2A2A);

                // ▶, tapering to a point on the right
                int half = 3;
                int left = getX() + PLAY_W / 2 - 2;
                int cy = getY() + getHeight() / 2;

                for (int dy = -half; dy <= half; dy++) {
                    g.fill(left, cy + dy, left + half - Math.abs(dy) + 1, cy + dy + 1, 0xFF8BC24A);
                }

                String label = font.plainSubstrByWidth(eventId.toString(), getWidth() - PLAY_W - 8);
                g.text(font, label, getX() + PLAY_W + 4, getY() + (ROW_H - font.lineHeight) / 2, project ? PROJECT_COLOR : PACK_COLOR, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
                if (isOverPlay(event.x())) {
                    stopPreview();

                    preview = SimpleSoundInstance.forUI(soundEventOf(eventId), 1.0F, 1.0F);
                    Minecraft.getInstance().getSoundManager().play(preview);
                } else {
                    onSoundSelected.accept(soundEventOf(eventId));
                }

                return true;
            }
        }

        // ***************************************************************
        // [AnchoredWidget] implementations
        // ***************************************************************
        private Unit x1 = AnchoredWidget.pixel(0);
        private Unit x2 = AnchoredWidget.pixel(0);
        private Unit y1 = AnchoredWidget.pixel(LIST_TOP);
        private Unit y2 = AnchoredWidget.pixel(0);
        private AnchoredWidget.HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_RIGHT;
        private AnchoredWidget.VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_BOTTOM;

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
        @Override public void setHorizontalAnchorType(HorizontalAnchorType horizontalAnchorType) { this.horizontalAnchorType = horizontalAnchorType; }
        @Override public void setVerticalAnchorType(VerticalAnchorType verticalAnchorType) { this.verticalAnchorType = verticalAnchorType; }
    }
}
