package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.client.gui.widget.AnchoredButton;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.OverlayingWindowModal;
import com.yesman.akytheralabs.gui.widget.WidgetContainerModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// A modal for picking the editor (game) language: a title, a scrollable language list, and an
/// `Apply` / `Cancel` footer.
///
/// Selecting a row in the list is a **preview only** — it never reloads the game's resources. Instead
/// the modal re-translates its *own* widgets (the title and the two footer buttons) into the picked
/// language and swaps their text in place, so e.g. picking `한국어` flips the title to `언어 선택` and the
/// buttons to `적용` / `취소`. The translations for the not-yet-active language are pulled from a
/// [ClientLanguage] loaded straight off the resource manager (English as the base fallback layer).
///
/// Only `Apply` commits: it switches [LanguageManager], persists the choice, and reloads resource
/// packs — the standard vanilla language-switch sequence. `Cancel` (or closing) leaves the active
/// language untouched.
public final class LanguageSelectionModal extends OverlayingWindowModal {
    /// Fixed width, like the config modals (Editor Preferences is 320), so this modal lines up with them.
    private static final int POPUP_W = 320;

    // Visual metrics mirror WidgetContainerModal (title bar, footer, content padding, even buttons) so
    // this modal reads identically to Project Settings / Editor Preferences.
    private static final int TITLE_H = 20;
    private static final int FOOTER_H = 28;
    private static final int ROW_H = 18;
    private static final int PAD = 8;
    private static final int BTN_W = WidgetContainerModal.evenButtonWidth(POPUP_W, 2);
    private static final int BTN_H = 16;
    private static final int BTN_GAP = 6;
    private static final int SCROLLBAR_W = 5;

    private final Font font;
    private final LanguageList languageList;
    private final AnchoredButton applyButton;
    private final AnchoredButton cancelButton;

    /// Cache of loaded preview translation tables, keyed by language code, so re-selecting a row (or
    /// re-previewing the initial language) doesn't re-read the resource manager each time.
    private final Map<String, ClientLanguage> previewCache = new HashMap<>();

    /// The title text, re-translated into the previewed language on each selection. Drawn directly in
    /// [#extractWidgetRenderState] (rather than the base modal message) so it can be swapped in place.
    private Component titleComponent;

    /// The currently-previewed (highlighted) language code — what `Apply` would commit.
    private String selectedCode;

    public static void open(Font font) {
        AkytheraEditorLayoutManager.getInstance().openModalWidgetAtCenter(new LanguageSelectionModal(font));
    }

