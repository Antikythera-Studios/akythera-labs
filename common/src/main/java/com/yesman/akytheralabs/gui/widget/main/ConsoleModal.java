package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.CheckBox;
import com.yesman.akythera.client.api.gui.ClampedNumberBox;
import com.yesman.akythera.client.api.gui.CloseableWidget;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.config.AkytheraLabsConfig;
import com.yesman.akytheralabs.editor.debug.EditorLogBuffer;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import com.yesman.akytheralabs.gui.widget.OverlayingWindowModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.apache.logging.log4j.Level;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The editor's output console: every log line [EditorLogBuffer] holds, filtered by level through the
/// [CheckBox] row under the title.
///
/// Rows are rebuilt when the buffer's revision moves, when a filter box is pressed, or when the wrap
/// width changes. A list sitting at the bottom stays pinned there as new lines arrive.
///
/// Text drag-selects across rows and copies to the OS clipboard, addressed by [EditorLogBuffer.Line]
/// id so a rebuild leaves the selection where the user put it.
public final class ConsoleModal extends OverlayingWindowModal implements CloseableWidget {
    /// Left to right in the filter row. Nothing below [Level#DEBUG] reaches the buffer.
    private static final List<Level> LEVELS = List.of(Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR, Level.FATAL);

    private static final int WIDTH_FRACTION = 70;
    private static final int HEIGHT_FRACTION = 70;
    private static final int TITLE_H = 20;
    private static final int FILTER_H = 20;
    private static final int FOOTER_H = 28;
    private static final int ROW_H = 10;
    private static final int PAD = 8;
    private static final int CHECK_H = 12;
    private static final int CHECK_GAP = 10;
    private static final int BTN_W = 60;
    private static final int OPEN_LOGS_BTN_W = 100;
    private static final int BTN_H = 16;
    private static final int BTN_GAP = 6;
    private static final int BUFFER_BOX_W = 80;
    private static final int BUFFER_BOX_H = 20;
    private static final int MIN_BUFFER = 100;
    private static final int MAX_BUFFER = 100000;
    private static final int BUFFER_STEP = 100;
    private static final int SCROLLBAR_W = 5;
    private static final int SEARCH_W = 150;
    private static final int SEARCH_H = 14;
    private static final int NAV_BTN_W = 14;
    private static final int NAV_GAP = 3;

    /// Room reserved at the right of the filter row for the `current/total` readout.
    private static final int COUNTER_W = 54;

    /// Tint of the `[time] [thread/LEVEL] ` stamp, and of each level's [CheckBox].
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int WARN_COLOR = 0xFFFFCC00;
    private static final int ERROR_COLOR = 0xFFFF5555;

    /// Everything after the stamp, whatever the level.
    private static final int BODY_COLOR = 0xFFBBBBBB;
    private static final int SELECTION_COLOR = 0xFF2F5C8F;
    private static final int COUNTER_COLOR = 0xFFAAAAAA;

    /// Behind a search hit, and behind the one [#step] is parked on.
    private static final int MATCH_COLOR = 0xFF4F4620;
    private static final int CURRENT_MATCH_COLOR = 0xFFA06A10;

    /// Match marks on the scrollbar, which need to read against the track rather than behind text.
    private static final int MATCH_TICK_COLOR = 0xFFC8A000;
    private static final int CURRENT_TICK_COLOR = 0xFFFFFFFF;

    private final Font font;
    private final Component bufferLabel = Component.translatable(LangKeys.GUI_CONSOLE_MODAL_BUFFER_SIZE);
    private final Set<Level> enabledLevels = new HashSet<>();
    private final List<AbstractWidget> children = new ArrayList<>();
    private final LogList logList;

    /// Buffer revision the rows were built from. `-1` forces a rebuild on the next frame.
    private int builtVersion = -1;

    /// Wrap width the rows were built at, so a resize re-wraps them.
    private int builtWidth = -1;

    /// Set by a keystroke in the search box: the rebuild it forces is followed by a jump to the match
    /// nearest where the view already sits.
    private boolean pendingSearchFocus;

    public static void open(final Font font) {
        ModalManager.getInstance().openModalWidgetAtCenter(new ConsoleModal(font));
    }

