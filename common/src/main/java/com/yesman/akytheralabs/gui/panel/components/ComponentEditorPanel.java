package com.yesman.akytheralabs.gui.panel.components;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.client.gui.widget.WidgetTable;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.dataholder.ComponentHolderRegistry;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.AdvancedEditBox;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsEditBoxTheme;
import com.yesman.akytheralabs.gui.widget.ContextMenuContainer;
import com.yesman.akytheralabs.gui.widget.PanelHeader;
import com.yesman.akytheralabs.gui.widget.RefusalPopup;
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/// "Components" panel: a [PanelHeader] with a `+` add button over a [WidgetTable] listing the patch's
/// component tree, one row per component indented under the [ComponentAttachable] it is attached to.
///
/// A row's name is the component's identity, since the engine keys components by name. It shows as an
/// editable box and a commit re-keys it in its host's map, with sibling names kept unique by
/// [ComponentAttachableHolder#uniqueName].
public final class ComponentEditorPanel extends AdjustablePanelManager.PanelArea {
    private static final int ADD_BTN = 16;
    private static final int ROW_H = 18;
    private static final int INDENT = 10; // horizontal inset one level of attachment adds to a row
    private static final int DROP_LINE_COLOR = 0xFF4A9EFF; // drop separator of a row drag
    private static final int OVERRIDE_COLOR = 0xFFFFFFFF; // a row declaring something of its own
    private static final int INHERITED_COLOR = 0xFF7A7A7A; // a row holding what it inherits
    private static final int REVERT_BTN = 12; // the ✕ an overriding row carries

    private final Font font;

    /// The editor holders of the patch kind being edited. Its blueprint registry is what the panel's
    /// menus offer.
    private final ComponentHolderRegistry registry;

    /// The tree's root host: the patch itself, whose children are the top-level components.
    private final ComponentAttachableHolder root;

    /// The render state the patched entity is drawn through; null when the patch kind has no entity
    /// behind it.
    private final @Nullable Class<? extends EntityRenderState> hostRenderState;

    /// The components the edited patch inherits, keyed as [ComponentAttachableHolder#foldChain] folded
    /// them. Re-read on every [#rebuildList]. `Map::of` for a patch kind with no parent chain.
    private final Supplier<? extends Map<String, ComponentDataHolder<?>>> inherited;

    private final Runnable onChanged;

    /// Fired when the focused component changes, with the host it hangs off; both null when nothing
    /// is focused.
    private final BiConsumer<@Nullable ComponentDataHolder<?>, @Nullable ComponentAttachableHolder> onComponentFocused;

    private final PanelHeader header;
    private final WidgetTable table;

    /// The flattened tree in traversal order, one entry per visible row, rebuilt by [#rebuildList].
    private final List<Row> rows = new ArrayList<>();

    /// The inline name box of each listed component.
    private final Map<ComponentDataHolder<?>, AdvancedEditBox<String>> nameBoxes = new IdentityHashMap<>();

    /// The holders whose rows read as overrides. Telling one apart from what it overrides serializes
    /// both, so the answer is kept until an edit lands rather than taken per frame.
    private final Set<ComponentDataHolder<?>> overriding = Collections.newSetFromMap(new IdentityHashMap<>());

    /// The history top [#overriding] was taken against. An undo or redo of a value edit rebuilds
    /// nothing here, so a top that has moved on its own is the signal to re-read.
    @Nullable
    private WorkHistory overridingAt;

    /// Set when something outside this panel has changed a holder — a value written in the Details
    /// panel, which reaches the holder without a history entry until the edit commits.
    private boolean overridingStale = true;

    @Nullable
    private ComponentDataHolder<?> selected;

    /// The component being dragged to a new position among its siblings; null when no drag is in
    /// flight.
    @Nullable
    private ComponentDataHolder<?> dragged;

    /// Where releasing the drag would put [#dragged]; null while the cursor is over the position it
    /// already holds.
    @Nullable
    private DropGap dropGap;

    /// The claims the shown component would commit, hosts before their children; empty once they are
    /// committed or the selection moved on.
    private final List<Claim> pending = new ArrayList<>();

    /// The components committed claims attached, hosts before their children. Swept by
    /// [#dropUnchangedOverrides].
    private final List<Claim> committed = new ArrayList<>();

    @Nullable
    private ScreenRectangle lastRect;
    private int addBtnX;
    private int addBtnY;

    /// One listed component: its holder, the host it hangs off, the name it is attached under, and how
    /// many levels of [#INDENT] its row carries. An inherited row belongs to an ancestor patch and
    /// carries no host of its own until [#prepareClaim] gives it one. `parent` is the row it hangs
    /// under, so a claim can walk up to the host it needs; `shadowed` is the inherited component a
    /// local row takes the place of.
    private record Row(
        int depth,
        String name,
        ComponentDataHolder<?> holder,
        @Nullable ComponentAttachableHolder host,
        @Nullable Row parent,
        boolean inherited,
        @Nullable ComponentDataHolder<?> shadowed
    ) {}