    private LanguageSelectionModal(Font font) {
        super(
            AnchoredWidget.pixel(POPUP_W), AnchoredWidget.fraction(72),
            Component.translatable(LangKeys.GUI_LANGUAGE_SELECT_MODAL_TITLE)
        );

        this.font = font;
        this.titleComponent = Component.translatable(LangKeys.GUI_LANGUAGE_SELECT_MODAL_TITLE);
        this.selectedCode = Minecraft.getInstance().getLanguageManager().getSelected();

        // Footer mirrors the config modals (Project Settings / Editor Preferences): two even-width,
        // bottom-right buttons, with the Cancel button rightmost and the primary action (Apply) to its
        // left — the order WidgetContainerModal produces. Both labels re-translate in place via
        // #previewLanguage. Offsets match WidgetContainerModal's footer (content padding + button gap).
        this.applyButton = AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_APPLY), _ -> applySelected())
            .xUnits(AnchoredWidget.pixel(PAD + BTN_W + 2 * BTN_GAP), AnchoredWidget.pixel(BTN_W))
            .yUnits(AnchoredWidget.pixel((FOOTER_H - BTN_H) / 2), AnchoredWidget.pixel(BTN_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .build();

        this.cancelButton = AnchoredButton.buttonBuilder(font, Component.translatable(LangKeys.GUI_CANCEL),
                _ -> AkytheraEditorLayoutManager.getInstance().closeModal())
            .xUnits(AnchoredWidget.pixel(PAD + BTN_GAP), AnchoredWidget.pixel(BTN_W))
            .yUnits(AnchoredWidget.pixel((FOOTER_H - BTN_H) / 2), AnchoredWidget.pixel(BTN_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .build();

        this.languageList = new LanguageList();
        this.languageList.rebuild();

        // Highlight the active language and seed the preview from it.
        languageList.children().stream()
            .filter(e -> e.code.equals(selectedCode))
            .findFirst()
            .ifPresent(languageList::setSelected);
    }

    /// Re-translates the title and footer buttons into `code` and swaps them in place. No resource
    /// reload — the preview table is loaded once off the resource manager and cached.
    private void previewLanguage(String code) {
        selectedCode = code;

        ClientLanguage lang = previewCache.computeIfAbsent(code, LanguageSelectionModal::loadPreview);

        titleComponent = Component.literal(lang.getOrDefault(LangKeys.GUI_LANGUAGE_SELECT_MODAL_TITLE, "Select Language"));
        applyButton.setMessage(Component.literal(lang.getOrDefault(LangKeys.GUI_APPLY, "Apply")));
        cancelButton.setMessage(Component.literal(lang.getOrDefault(LangKeys.GUI_CANCEL, "Cancel")));
    }

    /// Loads `code`'s full translation table from the resource manager, with `en_us` underneath as the
    /// base fallback layer so keys missing in `code` still resolve (to English). Never switches the
    /// active language.
    private static ClientLanguage loadPreview(String code) {
        LanguageInfo info = Minecraft.getInstance().getLanguageManager().getLanguage(code);
        boolean rtl = info != null && info.bidirectional();
        List<String> codes = code.equals("en_us") ? List.of("en_us") : List.of("en_us", code);
        return ClientLanguage.loadFrom(Minecraft.getInstance().getResourceManager(), codes, rtl);
    }

    /// Commits the previewed language: the standard vanilla switch (set selected, persist, reload
    /// packs), then closes. A no-op switch (same as active) just closes.
    private void applySelected() {
        Minecraft minecraft = Minecraft.getInstance();
        LanguageManager languageManager = minecraft.getLanguageManager();

        if (!selectedCode.equals(languageManager.getSelected())) {
            languageManager.setSelected(selectedCode);
            minecraft.options.languageCode = selectedCode;
            minecraft.options.save();
            minecraft.reloadResourcePacks();
        }

        AkytheraEditorLayoutManager.getInstance().closeModal();
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(languageList, cancelButton, applyButton);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public boolean isMouseOverHeader(double mouseX, double mouseY) {
        return getY() <= mouseY && mouseY <= getY() + TITLE_H;
    }

    @Override
    protected boolean closeOnPressBackground() {
        return true;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        super.mouseClicked(event, doubleClick);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return languageList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
    public void setFocused(final @Nullable GuiEventListener focused) {
        if (getFocused() == focused) {
            return;
        }

        super.setFocused(focused);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Full-screen dimmer — same as the config modals (drawn by OverlayingWindowModal).
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);

        int boxX = getX(), boxY = getY(), boxR = getRight(), boxB = getBottom();

        // Outer border + inner fill — identical palette to WidgetContainerModal.
        graphics.fill(boxX, boxY, boxR, boxB, 0xFF555555);
        graphics.fill(boxX + 1, boxY + 1, boxR - 1, boxB - 1, 0xFF040404);

        // Title bar fill + bottom separator, matching WidgetContainerModal.TitleRow.
        graphics.fill(boxX + 1, boxY + 1, boxR - 1, boxY + TITLE_H, 0xFF0A0A0A);
        graphics.fill(boxX + 1, boxY + TITLE_H, boxR - 1, boxY + TITLE_H + 1, 0xFF555555);
        graphics.text(font, titleComponent, boxX + PAD, boxY + (TITLE_H - font.lineHeight) / 2, 0xFFEEEEEE, false);

        // Footer top separator, matching WidgetContainerModal.FooterRow.
        int footerTop = boxB - FOOTER_H;
        graphics.fill(boxX + 1, footerTop, boxR - 1, footerTop + 1, 0xFF555555);

        languageList.extractRenderState(graphics, mouseX, mouseY, a);
        cancelButton.extractRenderState(graphics, mouseX, mouseY, a);
        applyButton.extractRenderState(graphics, mouseX, mouseY, a);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    private final class LanguageList extends AbstractSelectionList<LanguageList.@NonNull Entry> implements AnchoredWidget {
        LanguageList() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
        }

        void rebuild() {
            clearEntries();

            for (Map.Entry<String, LanguageInfo> e : Minecraft.getInstance().getLanguageManager().getLanguages().entrySet()) {
                addEntry(new Entry(e.getKey(), e.getValue().toComponent()));
            }

            // Effectively call repositionEntries without access widener
            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
            if (!isMouseOver(mx, my)) {
                return false;
            }

            return super.mouseScrolled(mx, my, scrollX, scrollY);
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
            return Mth.clamp((int) ((float) (this.height * this.height) / (float) this.contentHeight()), 8, this.height - 8);
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
            return true;
        }

        @Override
        protected void extractScrollbar(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!scrollable()) return;
            int sbX = scrollBarX(), scrollerH = scrollerHeight(), scrollerY = scrollBarY();
            boolean hovered = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;

            graphics.fill(sbX, getY(), sbX + SCROLLBAR_W, getBottom() - 1, 0xFF1A1A1A);
            graphics.fill(sbX + 1, scrollerY, sbX + SCROLLBAR_W - 1, scrollerY + scrollerH, hovered ? 0xFF6A6A6A : 0xFF505050);
        }

        @Override
        protected void extractSelection(GuiGraphicsExtractor graphics, Entry entry, int outlineColor) {
            int x = entry.getX(), y = entry.getY();
            int right = entry.getContentRight();
            int bottom = y + entry.getContentHeight();
            graphics.fill(x, y, right, bottom, 0xFF0A2833);
            graphics.outline(x, y, entry.getContentWidth(), entry.getHeight(), 0xFF37D5FF);
        }

        @Override
        public void setSelected(@Nullable Entry selected) {
            super.setSelected(selected);

            if (selected != null) {
                previewLanguage(selected.code);
            }
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
        }

        final class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
            private final String code;
            private final Component label;

            Entry(String code, Component label) {
                this.code = code;
                this.label = label;
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                if (getSelected() != this && hovered) {
                    g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF262626);
                }

                g.text(font, label, getX() + 8, getY() + (getHeight() - 8) / 2, getSelected() == this ? 0xFFFFFFFF : 0xFFCCCCCC, false);
            }

            @Override
            public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClicked) {
                setSelected(this);

                if (doubleClicked) {
                    applySelected();
                }

                return true;
            }
        }

        /// ***************************************************************
        /// [AnchoredWidget] implementations                              *
        /// ***************************************************************
        private Unit x1 = AnchoredWidget.pixel(PAD);
        private Unit x2 = AnchoredWidget.pixel(PAD);
        private Unit y1 = AnchoredWidget.pixel(TITLE_H + PAD);
        private Unit y2 = AnchoredWidget.pixel(FOOTER_H);
        private final HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_RIGHT;
        private final VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_BOTTOM;

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
}