    private ConsoleModal(final Font font) {
        super(
            AnchoredWidget.fraction(WIDTH_FRACTION), AnchoredWidget.fraction(HEIGHT_FRACTION),
            Component.translatable(LangKeys.GUI_CONSOLE_MODAL_TITLE)
        );

        this.font = font;
        this.logList = new LogList();

        int offsetX = PAD;

        for (Level level : LEVELS) {
            Component label = Component.literal(level.name());
            int boxWidth = CHECK_H + 4 + font.width(label);

            CheckBox checkBox = new CheckBox(
                font,
                AnchoredWidget.pixel(offsetX), AnchoredWidget.pixel(boxWidth),
                AnchoredWidget.pixel(TITLE_H + (FILTER_H - CHECK_H) / 2), AnchoredWidget.pixel(CHECK_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                () -> enabledLevels.contains(level),
                checked -> {
                    if (checked) {
                        enabledLevels.add(level);
                    } else {
                        enabledLevels.remove(level);
                    }

                    builtVersion = -1;
                },
                label,
                true
            );

            enabledLevels.add(level);
            checkBox.valueSetter().accept(true);
            checkBox.setColor(levelColor(level));
            children.add(checkBox);

            offsetX += boxWidth + CHECK_GAP;
        }

        children.add(EditorEditBox.stringBox(
            font,
            AnchoredWidget.pixel(PAD + COUNTER_W + 2 * (NAV_BTN_W + NAV_GAP) + NAV_GAP), AnchoredWidget.pixel(SEARCH_W),
            AnchoredWidget.pixel(TITLE_H + (FILTER_H - SEARCH_H) / 2), AnchoredWidget.pixel(SEARCH_H),
            AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> "",
            query -> {
                logList.setQuery(query);
                builtVersion = -1;
                pendingSearchFocus = true;
            },
            Component.translatable(LangKeys.GUI_MESSAGE_EMPTY_SEARCHBAR)
        ));

        children.add(navButton(font, LocalizedMessage.handwritten("▲"), PAD + COUNTER_W + 2 * NAV_BTN_W + NAV_GAP, -1));
        children.add(navButton(font, LocalizedMessage.handwritten("▼"), PAD + COUNTER_W + NAV_BTN_W, 1));

        children.add(logList);

        ClampedNumberBox<Integer> bufferBox = new ClampedNumberBox<>(
            font,
            AnchoredWidget.pixel(PAD + font.width(bufferLabel) + 6), AnchoredWidget.pixel(BUFFER_BOX_W),
            AnchoredWidget.pixel((FOOTER_H - BUFFER_BOX_H) / 2), AnchoredWidget.pixel(BUFFER_BOX_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT,
            AkytheraLabsConfig::getConsoleBufferSize,
            value -> {
                AkytheraLabsConfig.setConsoleBufferSize(value);
                EditorLogBuffer.getInstance().setCapacity(value);
            },
            Component.empty(),
            true,
            MIN_BUFFER, MAX_BUFFER, BUFFER_STEP, -BUFFER_STEP,
            Integer::parseInt, String::valueOf,
            Integer::compareTo, Integer::sum
        );

        bufferBox.setValue(AkytheraLabsConfig.getConsoleBufferSize());
        children.add(bufferBox);

        children.add(AkytheraButton
            .buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CONSOLE_MODAL_OPEN_LOGS), () -> openLogFolder())
            .xUnits(AnchoredWidget.pixel(PAD + 3 * BTN_GAP + 2 * BTN_W), AnchoredWidget.pixel(OPEN_LOGS_BTN_W))
            .yUnits(AnchoredWidget.pixel((FOOTER_H - BTN_H) / 2), AnchoredWidget.pixel(BTN_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .build());

        children.add(AkytheraButton
            .buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CONSOLE_MODAL_CLEAR), () -> EditorLogBuffer.getInstance().clear())
            .xUnits(AnchoredWidget.pixel(PAD + 2 * BTN_GAP + BTN_W), AnchoredWidget.pixel(BTN_W))
            .yUnits(AnchoredWidget.pixel((FOOTER_H - BTN_H) / 2), AnchoredWidget.pixel(BTN_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .build());

        children.add(AkytheraButton
            .buttonBuilder(font, LocalizedMessage.translatable(LangKeys.GUI_CLOSE), () -> ModalManager.getInstance().closeModal())
            .xUnits(AnchoredWidget.pixel(PAD + BTN_GAP), AnchoredWidget.pixel(BTN_W))
            .yUnits(AnchoredWidget.pixel((FOOTER_H - BTN_H) / 2), AnchoredWidget.pixel(BTN_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.BOTTOM_HEIGHT)
            .build());
    }

    /// A filter-row arrow that steps the current match by `delta`.
    private AkytheraButton navButton(final Font font, final LocalizedMessage arrow, final int rightOffset, final int delta) {
        return AkytheraButton
            .buttonBuilder(font, arrow, () -> logList.step(delta))
            .xUnits(AnchoredWidget.pixel(rightOffset), AnchoredWidget.pixel(NAV_BTN_W))
            .yUnits(AnchoredWidget.pixel(TITLE_H + (FILTER_H - SEARCH_H) / 2), AnchoredWidget.pixel(SEARCH_H))
            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
            .build();
    }

    private static int levelColor(final Level level) {
        if (Level.WARN.equals(level)) {
            return WARN_COLOR;
        }

        if (Level.ERROR.equals(level) || Level.FATAL.equals(level)) {
            return ERROR_COLOR;
        }

        return TEXT_COLOR;
    }

    /// Opens the directory holding `debug.log`. [FileSystemHelper#openInFileExplorer] resolves the
    /// parent, so a missing `debug.log` still opens `logs`.
    private static void openLogFolder() {
        Path debugLog = Minecraft.getInstance().gameDirectory.toPath().resolve("logs").resolve("debug.log");

        FileSystemHelper.openInFileExplorer(debugLog);
    }

    /// Breaks `text` at the character the width runs out on, so the rows concatenate back into `text`
    /// and each one can carry an exact offset into it.
    private static List<String> wrap(final Font font, final String text, final int width) {
        List<String> rows = new ArrayList<>();
        String rest = text;

        while (true) {
            String head = font.plainSubstrByWidth(rest, width);

            if (head.isEmpty()) {
                rows.add(rest);
                return rows;
            }

            rows.add(head);
            rest = rest.substring(head.length());

            if (rest.isEmpty()) {
                return rows;
            }
        }
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return children;
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
        return logList.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.isEscape()) {
            ModalManager.getInstance().closeModal();
            return true;
        }

        GuiEventListener focused = getFocused();

        if (focused != null && focused != logList && focused.keyPressed(event)) {
            return true;
        }

        if (EditorKeyMappings.COPY.matches(event)) {
            String selected = logList.selectedText();

            if (!selected.isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(selected);
            }

            return true;
        }

        if (EditorKeyMappings.SELECT_ALL.matches(event)) {
            logList.selectAll();
            return true;
        }

        return false;
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
        int version = EditorLogBuffer.getInstance().version();
        int wrapWidth = logList.wrapWidth();

        if (version != builtVersion || wrapWidth != builtWidth) {
            if (wrapWidth != builtWidth) {
                logList.dropWrapCache();
            }

            builtVersion = version;
            builtWidth = wrapWidth;
            logList.rebuild();

            if (pendingSearchFocus) {
                pendingSearchFocus = false;
                logList.focusNearestMatch();
            }
        }

        // Full-screen dimmer, drawn by OverlayingWindowModal
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);

        int boxX = getX(), boxY = getY(), boxR = getRight(), boxB = getBottom();

        graphics.fill(boxX, boxY, boxR, boxB, 0xFF555555);
        graphics.fill(boxX + 1, boxY + 1, boxR - 1, boxB - 1, 0xFF040404);

        graphics.fill(boxX + 1, boxY + 1, boxR - 1, boxY + TITLE_H, 0xFF0A0A0A);
        graphics.fill(boxX + 1, boxY + TITLE_H, boxR - 1, boxY + TITLE_H + 1, 0xFF555555);
        graphics.text(font, getMessage(), boxX + PAD, boxY + (TITLE_H - font.lineHeight) / 2, 0xFFEEEEEE, false);

        graphics.fill(boxX + 1, boxY + TITLE_H + FILTER_H, boxR - 1, boxY + TITLE_H + FILTER_H + 1, 0xFF555555);

        String counter = logList.currentMatchNumber() + "/" + logList.matchCount();
        graphics.text(
            font, counter,
            boxR - PAD - font.width(counter), boxY + TITLE_H + (FILTER_H - font.lineHeight) / 2,
            COUNTER_COLOR, false
        );

        int footerTop = boxB - FOOTER_H;
        graphics.fill(boxX + 1, footerTop, boxR - 1, footerTop + 1, 0xFF555555);
        graphics.text(font, bufferLabel, boxX + PAD, footerTop + (FOOTER_H - font.lineHeight) / 2, 0xFFAAAAAA, false);

        for (AbstractWidget child : children) {
            child.extractRenderState(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
    }

    /// Persists the buffer size the footer box may have changed.
    @Override
    public void destroy() {
        AkytheraLabsConfig.saveConfigValues();
    }

    /// A caret in the console text: the line it sits on, and a character offset into that line.
    private record TextPoint(long lineId, int charIndex) {
        boolean isBefore(TextPoint other) {
            return lineId != other.lineId ? lineId < other.lineId : charIndex <= other.charIndex;
        }
    }

    /// An ordered caret pair.
    private record TextRange(TextPoint from, TextPoint to) {}

    /// One search hit, in source-line coordinates. `row` is the index of the wrapped row `from` falls
    /// on, which is what places the hit on the scrollbar and what [LogList#step] scrolls to.
    private record Match(long lineId, int from, int to, int row) {}

    /// `haystack.indexOf(needle, from)`, ignoring case without copying either string.
    private static int indexOfIgnoreCase(final String haystack, final String needle, final int from) {
        int limit = haystack.length() - needle.length();

        for (int i = from; i <= limit; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }

        return -1;
    }

    private final class LogList extends AbstractSelectionList<LogList.@NonNull Entry> implements AnchoredWidget {
        /// The lines the rows were built from, in id order.
        private List<EditorLogBuffer.Line> visibleLines = List.of();

        /// Wrapped rows per line id, carried across rebuilds so a rebuild only wraps lines it has not
        /// seen. Holds an entry for exactly the lines of the last rebuild.
        private Map<Long, List<String>> wrapCache = new HashMap<>();

        private @Nullable TextPoint selectionAnchor;
        private @Nullable TextPoint selectionCursor;
        private boolean selecting;

        private boolean draggingScrollbar;

        /// Pixels between the pointer and the scroller's top, taken when the drag started.
        private double scrollGrab;

        /// Search hits over [#visibleLines], in row order, rebuilt with the rows.
        private final List<Match> matches = new ArrayList<>();

        private String query = "";

        /// Index into [#matches], or `-1` when there is nothing to step through.
        private int currentMatch = -1;

        /// Identity of the match [#currentMatch] points at. A rebuild re-finds it by this rather than
        /// keeping the index, which shifts as lines roll off the front.
        private long currentLineId = -1;
        private int currentFrom = -1;

        LogList() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_H);
        }

        /// Pixels a row has for text, after the scrollbar and the row's own padding.
        int wrapWidth() {
            return Math.max(1, getRowWidth() - PAD * 2);
        }

        /// Forces the next rebuild to re-wrap every line. Called when the wrap width moves.
        void dropWrapCache() {
            wrapCache.clear();
        }

        void rebuild() {
            boolean atBottom = maxScrollAmount() <= 0 || scrollAmount() >= maxScrollAmount() - 1.0D;

            clearEntries();
            matches.clear();

            int wrapWidth = wrapWidth();
            List<EditorLogBuffer.Line> lines = new ArrayList<>();
            Map<Long, List<String>> nextCache = new HashMap<>();

            for (EditorLogBuffer.Line line : EditorLogBuffer.getInstance().snapshot()) {
                if (!enabledLevels.contains(line.level())) {
                    continue;
                }

                lines.add(line);

                List<String> rows = wrapCache.get(line.id());

                if (rows == null) {
                    rows = wrap(font, line.text(), wrapWidth);
                }

                nextCache.put(line.id(), rows);
                collectMatches(line, rows, getItemCount());

                int offset = 0;

                for (String row : rows) {
                    addEntry(new Entry(line, offset, row));
                    offset += row.length();
                }
            }

            visibleLines = lines;
            wrapCache = nextCache;

            // Effectively call repositionEntries without access widener
            updateSizeAndPosition(getWidth(), getHeight(), getX(), getY());

            if (atBottom) {
                setScrollAmount(maxScrollAmount());
            }

            resolveCurrentMatch();
        }

        /// Re-points [#currentMatch] at the match it was on before the rebuild, dropping it when that
        /// match is gone.
        private void resolveCurrentMatch() {
            currentMatch = -1;

            for (int i = 0; i < matches.size(); i++) {
                Match match = matches.get(i);

                if (match.lineId() == currentLineId && match.from() == currentFrom) {
                    currentMatch = i;
                    return;
                }
            }
        }

        private void setCurrentMatch(int index) {
            currentMatch = index;
            currentLineId = index < 0 ? -1 : matches.get(index).lineId();
            currentFrom = index < 0 ? -1 : matches.get(index).from();
        }

        /// Records every hit in `line`, tagging each with the row of `rows` it starts on. `firstRow` is
        /// the index the first of those rows will take.
        private void collectMatches(EditorLogBuffer.Line line, List<String> rows, int firstRow) {
            if (query.isEmpty()) {
                return;
            }

            String text = line.text();

            for (int at = indexOfIgnoreCase(text, query, 0); at >= 0; at = indexOfIgnoreCase(text, query, at + query.length())) {
                int row = firstRow;
                int consumed = 0;

                for (String candidate : rows) {
                    if (consumed + candidate.length() > at) {
                        break;
                    }

                    consumed += candidate.length();
                    row++;
                }

                matches.add(new Match(line.id(), at, at + query.length(), row));
            }
        }

        void setQuery(String query) {
            this.query = query;
        }

        int matchCount() {
            return matches.size();
        }

        /// The 1-based position of the current match, or `0` when there is none.
        int currentMatchNumber() {
            return currentMatch < 0 ? 0 : currentMatch + 1;
        }

        /// Parks on the match nearest the row the view already shows, and scrolls to it.
        void focusNearestMatch() {
            if (matches.isEmpty()) {
                setCurrentMatch(-1);
                return;
            }

            int anchorRow = (int)(scrollAmount() / ROW_H);
            int best = 0;
            int bestDistance = Integer.MAX_VALUE;

            for (int i = 0; i < matches.size(); i++) {
                int distance = Math.abs(matches.get(i).row() - anchorRow);

                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }

            setCurrentMatch(best);
            scrollToCurrentMatch();
        }

        /// Steps the current match by `delta`, wrapping at either end. With nothing parked yet, a step
        /// forward lands on the first match and a step back on the last.
        void step(int delta) {
            if (matches.isEmpty()) {
                setCurrentMatch(-1);
                return;
            }

            int from = currentMatch < 0 ? (delta > 0 ? -1 : 0) : currentMatch;

            setCurrentMatch(Math.floorMod(from + delta, matches.size()));
            scrollToCurrentMatch();
        }

        private void scrollToCurrentMatch() {
            if (currentMatch < 0) {
                return;
            }

            setScrollAmount(matches.get(currentMatch).row() * ROW_H - (getHeight() - ROW_H) / 2.0D);
        }

        void selectAll() {
            if (visibleLines.isEmpty()) {
                selectionAnchor = null;
                selectionCursor = null;
                return;
            }

            EditorLogBuffer.Line first = visibleLines.getFirst();
            EditorLogBuffer.Line last = visibleLines.getLast();

            selectionAnchor = new TextPoint(first.id(), 0);
            selectionCursor = new TextPoint(last.id(), last.text().length());
        }

        /// The selected text, newline-joined on the source lines rather than on the wrapped rows.
        String selectedText() {
            TextRange selection = orderedSelection();

            if (selection == null) {
                return "";
            }

            StringBuilder builder = new StringBuilder();

            for (EditorLogBuffer.Line line : visibleLines) {
                if (line.id() < selection.from().lineId() || line.id() > selection.to().lineId()) {
                    continue;
                }

                int length = line.text().length();
                int from = line.id() == selection.from().lineId() ? Mth.clamp(selection.from().charIndex(), 0, length) : 0;
                int to = line.id() == selection.to().lineId() ? Mth.clamp(selection.to().charIndex(), from, length) : length;

                if (!builder.isEmpty()) {
                    builder.append('\n');
                }

                builder.append(line.text(), from, to);
            }

            return builder.toString();
        }

        private @Nullable TextRange orderedSelection() {
            if (selectionAnchor == null || selectionCursor == null || selectionAnchor.equals(selectionCursor)) {
                return null;
            }

            return selectionAnchor.isBefore(selectionCursor)
                ? new TextRange(selectionAnchor, selectionCursor)
                : new TextRange(selectionCursor, selectionAnchor);
        }

        private @Nullable TextPoint pointAt(double mouseX, double mouseY) {
            List<Entry> rows = children();

            if (rows.isEmpty()) {
                return null;
            }

            int index = Mth.clamp((int)Math.floor((mouseY - getRowTop(0)) / (double)ROW_H), 0, rows.size() - 1);
            Entry row = rows.get(index);

            return new TextPoint(row.line.id(), row.sourceOffset + row.columnAt(mouseX));
        }

        @Override
        public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
            if (event.button() != 0 || !isMouseOver(event.x(), event.y())) {
                return false;
            }

            if (updateScrolling(event)) {
                int scrollerTop = scrollBarY();
                int scrollerH = scrollerHeight();
                boolean onScroller = event.y() >= scrollerTop && event.y() < scrollerTop + scrollerH;

                // A press on the bare track takes the scroller by its middle, so it jumps under the
                // pointer and the drag continues from there.
                draggingScrollbar = true;
                scrollGrab = onScroller ? event.y() - scrollerTop : scrollerH / 2.0D;

                dragScrollerTo(event.y());

                return true;
            }

            selecting = true;
            selectionAnchor = pointAt(event.x(), event.y());
            selectionCursor = selectionAnchor;

            return true;
        }

        /// Puts the scroller's top at `mouseY - scrollGrab`, inverting the `scrollBarY` formula.
        ///
        /// Vanilla's `AbstractScrollArea#mouseDragged` moves the scroller by a scaled delta
        /// (`dy * yDragScale`) instead of by the pointer, and the scale is floored at 1.0. On a list
        /// whose content dwarfs its height the two disagree, and every clamp at either end of the
        /// track loses the remainder, so the scroller drifts from the cursor and never catches up.
        private void dragScrollerTo(double mouseY) {
            int track = getHeight() - scrollerHeight();

            if (track <= 0) {
                return;
            }

            setScrollAmount((mouseY - scrollGrab - getY()) * maxScrollAmount() / track);
        }

        @Override
        public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
            if (draggingScrollbar) {
                dragScrollerTo(event.y());
                return true;
            }

            if (!selecting) {
                return super.mouseDragged(event, dx, dy);
            }

            // Past an edge, step the view one row that way so the drag keeps reaching further text.
            if (event.y() < getY()) {
                setScrollAmount(scrollAmount() - ROW_H);
            } else if (event.y() > getBottom()) {
                setScrollAmount(scrollAmount() + ROW_H);
            }

            selectionCursor = pointAt(event.x(), event.y());

            return true;
        }