    /// A copy of an inherited component the Details panel is showing, where it lands once something is
    /// written into it, and the component it was copied from — which is what it overrides.
    private record Claim(
        ComponentAttachableHolder host, String name, ComponentDataHolder<?> holder, ComponentDataHolder<?> inherited
    ) {}

    /// A place a dragged row can land: the index it inserts at in its host's key order, and the flat
    /// row its separator draws above.
    private record DropGap(int siblingIndex, int row) {}

    public ComponentEditorPanel(
        Font font,
        Screen parentScreen,
        ComponentHolderRegistry registry,
        ComponentAttachableHolder root,
        @Nullable Class<? extends EntityRenderState> hostRenderState,
        Supplier<? extends Map<String, ComponentDataHolder<?>>> inherited,
        Runnable onChanged,
        BiConsumer<@Nullable ComponentDataHolder<?>, @Nullable ComponentAttachableHolder> onComponentFocused
    ) {
        this.font = font;
        this.registry = registry;
        this.root = root;
        this.hostRenderState = hostRenderState;
        this.inherited = inherited;
        this.onChanged = onChanged;
        this.onComponentFocused = onComponentFocused;
        this.header = new PanelHeader(font, Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_PANEL_COMPONENTS));
        this.table = new WidgetTable(
            parentScreen,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(PanelHeader.DEFAULT_HEADER_HEIGHT), AnchoredWidget.pixel(0),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_BOTTOM,
            ROW_H
        );

        addWidget(header);
        addWidget(table);

        rebuildList();
    }

    public void rebuildList() {
        syncFixedNames(root);

        table.clearWidgets();
        rows.clear();
        nameBoxes.clear();

        collectRows(root, null, inherited.get(), 0);

        for (Row row : rows) {
            AdvancedEditBox<String> nameBox = nameBox(row);
            nameBoxes.put(row.holder(), nameBox);
            table.newRow().addWidget(nameBox);
        }

        refreshOverrides();

        table.initialize(false);
        if (lastRect != null) table.relocate(lastRect);
    }

    /// Re-reads which rows declare something of their own and recolors their name boxes.
    ///
    /// An edit in the Details panel writes straight into the holder and rebuilds nothing here, so the
    /// state is re-read whenever the history top has moved since the last pass.
    private void refreshOverrides() {
        overridingAt = HistoryManager.getInstance().currentSavePoint();
        overridingStale = false;
        overriding.clear();

        for (Row row : rows) {
            boolean override = isOverride(row);

            if (override) overriding.add(row.holder());

            AdvancedEditBox<String> box = nameBoxes.get(row.holder());
            if (box != null) box.setTextColor(override ? OVERRIDE_COLOR : INHERITED_COLOR);
        }
    }

    /// Depth-first walk of the holder tree into [#rows], parents immediately before their children.
    /// `inheritedChildren` are what an ancestor patch contributes at this level.
    ///
    /// Rows follow the **inherited** order: a local component takes the slot of the one it overrides,
    /// and one this patch adds of its own falls in after them. That is the order
    /// [com.yesman.akythera.api.component.PatchComponent#fold] lays the layers out in, so claiming a
    /// component leaves every row where it was.
    private void collectRows(
        @Nullable ComponentAttachableHolder host,
        @Nullable Row parent,
        Map<String, ComponentDataHolder<?>> inheritedChildren,
        int depth
    ) {
        Map<String, ComponentDataHolder<?>> locals = host == null ? Map.of() : host.getComponents();

        // What each local component overrides, so an override can be listed in its counterpart's slot
        Map<ComponentDataHolder<?>, String> overrideNames = new IdentityHashMap<>();

        locals.forEach((name, holder) -> {
            ComponentDataHolder<?> shadowed = ComponentAttachableHolder.matching(inheritedChildren, name, holder);
            if (shadowed != null) overrideNames.put(shadowed, name);
        });

        Set<String> claimedSlots = new HashSet<>(overrideNames.values());

        inheritedChildren.forEach((inheritedName, inheritedHolder) -> {
            String name = overrideNames.get(inheritedHolder);
            ComponentDataHolder<?> local = name == null ? null : locals.get(name);

            Row row = local == null
                ? new Row(depth, inheritedName, inheritedHolder, null, parent, true, null)
                : new Row(depth, name, local, host, parent, false, inheritedHolder);

            rows.add(row);
            collectRows(
                local instanceof ComponentAttachableHolder child ? child : null,
                row,
                childrenOf(inheritedHolder),
                depth + 1);
        });

        locals.forEach((name, holder) -> {
            if (claimedSlots.contains(name)) return;

            Row row = new Row(depth, name, holder, host, parent, false, null);
            rows.add(row);
            collectRows(
                holder instanceof ComponentAttachableHolder child ? child : null,
                row,
                Map.of(),
                depth + 1);
        });
    }

    private static Map<String, ComponentDataHolder<?>> childrenOf(@Nullable ComponentDataHolder<?> holder) {
        return holder instanceof ComponentAttachableHolder host ? host.getComponents() : Map.of();
    }

    /// The inherited component `local` overrides, or null when it overrides none. A component that is
    /// only prepared is not listed yet, so its claim answers for it.
    public @Nullable ComponentDataHolder<?> inheritedBehind(ComponentDataHolder<?> local) {
        for (Claim claim : pending) {
            if (claim.holder() == local) return claim.inherited();
        }

        return rows.stream()
            .filter(row -> row.holder() == local && !row.inherited())
            .findFirst()
            .map(Row::shadowed)
            .orElse(null);
    }

    /// One listed component: the holder, and whether it comes from an ancestor patch with no
    /// override of its own here.
    public record ListedComponent(ComponentDataHolder<?> holder, boolean inherited) {}

    /// Every component this panel lists, in row order: this patch's own, plus the inherited ones it
    /// declares no override for.
    ///
    /// A row being previewed through a claim reports the **copy** [#prepareClaim] set aside, marked
    /// as the patch's own — that copy is what the Details rows edit, and what anything else showing
    /// the row has to edit with them.
    public Stream<ListedComponent> listedComponents() {
        return rows.stream().map(row -> pending.stream()
            .filter(claim -> claim.inherited() == row.holder())
            .<ListedComponent>map(claim -> new ListedComponent(claim.holder(), false))
            .findFirst()
            .orElseGet(() -> new ListedComponent(row.holder(), row.inherited())));
    }

    /// Focuses the row `holder` is listed in — the 3D preview's counterpart of clicking that row,
    /// claim and all, where [#focusComponent] goes straight to [#select] and would hand an inherited
    /// component over as the ancestor's own.
    public void focusListedComponent(ComponentDataHolder<?> holder) {
        Row row = rowOf(holder);

        if (row != null) selectRow(row);
    }

    /// Re-keys every fixed-name component in the tree whose key no longer matches the name it derives,
    /// suffixing a name a sibling already holds. True when a key actually moved.
    ///
    /// A component whose derived name is taken keeps the suffixed key it settled on, so a second pass
    /// over an already-synced tree moves nothing — which is what makes [#refreshFixedNames] safe to run
    /// per frame.
    private static boolean syncFixedNames(ComponentAttachableHolder host) {
        SequencedMap<String, ComponentDataHolder<?>> children = host.getComponents();
        boolean moved = false;

        for (String name : List.copyOf(children.keySet())) {
            ComponentDataHolder<?> holder = children.get(name);
            String fixed = holder.fixedName();

            if (fixed != null && !fixed.equals(name)) {
                Map<String, ComponentDataHolder<?>> others = new HashMap<>(children);
                others.remove(name);
                String unique = ComponentAttachableHolder.uniqueName(fixed, others.keySet());

                if (!unique.equals(name)) {
                    rekey(children, List.copyOf(children.keySet()), name, unique);
                    moved = true;
                }
            }

            if (holder instanceof ComponentAttachableHolder child) moved |= syncFixedNames(child);
        }

        return moved;
    }

    /// Rebuilds the rows once a fixed name has drifted from what its component now derives.
    ///
    /// A derived name changes wherever its source does — a stance picked in the Details panel, an undo,
    /// a data block renamed elsewhere in the project — and none of those reach this panel, so the tree
    /// is re-checked as it draws rather than pushed at from every writer.
    private void refreshFixedNames() {
        if (syncFixedNames(root)) rebuildList();
    }

    /// The row's name, editable in place and transparent until focused. A fixed-name row is read-only,
    /// [#syncFixedNames] having already keyed it under what it derives; so is an inherited row, which
    /// belongs to an ancestor patch.
    private AdvancedEditBox<String> nameBox(Row row) {
        boolean fixed = row.holder().fixedName() != null;

        AdvancedEditBox<String> box = new AdvancedEditBox<>(
            font,
            AnchoredWidget.pixel(6 + row.depth() * INDENT), AnchoredWidget.pixel(15),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_RIGHT,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            row::name,
            _ -> {},
            ComponentEditorPanel::isValidComponentName,
            s -> s, s -> s,
            Component.empty()
        );

        box.setTheme(AkytheraLabsEditBoxTheme.TRANSPARENT_EDIT_BOX);
        box.setFormatter(name -> name + " (" + row.holder().generateEventually().getSimpleName() + ")");

        if (fixed || row.inherited()) {
            box.setEditable(false);
        } else {
            box.addCharReplacement(' ', '_');
            box.setOnCommitted((_, _) -> commitRename(row, box));
        }

        return box;
    }

    /// Whether the row declares something of its own — the white state. An inherited row never does; a
    /// local row that overrides nothing always does.
    private boolean isOverride(Row row) {
        if (row.inherited()) return false;
        if (row.shadowed() == null) return true;

        return !registry.sameDeclaration(row.holder(), row.shadowed());
    }

    /// Name filter: rejects the quote and backslash the source export cannot carry into a Java string
    /// literal.
    private static boolean isValidComponentName(String s) {
        return s.indexOf('"') < 0 && s.indexOf('\\') < 0;
    }

    @Override
    protected void onPanelResized(ScreenRectangle screenRectangle) {
        lastRect = screenRectangle;
        header.relocate(screenRectangle);
        table.relocate(screenRectangle);

        addBtnX = screenRectangle.right() - ADD_BTN - 4;
        addBtnY = screenRectangle.top() + (PanelHeader.DEFAULT_HEADER_HEIGHT - ADD_BTN) / 2;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        refreshFixedNames();

        if (overridingStale || HistoryManager.getInstance().currentSavePoint() != overridingAt) refreshOverrides();

        graphics.fill(getX(), getY(), getRight(), getBottom(), bgColor);

        // Focused-row highlight, under the table's row content
        int selectedRow = selectedRowIndex();

        if (selectedRow >= 0) {
            int top = rowTop(selectedRow);
            graphics.fill(getX(), top, getRight(), top + ROW_H, 0xFF0A2833);
        }

        header.extractRenderState(graphics, mouseX, mouseY, a);
        table.extractRenderState(graphics, mouseX, mouseY, a);

        extractDropSeparator(graphics);

        boolean hovered = overAddButton(mouseX, mouseY);
        graphics.fill(addBtnX, addBtnY, addBtnX + ADD_BTN, addBtnY + ADD_BTN, hovered ? 0xFF3A3A3A : 0xFF2A2A2A);
        graphics.text(
            font, Component.literal("+"),
            addBtnX + (ADD_BTN - font.width("+")) / 2,
            addBtnY + (ADD_BTN - font.lineHeight) / 2,
            0xFFCCCCCC, false
        );

        extractRevertMarks(graphics, mouseX, mouseY);
        extractFailureMarks(graphics, mouseX, mouseY);
    }

    /// The ✕ every overriding row carries. Drawn here rather than added to the row, because a press on
    /// a row is consumed by [#mouseClicked] before the table's own widgets see it.
    private void extractRevertMarks(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < rows.size(); i++) {
            if (!isRevertable(i)) continue;

            int top = rowTop(i);
            if (top < table.getY() || top + ROW_H > table.getBottom()) continue;

            graphics.text(
                font, Component.literal("✕"),
                getRight() - REVERT_BTN, top + (ROW_H - font.lineHeight) / 2,
                overRevert(i, mouseX, mouseY) ? 0xFFFFFFFF : 0xFF888888, false
            );
        }
    }

    /// The line marking where the row being dragged would land, indented to the level it drops into.
    private void extractDropSeparator(GuiGraphicsExtractor graphics) {
        if (dragged == null || dropGap == null) return;

        Row row = rowOf(dragged);
        if (row == null) return;

        int y = rowTop(dropGap.row());
        if (y < table.getY() || y > table.getBottom()) return;

        graphics.fill(getX() + 6 + row.depth() * INDENT, y - 1, getRight() - 2, y + 1, DROP_LINE_COLOR);
    }

    /// Red outline on every row whose component holder failed to deserialize, plus a hover tooltip
    /// carrying the failure.
    private void extractFailureMarks(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Exception hoveredFailure = null;

        for (int i = 0; i < rows.size(); i++) {
            Exception failure = rows.get(i).holder().getDeserializationFailureException();
            if (failure == null) continue;

            int top = rowTop(i);
            graphics.outline(getX(), top, getRight() - getX(), ROW_H, AbstractNodeWidget.COLOR_ERROR_OUTLINE);

            if (mouseX >= getX() && mouseX < getRight() && mouseY >= top && mouseY < top + ROW_H) {
                hoveredFailure = failure;
            }
        }

        if (hoveredFailure != null) {
            drawFailureTooltip(graphics, hoveredFailure, mouseX, mouseY);
        }
    }

    private void drawFailureTooltip(GuiGraphicsExtractor graphics, Exception failure, int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = new ArrayList<>(
            font.split(
                Component.literal("This component was malformed and failed to deserialize; its values were reset to defaults."),
                220
            )
        );
        String message = failure.getMessage();

        if (message != null && !message.isBlank()) {
            lines.addAll(font.split(Component.literal(message), 220));
        }

        int pad = 4;
        int lineH = font.lineHeight + 1;
        int textW = lines.stream().mapToInt(font::width).max().orElse(0);
        int boxW = textW + pad * 2;
        int boxH = lines.size() * lineH - 1 + pad * 2;
        int boxX = mouseX + 10;
        int boxY = mouseY + 10;

        graphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xF0200C0C);
        graphics.outline(boxX, boxY, boxW, boxH, AbstractNodeWidget.COLOR_ERROR_OUTLINE);

        int textY = boxY + pad;

        for (FormattedCharSequence line : lines) {
            graphics.text(font, line, boxX + pad, textY, 0xFFFF8A8A, false);
            textY += lineH;
        }
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (overAddButton(event.x(), event.y())) {
            openAttachMenu(root, addBtnX, addBtnY + ADD_BTN);
            return true;
        }

        int index = rowIndexAt(event.y());

        if (event.button() == 0 && index >= 0 && isRevertable(index) && overRevert(index, event.x(), event.y())) {
            revertComponent(rows.get(index));
            return true;
        }

        if (event.button() == 1) { // row menu
            if (index >= 0) {
                Row row = rows.get(index);
                selectRow(row);

                // An inherited row's menu would act on the ancestor patch's component.
                if (!row.inherited()) openRowMenu(row, (int) event.x(), (int) event.y());
            }
            return true;
        }

        if (event.button() != 0 || event.y() < table.getY()) {
            return super.mouseClicked(event, doubleClick);
        }

        if (index < 0) { // empty space clears the focus
            select(null, null);
            return true;
        }

        Row row = rows.get(index);
        selectRow(row);

        // A press on a name box already being renamed repositions its cursor instead
        AdvancedEditBox<String> box = nameBoxes.get(row.holder());
        if (box != null && box.isFocused()) {
            return super.mouseClicked(event, doubleClick);
        }

        if (doubleClick && !row.inherited()) {
            beginRename(row.holder());
        } else if (!row.inherited() && row.host() != null && !overScrollbar(event.x())) {
            dragged = row.holder();
        }

        return true;
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        if (dragged == null) {
            return super.mouseDragged(event, dx, dy);
        }

        dropGap = dropGapAt(event.y());

        return true;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        ComponentDataHolder<?> moving = dragged;
        DropGap gap = dropGap;

        dragged = null;
        dropGap = null;

        if (moving != null && gap != null) {
            setDragging(false);
            moveComponent(moving, gap.siblingIndex());
            return true;
        }

        return super.mouseReleased(event);
    }

    /// The gap the cursor sits nearest to among the ones the dragged row's siblings open, or null when
    /// that is the position it already holds.
    private @Nullable DropGap dropGapAt(double mouseY) {
        Row row = rowOf(dragged);
        if (row == null) return null;

        ComponentAttachableHolder host = row.host();
        if (host == null) return null;

        DropGap nearest = null;
        int nearestDistance = Integer.MAX_VALUE;

        for (DropGap gap : dropGaps(host)) {
            int distance = Math.abs((int) mouseY - rowTop(gap.row()));

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = gap;
            }
        }

        int from = List.copyOf(host.getComponents().keySet()).indexOf(row.name());

        if (nearest != null && (nearest.siblingIndex() == from || nearest.siblingIndex() == from + 1)) {
            return null;
        }

        return nearest;
    }

    /// Where a child of `host` can be dropped: one gap above each of its rows, plus one past the last
    /// row nested under them. An inherited row belongs to an ancestor patch and opens no gap.
    private List<DropGap> dropGaps(ComponentAttachableHolder host) {
        List<DropGap> gaps = new ArrayList<>();
        int end = -1;

        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).host() != host) continue;

            gaps.add(new DropGap(gaps.size(), i));
            end = subtreeEnd(i);
        }

        if (end >= 0) gaps.add(new DropGap(gaps.size(), end));

        return gaps;
    }

    /// The flat index one past the last row nested under the row at `index`.
    private int subtreeEnd(int index) {
        int depth = rows.get(index).depth();
        int end = index + 1;

        while (end < rows.size() && rows.get(end).depth() > depth) end++;

        return end;
    }

    /// Focuses an already-listed component from outside the panel.
    public void focusComponent(@Nullable ComponentDataHolder<?> holder) {
        select(holder, holder == null ? null : hostOf(holder));
    }

    /// Re-fires the focus callback with the current selection, past [#select]'s identity guard.
    public void refocusDetails() {
        onComponentFocused.accept(selected, selected == null ? null : hostOf(selected));
    }

    /// Focuses a component, reporting the host it hangs off with it.
    private void select(@Nullable ComponentDataHolder<?> holder, @Nullable ComponentAttachableHolder host) {
        if (selected == holder) return;

        pending.clear();
        dropUnchangedOverrides();

        selected = holder;
        onComponentFocused.accept(holder, host);
    }

    /// Focuses a row. An inherited one is shown through a copy of itself that [#commitClaim] attaches
    /// once something is written into it, so an idle click leaves the patch alone.
    private void selectRow(Row row) {
        if (!row.inherited()) {
            select(row.holder(), row.host());
            return;
        }

        pending.clear();
        dropUnchangedOverrides();

        ComponentDataHolder<?> copy = prepareClaim(row);
        if (copy == null) return;

        selected = copy;
        onComponentFocused.accept(copy, pending.getLast().host());
    }

    /// Fills [#pending] with the copies that would take `row` and every inherited host above it into
    /// the patch, hosts first, and answers the copy of `row` itself. Null when a copy cannot be made.
    private @Nullable ComponentDataHolder<?> prepareClaim(Row row) {
        if (!row.inherited()) return row.holder();

        ComponentAttachableHolder host = row.parent() == null ? root : hostOfClaim(row.parent());
        if (host == null) return null;

        ComponentDataHolder<?> copy = registry.copyOf(row.holder());
        if (copy == null) return null;

        String fixed = copy.fixedName();
        String name = ComponentAttachableHolder.uniqueName(
            fixed == null ? row.name() : fixed, host.getComponents().keySet());

        pending.add(new Claim(host, name, copy, row.holder()));

        return copy;
    }

    /// The host a claim under `row` lands in: `row`'s own holder, prepared as a claim of its own when
    /// `row` is inherited too.
    private @Nullable ComponentAttachableHolder hostOfClaim(Row row) {
        ComponentDataHolder<?> holder = prepareClaim(row);

        return holder instanceof ComponentAttachableHolder host ? host : null;
    }

    /// A value is about to be written into the shown component: take over a component the patch is
    /// only previewing, and re-read the row colors, the write landing right after this returns.
    public void onDetailsWrite() {
        commitClaim();
        overridingStale = true;
    }

    /// Attaches what [#prepareClaim] set aside. Does nothing after the first write of a fill.
    private void commitClaim() {
        if (pending.isEmpty()) return;

        pending.forEach(claim -> claim.host().getComponents().put(claim.name(), claim.holder()));
        committed.addAll(pending);
        pending.clear();

        rebuildList();
        onChanged.run();
    }

    /// Drops every committed override that declares what it overrides and nothing more, children
    /// before their hosts. Runs on a selection change, after a list edit, and before a save, so an
    /// undone first edit leaves no override behind.
    public void dropUnchangedOverrides() {
        boolean dropped = false;

        for (Claim claim : committed.reversed()) {
            ComponentDataHolder<?> attached = claim.host().getComponents().get(claim.name());
            if (attached != claim.holder()) continue;

            if (attached instanceof ComponentAttachableHolder host && !host.getComponents().isEmpty()) continue;
            if (!registry.sameDeclaration(attached, claim.inherited())) continue;

            claim.host().getComponents().remove(claim.name());
            dropped = true;
        }

        committed.removeIf(claim -> claim.host().getComponents().get(claim.name()) != claim.holder());
        overridingStale = true;

        if (!dropped) return;

        if (selected != null && ComponentAttachableHolder.flatten(root).noneMatch(h -> h == selected)) {
            selected = null;
            onComponentFocused.accept(null, null);
        }

        rebuildList();
        onChanged.run();
    }

    /// The host of an already-listed component.
    private @Nullable ComponentAttachableHolder hostOf(ComponentDataHolder<?> holder) {
        Row row = rowOf(holder);

        return row == null ? null : row.host();
    }

    /// The listed row of a component, or null when it is no longer listed.
    private @Nullable Row rowOf(@Nullable ComponentDataHolder<?> holder) {
        int index = indexOf(holder);

        return index < 0 ? null : rows.get(index);
    }

    /// The flat row index of the focused component. A component the focus is only previewing has no
    /// row of its own yet, so it is the inherited row it was copied from.
    private int selectedRowIndex() {
        int index = indexOf(selected);
        if (index >= 0) return index;

        for (Claim claim : pending) {
            if (claim.holder() == selected) return indexOf(claim.inherited());
        }

        return -1;
    }

    private int indexOf(@Nullable ComponentDataHolder<?> holder) {
        if (holder == null) return -1;

        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).holder() == holder) return i;
        }

        return -1;
    }

    // ***************************************************************
    // Menus
    // ***************************************************************

    /// The component types `host` accepts, this patch kind offers, and [#registry] has a holder for.
    private List<Identifier> attachableTypes(ComponentAttachableHolder host) {
        if (host.attachableTypes().isEmpty()) return List.of();

        return registry.blueprintTypes().listElements()
            .map(entry -> entry.key().identifier())
            .filter(id -> {
                Class<?> holderClass = registry.holderClass(id);
                return holderClass != null && host.accepts(holderClass);
            })
            .filter(this::acceptsRenderState)
            .sorted(Comparator.comparing(Identifier::toString))
            .toList();
    }

    /// Whether `typeId`'s component can draw through this patch's render state. A component declaring
    /// no state, and a patch with no known state, both pass.
    private boolean acceptsRenderState(Identifier typeId) {
        Class<? extends EntityRenderState> componentState = registry.renderStateOf(typeId);

        return hostRenderState == null || componentState == null || componentState.isAssignableFrom(hostRenderState);
    }

    /// Opens the menu of component types attachable to `host`.
    private void openAttachMenu(ComponentAttachableHolder host, int x, int y) {
        ContextMenuContainer.PageBuilder builder = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(160);
        fillAttachEntries(builder, host);
        AkytheraEditorLayoutManager.getInstance().openModalWidget(builder.build(), x, y);
    }

    private void fillAttachEntries(ContextMenuContainer.PageBuilder builder, ComponentAttachableHolder host) {
        List<Identifier> available = attachableTypes(host);

        if (available.isEmpty()) {
            builder.entry()
                .message(Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MENU_NO_COMPONENTS))
                .onPress(_ -> {})
                .done();
            return;
        }

        for (Identifier id : available) {
            Component refusal = registry.attachRefusal(id, host);

            builder.entry()
                .message(refusal == null
                    ? Component.literal(id.getPath())
                    : Component.literal(id.getPath()).withStyle(ChatFormatting.DARK_GRAY))
                .onPress(_ -> {
                    if (refusal == null) attachComponent(host, id);
                    else RefusalPopup.open(
                        Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_POPUP_ATTACH_REFUSED_TITLE), refusal);
                })
                .done();
        }
    }

    /// Row menu: attach (only when the row's component hosts children at all), rename, remove.
    private void openRowMenu(Row row, int x, int y) {
        ContextMenuContainer.PageBuilder builder = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(150);

        if (row.holder() instanceof ComponentAttachableHolder host && !host.attachableTypes().isEmpty()) {
            ContextMenuContainer.PageBuilder subpage = builder.entry()
                .message(Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MENU_ATTACH_COMPONENT))
                .subpage();

            fillAttachEntries(subpage, host);
            subpage.done();
        }

        if (row.holder().fixedName() == null) {
            builder.entry()
                .message(Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MENU_RENAME))
                .onPress(_ -> beginRename(row.holder()))
                .done();
        }

        // A component overriding an inherited one is not the component — removing it would read as
        // deleting the ancestor's. The row's own ✕ puts it back instead.
        if (row.shadowed() == null) {
            builder.entry()
                .message(Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MENU_REMOVE))
                .onPress(_ -> removeComponent(row))
                .done();
        }

        AkytheraEditorLayoutManager.getInstance().openModalWidget(builder.build(), x, y);
    }

    // ***************************************************************
    // Structural edits
    // ***************************************************************

    private void attachComponent(ComponentAttachableHolder host, Identifier typeId) {
        ComponentDataHolder<?> holder = registry.createDefault(typeId, host);
        if (holder == null) return;

        String fixed = holder.fixedName();
        String name = fixed == null
            ? ComponentAttachableHolder.defaultName(typeId, host.getComponents().keySet())
            : ComponentAttachableHolder.uniqueName(fixed, host.getComponents().keySet());

        host.getComponents().put(name, holder);
        select(holder, host);

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                host.getComponents().remove(name);
                refresh();
            }

            @Override
            public void redo() {
                host.getComponents().put(name, holder);
                select(holder, host);
                refresh();
            }
        });

        refresh();
    }

    /// Removes a component and everything attached under it.
    private void removeComponent(Row row) {
        ComponentAttachableHolder host = row.host();
        if (host == null) return;

        SequencedMap<String, ComponentDataHolder<?>> siblings = host.getComponents();
        List<String> order = List.copyOf(siblings.keySet());
        if (!siblings.containsKey(row.name())) return;

        siblings.remove(row.name());

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                siblings.put(row.name(), row.holder());
                reorder(siblings, order);
                refresh();
            }

            @Override
            public void redo() {
                siblings.remove(row.name());
                refresh();
            }
        });

        refresh();
    }

    /// Whether the row at `index` carries the ✕: an override holding something its parent does not.
    private boolean isRevertable(int index) {
        Row row = rows.get(index);

        return row.shadowed() != null && overriding.contains(row.holder());
    }

    /// The ✕'s click box on the row at `index` — the [#REVERT_BTN] strip inside the panel's right
    /// edge, which is the gutter the name box already leaves clear.
    private boolean overRevert(int index, double mouseX, double mouseY) {
        int top = rowTop(index);
        int right = getRight() - 2;

        return mouseX >= right - REVERT_BTN && mouseX < right && mouseY >= top && mouseY < top + ROW_H;
    }

    /// Puts an overriding component back to the declaration it overrides, in one gesture over the
    /// whole component — which is why no Details row carries a revert of its own.
    ///
    /// A component with no children of its own leaves the patch and its row reads inherited again. One
    /// hosting components the patch attached itself stays, holding its parent's declaration under
    /// them, since those children have nowhere else to live.
    private void revertComponent(Row row) {
        ComponentDataHolder<?> base = row.shadowed();
        ComponentAttachableHolder host = row.host();
        if (base == null || host == null) return;

        ComponentDataHolder<?> previous = row.holder();
        boolean hostsOwn = previous instanceof ComponentAttachableHolder owner && !owner.getComponents().isEmpty();
        ComponentDataHolder<?> restored = hostsOwn ? registry.copyOf(base) : null;

        // Nothing to copy leaves the override standing rather than taking its children with it
        if (hostsOwn && !(restored instanceof ComponentAttachableHolder)) return;

        if (restored instanceof ComponentAttachableHolder target && previous instanceof ComponentAttachableHolder owner) {
            target.getComponents().putAll(owner.getComponents());
        }

        SequencedMap<String, ComponentDataHolder<?>> siblings = host.getComponents();
        List<String> order = List.copyOf(siblings.keySet());

        put(siblings, order, row.name(), restored);

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                put(siblings, order, row.name(), previous);
                refresh();
                refocus(previous);
            }

            @Override
            public void redo() {
                put(siblings, order, row.name(), restored);
                refresh();
                refocus(restored == null ? base : restored);
            }
        });

        refresh();
        refocus(restored == null ? base : restored);
    }

    /// Focuses whatever row `holder` sits in now. A revert drops the component the row was focused on,
    /// and the row that takes its place — the inherited one, or the restored copy — is the one the
    /// author is still looking at.
    private void refocus(ComponentDataHolder<?> holder) {
        Row row = rowOf(holder);

        if (row != null) selectRow(row);
    }

    /// Puts `holder` under `name` keeping the map's order, or takes the name out when it is null.
    private static void put(
        SequencedMap<String, ComponentDataHolder<?>> siblings,
        List<String> order,
        String name,
        @Nullable ComponentDataHolder<?> holder
    ) {
        if (holder == null) {
            siblings.remove(name);
            return;
        }

        siblings.put(name, holder);
        reorder(siblings, order);
    }

    /// Moves a component to `targetIndex` in its host's key order, counted with the component still in
    /// place.
    private void moveComponent(ComponentDataHolder<?> holder, int targetIndex) {
        Row row = rowOf(holder);
        if (row == null) return;

        ComponentAttachableHolder host = row.host();
        if (host == null) return;

        SequencedMap<String, ComponentDataHolder<?>> siblings = host.getComponents();
        List<String> order = List.copyOf(siblings.keySet());
        int from = order.indexOf(row.name());

        if (from < 0 || targetIndex == from || targetIndex == from + 1) return;

        List<String> shifted = new ArrayList<>(order);
        shifted.remove(from);
        shifted.add(targetIndex > from ? targetIndex - 1 : targetIndex, row.name());
        List<String> moved = List.copyOf(shifted);

        reorder(siblings, moved);

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                reorder(siblings, order);
                refresh();
            }

            @Override
            public void redo() {
                reorder(siblings, moved);
                refresh();
            }
        });

        refresh();
    }

    /// Commits an inline rename: re-keys the component in its host's map, keeping its position. A name
    /// a sibling already holds is suffixed rather than refused.
    private void commitRename(Row row, AdvancedEditBox<String> box) {
        String oldName = row.name();
        String typed = box.getText().trim();

        if (typed.isEmpty() || typed.equals(oldName)) {
            box.setValue(oldName); // nothing to do, or nothing left to name it
            return;
        }

        SequencedMap<String, ComponentDataHolder<?>> siblings = row.host().getComponents();
        List<String> order = List.copyOf(siblings.keySet());

        Map<String, ComponentDataHolder<?>> others = new HashMap<>(siblings);
        others.remove(oldName);
        String newName = ComponentAttachableHolder.uniqueName(typed, others.keySet());

        rekey(siblings, order, oldName, newName);

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                rekey(siblings, order, newName, oldName);
                refresh();
            }

            @Override
            public void redo() {
                rekey(siblings, order, oldName, newName);
                refresh();
            }
        });

        refresh();
    }

    /// Renames a key in place, rebuilding `map` against `order` so the component keeps its position.
    private static void rekey(
        SequencedMap<String, ComponentDataHolder<?>> map, List<String> order, String from, String to
    ) {
        ComponentDataHolder<?> holder = map.remove(from);
        if (holder == null) return;

        map.put(to, holder);
        reorder(map, order.stream().map(name -> name.equals(from) ? to : name).toList());
    }

    /// Restores `map`'s iteration order to `order`. Names it no longer contains are skipped, names it
    /// gained since are appended.
    private static void reorder(SequencedMap<String, ComponentDataHolder<?>> map, List<String> order) {
        LinkedHashMap<String, ComponentDataHolder<?>> ordered = new LinkedHashMap<>();

        for (String name : order) {
            ComponentDataHolder<?> holder = map.get(name);
            if (holder != null) ordered.put(name, holder);
        }

        ordered.putAll(map);

        map.clear();
        map.putAll(ordered);
    }

    /// Focuses a row's name box for inline typing.
    private void beginRename(ComponentDataHolder<?> holder) {
        if (holder.fixedName() != null) return;

        select(holder, hostOf(holder));

        AdvancedEditBox<String> box = nameBoxes.get(holder);
        int index = indexOf(holder);
        if (box == null || index < 0 || index >= table.children().size()) return;

        GuiEventListener entry = table.children().get(index);
        setFocused(table);
        table.setFocused(entry);

        if (entry instanceof ContainerEventHandler container) {
            container.setFocused(box);
        }

        box.setFocused(true);
        box.setCursorPosRightmost();
    }

    private void refresh() {
        // A component no longer in the tree can no longer stay focused
        if (selected != null && ComponentAttachableHolder.flatten(root).noneMatch(h -> h == selected)) {
            select(null, null);
        }

        rebuildList();
        onChanged.run();
    }

    private int rowIndexAt(double mouseY) {
        int bodyTop = rowTop(0);
        if (mouseY < bodyTop) return -1;

        int idx = (int) ((mouseY - bodyTop) / ROW_H);

        return (idx >= 0 && idx < rows.size()) ? idx : -1;
    }

    /// Top edge of the flat row at `index`, in screen pixels.
    private int rowTop(int index) {
        return table.getY() + 2 - (int) table.scrollAmount() + index * ROW_H;
    }

    /// Whether `mouseX` lands on the table's scrollbar, which drags the list rather than a row.
    private boolean overScrollbar(double mouseX) {
        return table.maxScrollAmount() > 0 && mouseX >= table.getRight() - table.scrollbarWidth();
    }

    private boolean overAddButton(double mouseX, double mouseY) {
        return mouseX >= addBtnX && mouseX < addBtnX + ADD_BTN
            && mouseY >= addBtnY && mouseY < addBtnY + ADD_BTN;
    }
}