        @Override
        public void onRelease(@NonNull MouseButtonEvent event) {
            selecting = false;
            draggingScrollbar = false;
            super.onRelease(event);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
            if (!isMouseOver(mx, my)) {
                return false;
            }

            return super.mouseScrolled(mx, my, scrollX, scrollY);
        }

        @Override
        protected double scrollRate() {
            return ROW_H * 3;
        }

        @Override
        public int getRowLeft() {
            return getX();
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
        protected void extractScrollbar(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (!scrollable()) return;

            int sbX = scrollBarX(), scrollerH = scrollerHeight(), scrollerY = scrollBarY();
            boolean hovered = mouseX >= sbX && mouseX < sbX + SCROLLBAR_W && mouseY >= scrollerY && mouseY < scrollerY + scrollerH;

            graphics.fill(sbX, getY(), sbX + SCROLLBAR_W, getBottom() - 1, 0xFF1A1A1A);
            graphics.fill(sbX + 1, scrollerY, sbX + SCROLLBAR_W - 1, scrollerY + scrollerH, hovered ? 0xFF6A6A6A : 0xFF505050);

            extractMatchTicks(graphics, sbX);
        }

        /// Marks every hit on the scrollbar track, drawn over the scroller so a hit behind it still shows.
        private void extractMatchTicks(@NonNull GuiGraphicsExtractor graphics, int sbX) {
            int rows = getItemCount();

            if (matches.isEmpty() || rows <= 0) {
                return;
            }

            int trackTop = getY();
            int trackHeight = getBottom() - 1 - trackTop;

            for (int i = 0; i < matches.size(); i++) {
                int y = trackTop + (int)((long)matches.get(i).row() * trackHeight / rows);

                graphics.fill(sbX, y, sbX + SCROLLBAR_W, y + 1, i == currentMatch ? CURRENT_TICK_COLOR : MATCH_TICK_COLOR);
            }
        }

        @Override
        protected void updateWidgetNarration(@NonNull NarrationElementOutput n) {
        }

        /// One wrapped row of [#line], starting [#sourceOffset] characters into it.
        final class Entry extends AbstractSelectionList.Entry<@NonNull Entry> {
            private final EditorLogBuffer.Line line;
            private final int sourceOffset;
            private final String text;

            Entry(EditorLogBuffer.Line line, int sourceOffset, String text) {
                this.line = line;
                this.sourceOffset = sourceOffset;
                this.text = text;
            }

            /// Character index under the given screen x.
            int columnAt(double mouseX) {
                int relX = (int)mouseX - getX() - PAD;

                if (relX <= 0) {
                    return 0;
                }

                return font.plainSubstrByWidth(text, relX).length();
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mx, int my, boolean hovered, float a) {
                int left = getX() + PAD;

                extractMatchHighlights(g, left);
                extractSelectionHighlight(g, left);

                int textY = getY() + (getHeight() - font.lineHeight) / 2;
                int stampEnd = Mth.clamp(line.prefixLength() - sourceOffset, 0, text.length());
                int textX = left;

                if (stampEnd > 0) {
                    String stamp = text.substring(0, stampEnd);

                    g.text(font, stamp, textX, textY, levelColor(line.level()), false);
                    textX += font.width(stamp);
                }

                if (stampEnd < text.length()) {
                    g.text(font, text.substring(stampEnd), textX, textY, BODY_COLOR, false);
                }
            }

            private void extractMatchHighlights(GuiGraphicsExtractor g, int left) {
                for (int i = 0; i < matches.size(); i++) {
                    Match match = matches.get(i);

                    if (match.lineId() != line.id()) {
                        continue;
                    }

                    fillSpan(g, left, match.from() - sourceOffset, match.to() - sourceOffset,
                        i == currentMatch ? CURRENT_MATCH_COLOR : MATCH_COLOR);
                }
            }

            private void extractSelectionHighlight(GuiGraphicsExtractor g, int left) {
                TextRange selection = orderedSelection();

                if (selection == null || line.id() < selection.from().lineId() || line.id() > selection.to().lineId()) {
                    return;
                }

                int low = line.id() == selection.from().lineId() ? selection.from().charIndex() - sourceOffset : 0;
                int high = line.id() == selection.to().lineId() ? selection.to().charIndex() - sourceOffset : text.length();

                fillSpan(g, left, low, high, SELECTION_COLOR);
            }

            /// Paints `[from, to)` of this row, both clamped into it, behind the text.
            private void fillSpan(GuiGraphicsExtractor g, int left, int from, int to, int color) {
                int start = Mth.clamp(from, 0, text.length());
                int end = Mth.clamp(to, start, text.length());

                if (end == start) {
                    return;
                }

                g.fill(
                    left + font.width(text.substring(0, start)), getY(),
                    left + font.width(text.substring(0, end)), getY() + getHeight(),
                    color
                );
            }
        }

        // ***************************************************************
        // [AnchoredWidget] implementations
        // ***************************************************************
        private Unit x1 = AnchoredWidget.pixel(1);
        private Unit x2 = AnchoredWidget.pixel(1);
        private Unit y1 = AnchoredWidget.pixel(TITLE_H + FILTER_H + 1);
        private Unit y2 = AnchoredWidget.pixel(FOOTER_H);
        private HorizontalAnchorType horizontalAnchorType = HorizontalAnchorType.LEFT_RIGHT;
        private VerticalAnchorType verticalAnchorType = VerticalAnchorType.TOP_BOTTOM;

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
