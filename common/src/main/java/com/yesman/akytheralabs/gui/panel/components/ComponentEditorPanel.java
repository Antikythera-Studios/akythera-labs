package com.yesman.akytheralabs.gui.panel.components;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.client.api.gui.ContextMenuContainer;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akythera.platform.ModPlatform;
import com.yesman.akythera.platform.ModPlatformProvider;
import com.yesman.akythera.mixinaccessor.client.GuiGraphicsAccessor;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.dataholder.ComponentHolderRegistry;
import com.yesman.akytheralabs.dataholder.FailedComponentHolder;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.WorkHistory;
import com.yesman.akytheralabs.editor.keybind.EditorKeyMappings;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsEditBoxTheme;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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
    private static final int SWITCHED_OFF_COLOR = 0xFF4A4A4A; // a row switched off, or under one that is
    private static final int REVERT_BTN = 12; // the ✕ an overriding row carries
    private static final int TOGGLE_X = 4; // left inset of the switch box a row carries
    private static final int TOGGLE_BOX = 8; // side of that switch box
    private static final int TOGGLE_GUTTER = 10; // horizontal inset a row's name leaves for it

    /// The order the attach menu lists mod ids in: [Akythera#MODID] first, the others alphabetically.
    private static final Comparator<String> MOD_ORDER =
        Comparator.comparing((String modId) -> !modId.equals(Akythera.MODID)).thenComparing(Comparator.naturalOrder());

    /// Handed a component released outside the panel, so the host screen can spawn a node reading it.
    @FunctionalInterface
    public interface DropTarget {
        /// @param path the component's names from the root host, joined by [ComponentAttachable#PATH_SEPARATOR]
        /// @return true when the drop landed on a valid target
        boolean onDropped(String path, double screenX, double screenY);
    }

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

    /// Whether rows carry the switch box that sets [ComponentDataHolder#isEnabled].
    private final boolean switchable;

    private final Runnable onChanged;

    /// Fired when the focused component changes, with the host it hangs off; both null when nothing
    /// is focused.
    private final BiConsumer<@Nullable ComponentDataHolder<?>, @Nullable ComponentAttachableHolder> onComponentFocused;

    private final PanelHeader header;
    private final WidgetTable table;

    /// The flattened tree in traversal order, one entry per visible row, rebuilt by [#rebuildList].
    private final List<Row> rows = new ArrayList<>();

    /// The inline name box of each listed component.
    private final Map<ComponentDataHolder<?>, EditorEditBox<String>> nameBoxes = new IdentityHashMap<>();

    /// The holders whose rows read as overrides. Telling one apart from what it overrides serializes
    /// both, so the answer is kept until an edit lands rather than taken per frame.
    private final Set<ComponentDataHolder<?>> overriding = Collections.newSetFromMap(new IdentityHashMap<>());

    /// What a node graph of a listed component threw while the game ran, by component. Flagged like a
    /// failed decode.
    private Map<ComponentDataHolder<?>, Throwable> runtimeErrors = Map.of();

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
    /// already holds, or over one it may not take.
    @Nullable
    private Drop drop;

    @Nullable
    private DropTarget dropTarget;

    /// Told (old path, new path) whenever a component is keyed under a new name, so whatever reads a
    /// component by path follows it. Fires on an undo and a redo of the rename too.
    @Nullable
    private BiConsumer<String, String> renameListener;

    /// The row the current left press began on; null when no press is held.
    @Nullable
    private Row pressed;

    /// The row being dragged out of the panel toward [#dropTarget]; null when no such drag is in
    /// flight. The cursor position the ghost follows is [#exportX] / [#exportY].
    @Nullable
    private Row exported;
    private double exportX;
    private double exportY;

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
    ) {
        /// Whether this row's component and every one it hangs under is switched on.
        boolean switchedOn() {
            return holder.isEnabled() && (parent == null || parent.switchedOn());
        }

        /// The names from the root host down to this row, joined by [ComponentAttachable#PATH_SEPARATOR].
        String path() {
            return parent == null ? name : parent.path() + ComponentAttachable.PATH_SEPARATOR + name;
        }
    }

    /// A copy of an inherited component the Details panel is showing, where it lands once something is
    /// written into it, and the component it was copied from — which is what it overrides.
    private record Claim(
        ComponentAttachableHolder host, String name, ComponentDataHolder<?> holder, ComponentDataHolder<?> inherited
    ) {}

    /// Where a dragged row can land.
    private sealed interface Drop {
        /// At `index` of `host`'s key order, counted with the dragged component still in place. The separator
        /// draws above the flat row `row`, indented to `depth`.
        record Gap(ComponentAttachableHolder host, int index, int row, int depth) implements Drop {}

        /// Last under `host`, the component listed at the flat row `row`.
        record Into(ComponentAttachableHolder host, int row) implements Drop {}
    }

    public ComponentEditorPanel(
        Font font,
        Screen parentScreen,
        Component title,
        ComponentHolderRegistry registry,
        ComponentAttachableHolder root,
        @Nullable Class<? extends EntityRenderState> hostRenderState,
        Supplier<? extends Map<String, ComponentDataHolder<?>>> inherited,
        boolean switchable,
        Runnable onChanged,
        BiConsumer<@Nullable ComponentDataHolder<?>, @Nullable ComponentAttachableHolder> onComponentFocused
    ) {
        this.font = font;
        this.registry = registry;
        this.root = root;
        this.hostRenderState = hostRenderState;
        this.inherited = inherited;
        this.switchable = switchable;
        this.onChanged = onChanged;
        this.onComponentFocused = onComponentFocused;
        this.header = new PanelHeader(font, title);
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

    /// Replaces the runtime errors the rows are flagged with. An empty map clears them.
    public void setRuntimeErrors(Map<ComponentDataHolder<?>, Throwable> runtimeErrors) {
        this.runtimeErrors = runtimeErrors;
    }

    /// Wires the callback a row dragged out of the panel is released to.
    public void setDropTarget(@Nullable DropTarget dropTarget) {
        this.dropTarget = dropTarget;
    }

    /// Wires the callback told about a component's rename, see [#renameListener].
    public void setRenameListener(@Nullable BiConsumer<String, String> renameListener) {
        this.renameListener = renameListener;
    }

    public void rebuildList() {
        syncFixedNames(root, "");

        table.clearWidgets();
        rows.clear();
        nameBoxes.clear();

        collectRows(root, null, inherited.get(), 0);

        for (Row row : rows) {
            EditorEditBox<String> nameBox = nameBox(row);
            nameBoxes.put(row.holder(), nameBox);
            table.newRow().addWidget(nameBox);
        }

        refreshOverrides();

        table.initialize(false);
        if (lastRect != null) table.relocate(lastRect);
    }

    /// Re-reads which rows declare something of their own and recolors their name boxes. A row that is
    /// switched off, or hangs under one that is, reads [#SWITCHED_OFF_COLOR] whatever it declares.
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

            EditorEditBox<String> box = nameBoxes.get(row.holder());
            if (box == null) continue;

            if (!row.switchedOn()) box.setTextColor(SWITCHED_OFF_COLOR);
            else box.setTextColor(override ? OVERRIDE_COLOR : INHERITED_COLOR);
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

    /// One listed component: the holder, whether it comes from an ancestor patch with no override of
    /// its own here, and the holder it is listed under — null at the root.
    public record ListedComponent(ComponentDataHolder<?> holder, boolean inherited, @Nullable ComponentAttachableHolder host) {}

    /// Every component this panel lists, in row order: this patch's own, plus the inherited ones it
    /// declares no override for.
    ///
    /// A row being previewed through a claim reports the **copy** [#prepareClaim] set aside, marked
    /// as the patch's own — that copy is what the Details rows edit, and what anything else showing
    /// the row has to edit with them.
    public Stream<ListedComponent> listedComponents() {
        return rows.stream().map(row -> pending.stream()
            .filter(claim -> claim.inherited() == row.holder())
            .<ListedComponent>map(claim -> new ListedComponent(claim.holder(), false, claim.host()))
            .findFirst()
            .orElseGet(() -> new ListedComponent(row.holder(), row.inherited(), row.host())));
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
    private boolean syncFixedNames(ComponentAttachableHolder host, String prefix) {
        SequencedMap<String, ComponentDataHolder<?>> children = host.getComponents();
        boolean moved = false;

        for (String name : List.copyOf(children.keySet())) {
            ComponentDataHolder<?> holder = children.get(name);
            String fixed = holder.fixedName();
            String keyed = name;

            if (fixed != null && !fixed.equals(name)) {
                Map<String, ComponentDataHolder<?>> others = new HashMap<>(children);
                others.remove(name);
                String unique = ComponentAttachableHolder.uniqueName(fixed, others.keySet());

                if (!unique.equals(name)) {
                    rekey(children, List.copyOf(children.keySet()), name, unique);
                    renamed(prefix + name, prefix + unique);
                    keyed = unique;
                    moved = true;
                }
            }

            if (holder instanceof ComponentAttachableHolder child) {
                moved |= syncFixedNames(child, prefix + keyed + ComponentAttachable.PATH_SEPARATOR);
            }
        }

        return moved;
    }

    /// Hands a rename to [#renameListener], if one is wired.
    private void renamed(String fromPath, String toPath) {
        if (renameListener != null) renameListener.accept(fromPath, toPath);
    }

    /// Rebuilds the rows once a fixed name has drifted from what its component now derives.
    ///
    /// A derived name changes wherever its source does — a stance picked in the Details panel, an undo,
    /// a data block renamed elsewhere in the project — and none of those reach this panel, so the tree
    /// is re-checked as it draws rather than pushed at from every writer.
    private void refreshFixedNames() {
        if (syncFixedNames(root, "")) rebuildList();
    }

    /// The row's name, editable in place and transparent until focused. A fixed-name row is read-only,
    /// [#syncFixedNames] having already keyed it under what it derives; so is an inherited row, which
    /// belongs to an ancestor patch.
    private EditorEditBox<String> nameBox(Row row) {
        boolean fixed = row.holder().fixedName() != null;

        EditorEditBox<String> box = new EditorEditBox<>(
            font,
            AnchoredWidget.pixel(nameLeft(row.depth())), AnchoredWidget.pixel(15),
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
    /// literal, and the separator of a component path.
    private static boolean isValidComponentName(String s) {
        return s.indexOf('"') < 0 && s.indexOf('\\') < 0 && !s.contains(ComponentAttachable.PATH_SEPARATOR);
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
        extractToggles(graphics, mouseX, mouseY);
        extractFailureMarks(graphics, mouseX, mouseY);

        // Deferred to the frame's tail so the ghost draws over the panel it is dropped on
        if (exported != null) {
            String name = exported.name();
            int x = (int) exportX + 8;
            int y = (int) exportY + 8;
            int w = font.width(name) + 12;

            ((GuiGraphicsAccessor) graphics).akythera$setDeferredWidgetRenderer(() -> {
                graphics.fill(x - 1, y - 1, x + w + 1, y + ROW_H + 1, 0xAA000000);
                graphics.fill(x, y, x + w, y + ROW_H, 0xFF2B2B2B);
                graphics.outline(x, y, w, ROW_H, DROP_LINE_COLOR);
                graphics.text(font, Component.literal(name), x + 6, y + (ROW_H - font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);
            });
        }
    }

    /// The switch box of every row [#hasToggle] gives one, filled while its component starts switched
    /// on. Drawn here for the same reason as [#extractRevertMarks].
    private void extractToggles(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < rows.size(); i++) {
            if (!hasToggle(i)) continue;

            ComponentDataHolder<?> holder = rows.get(i).holder();

            int top = rowTop(i);
            if (top < table.getY() || top + ROW_H > table.getBottom()) continue;

            int x = getX() + TOGGLE_X;
            int y = top + (ROW_H - TOGGLE_BOX) / 2;

            graphics.fill(x, y, x + TOGGLE_BOX, y + TOGGLE_BOX, overToggle(i, mouseX, mouseY) ? 0xFFFFFFFF : 0xFFA0A0A0);
            graphics.fill(x + 1, y + 1, x + TOGGLE_BOX - 1, y + TOGGLE_BOX - 1, 0xFF000000);

            if (holder.isEnabled()) {
                graphics.fill(x + 2, y + 2, x + TOGGLE_BOX - 2, y + TOGGLE_BOX - 2, 0xFFFFFFFF);
            }
        }
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

    /// Where the row being dragged would land: a line indented to the level it drops into, or an outline on
    /// the container it drops under.
    private void extractDropSeparator(GuiGraphicsExtractor graphics) {
        if (dragged == null || drop == null) return;

        switch (drop) {
            case Drop.Gap gap -> {
                int y = rowTop(gap.row());
                if (y < table.getY() || y > table.getBottom()) return;

                graphics.fill(getX() + nameLeft(gap.depth()), y - 1, getRight() - 2, y + 1, DROP_LINE_COLOR);
            }
            case Drop.Into into -> graphics.outline(getX(), rowTop(into.row()), getRight() - getX(), ROW_H, DROP_LINE_COLOR);
        }
    }

    /// Red outline on every row whose component holder failed to deserialize or whose node graph threw
    /// at runtime, plus a hover tooltip carrying the failure.
    private void extractFailureMarks(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ComponentDataHolder<?> hoveredHolder = null;

        for (int i = 0; i < rows.size(); i++) {
            ComponentDataHolder<?> holder = rows.get(i).holder();

            if (holder.getDeserializationFailureException() == null && !runtimeErrors.containsKey(holder)) continue;

            int top = rowTop(i);
            graphics.outline(getX(), top, getRight() - getX(), ROW_H, AbstractNodeWidget.COLOR_ERROR_OUTLINE);

            if (mouseX >= getX() && mouseX < getRight() && mouseY >= top && mouseY < top + ROW_H) {
                hoveredHolder = holder;
            }
        }

        if (hoveredHolder != null) {
            drawFailureTooltip(graphics, hoveredHolder, mouseX, mouseY);
        }
    }

    private void drawFailureTooltip(GuiGraphicsExtractor graphics, ComponentDataHolder<?> holder, int mouseX, int mouseY) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        Exception decodeFailure = holder.getDeserializationFailureException();
        Throwable runtimeError = runtimeErrors.get(holder);

        if (decodeFailure != null) {
            lines.addAll(font.split(
                Component.literal("This component was malformed and failed to deserialize; its values were reset to defaults."),
                220
            ));

            String message = decodeFailure.getMessage();

            if (message != null && !message.isBlank()) {
                lines.addAll(font.split(Component.literal(message), 220));
            }
        }

        if (runtimeError != null) {
            lines.addAll(font.split(
                Component.literal("A node graph of this component threw while the game ran. Open the graph to see the node."),
                220
            ));
            lines.addAll(font.split(Component.literal(runtimeError.toString()), 220));
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

        if (event.button() == 0 && index >= 0 && hasToggle(index) && overToggle(index, event.x(), event.y())) {
            toggleComponent(rows.get(index));
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
        EditorEditBox<String> box = nameBoxes.get(row.holder());
        if (box != null && box.isFocused()) {
            return super.mouseClicked(event, doubleClick);
        }

        if (doubleClick && !row.inherited()) {
            beginRename(row.holder());
        } else if (!overScrollbar(event.x())) {
            pressed = row;

            if (!row.inherited() && row.host() != null) dragged = row.holder();
        }

        return true;
    }

    /// Delete removes the focused component, unless a name is being typed.
    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        boolean renaming = nameBoxes.values().stream().anyMatch(EditorEditBox::isFocused);

        if (!renaming && selected != null && EditorKeyMappings.DELETE.matches(event)) {
            removeListedComponent(selected);
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy) {
        boolean outside = event.x() < getX() || event.x() >= getRight() || event.y() < getY() || event.y() >= getBottom();

        if (exported == null && pressed != null && dropTarget != null && outside) {
            exported = pressed;
            dragged = null;
            drop = null;
        }

        if (exported != null) {
            exportX = event.x();
            exportY = event.y();
            return true;
        }

        if (dragged == null) {
            return super.mouseDragged(event, dx, dy);
        }

        drop = dropAt(event.y());

        return true;
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        ComponentDataHolder<?> moving = dragged;
        Drop landing = drop;
        Row dropped = exported;

        dragged = null;
        drop = null;
        pressed = null;
        exported = null;

        if (dropped != null && dropTarget != null) {
            setDragging(false);
            dropTarget.onDropped(dropped.path(), event.x(), event.y());
            return true;
        }

        if (moving != null && landing != null) {
            setDragging(false);

            switch (landing) {
                case Drop.Gap gap -> moveComponent(moving, gap.host(), gap.index());
                case Drop.Into into -> moveComponent(moving, into.host(), into.host().getComponents().size());
            }

            return true;
        }

        return super.mouseReleased(event);
    }

    /// Where releasing the drag at `mouseY` would put the dragged row: under the row there when the cursor sits on
    /// the middle half of a container taking it, else above that row (upper half) or below it (lower half), a
    /// container showing children taking it first among them. Null where the row would stay as it is, or may
    /// not go: under itself, under an inherited row, or under a host refusing it. A row overriding an inherited
    /// one stays among its siblings.
    private @Nullable Drop dropAt(double mouseY) {
        Row row = rowOf(dragged);
        if (row == null || row.host() == null) return null;

        int draggedIndex = indexOf(dragged);
        int index = rowIndexAt(mouseY);
        Drop landing;

        if (index < 0) {
            landing = mouseY < rowTop(0) ? null : new Drop.Gap(root, root.getComponents().size(), rows.size(), 0);
        } else if (index >= draggedIndex && index < subtreeEnd(draggedIndex) || rows.get(index).host() == null) {
            landing = null;
        } else {
            Row target = rows.get(index);
            float offset = (float) (mouseY - rowTop(index)) / ROW_H;
            int position = List.copyOf(target.host().getComponents().keySet()).indexOf(target.name());
            ComponentAttachableHolder container = target.holder() instanceof ComponentAttachableHolder attachable && takes(attachable, row) ? attachable : null;

            if (container != null && offset >= 0.25F && offset < 0.75F) {
                landing = new Drop.Into(container, index);
            } else if (offset < 0.5F) {
                landing = new Drop.Gap(target.host(), position, index, target.depth());
            } else if (container != null && subtreeEnd(index) > index + 1) {
                landing = new Drop.Gap(container, 0, index + 1, target.depth() + 1);
            } else {
                landing = new Drop.Gap(target.host(), position + 1, subtreeEnd(index), target.depth());
            }
        }

        ComponentAttachableHolder host = switch (landing) {
            case Drop.Gap gap -> gap.host();
            case Drop.Into into -> into.host();
            case null -> null;
        };

        if (host == null || host != row.host() && (row.shadowed() != null || !takes(host, row))) return null;

        int from = List.copyOf(row.host().getComponents().keySet()).indexOf(row.name());
        int to = landing instanceof Drop.Gap gap ? gap.index() : host.getComponents().size();

        return host == row.host() && (to == from || to == from + 1) ? null : landing;
    }

    /// Whether `host` may take `row`'s component: a type it accepts, not refused under it.
    private boolean takes(ComponentAttachableHolder host, Row row) {
        Identifier typeId = registry.typeIdOrNull(row.holder());

        return typeId != null
            && host.accepts(row.holder().getClass())
            && acceptsRenderState(typeId)
            && registry.attachRefusal(typeId, host, root) == null;
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
        fillAttachEntries(builder, host, _ -> {});
        ModalManager.getInstance().openModalWidget(builder.build(), x, y);
    }

    /// Lists what `host` can take: the types under a title row per mod, or in one submenu per category with the
    /// ready-made components when [#registry] has a [ComponentHolderRegistry.AttachMenu]. A pick runs `configure` on
    /// the new component before attaching it, in the same undo step.
    public void fillAttachEntries(ContextMenuContainer.PageBuilder builder, ComponentAttachableHolder host, Consumer<ComponentDataHolder<?>> configure) {
        List<Identifier> available = attachableTypes(host);
        ComponentHolderRegistry.AttachMenu menu = registry.attachMenu();
        List<ComponentHolderRegistry.Preset> presets = menu == null ? List.of() : menu.presets(root).stream()
            .filter(preset -> available.contains(preset.typeId()))
            .toList();

        if (available.isEmpty()) {
            builder.entry()
                .message(Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MENU_NO_COMPONENTS))
                .onPress(_ -> {})
                .done();
            return;
        }

        if (menu == null) {
            Map<String, List<Identifier>> byMod = available.stream()
                .collect(Collectors.groupingBy(Identifier::getNamespace, () -> new TreeMap<>(MOD_ORDER), Collectors.toList()));

            byMod.forEach((modId, types) -> {
                builder.header(modTitle(modId));

                for (Identifier id : types) {
                    attachEntry(builder, host, id, Component.literal(ParseUtils.toDisplayName(id.getPath())), () -> attachComponent(host, id, configure));
                }
            });

            return;
        }

        for (Component category : menu.categories()) {
            List<Identifier> types = available.stream().filter(id -> category.equals(menu.categoryOf(id))).toList();
            List<ComponentHolderRegistry.Preset> ready = presets.stream().filter(preset -> preset.category().equals(category)).toList();

            if (types.isEmpty() && ready.isEmpty()) continue;

            ContextMenuContainer.PageBuilder page = builder.entry().message(category).subpage();

            for (Identifier id : types) {
                attachEntry(page, host, id, Component.literal(ParseUtils.toDisplayName(id.getPath())), () -> attachComponent(host, id, configure));
            }

            Map<String, List<ComponentHolderRegistry.Preset>> readyByMod = ready.stream()
                .collect(Collectors.groupingBy(ComponentHolderRegistry.Preset::modId, () -> new TreeMap<>(MOD_ORDER), Collectors.toList()));

            readyByMod.forEach((modId, modPresets) -> {
                page.header(modTitle(modId));

                for (ComponentHolderRegistry.Preset preset : modPresets) {
                    attachEntry(page, host, preset.typeId(), preset.label(), () -> {
                        ComponentDataHolder<?> holder = preset.factory().get();

                        configure.accept(holder);
                        attach(host, holder, ComponentAttachableHolder.uniqueName(preset.name(), host.getComponents().keySet()));
                    });
                }
            });

            page.done();
        }
    }

    /// The title row of a mod: its display name, or its id spelled out for a mod that is not loaded.
    private static Component modTitle(String modId) {
        ModPlatform.ModInfo mod = ModPlatformProvider.get().getLoadedModInfo(modId);

        return Component.literal(mod == null ? ParseUtils.toDisplayName(modId) : mod.displayName());
    }

    /// One attach menu entry: `label`, running `attach` on press, or greyed and naming why when `typeId` is refused
    /// under `host`.
    private void attachEntry(ContextMenuContainer.PageBuilder builder, ComponentAttachableHolder host, Identifier typeId, Component label, Runnable attach) {
        Component refusal = registry.attachRefusal(typeId, host, root);

        builder.entry()
            .message(refusal == null ? label : label.copy().withStyle(ChatFormatting.DARK_GRAY))
            .onPress(_ -> {
                if (refusal == null) attach.run();
                else RefusalPopup.open(Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_POPUP_ATTACH_REFUSED_TITLE), refusal);
            })
            .done();
    }

    /// Row menu: attach (only when the row's component hosts children at all), rename, remove.
    private void openRowMenu(Row row, int x, int y) {
        ContextMenuContainer.PageBuilder builder = ContextMenuContainer.builder(Minecraft.getInstance()).pageWidth(150);

        if (row.holder() instanceof ComponentAttachableHolder host && !host.attachableTypes().isEmpty()) {
            ContextMenuContainer.PageBuilder subpage = builder.entry()
                .message(Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MENU_ATTACH_COMPONENT))
                .subpage();

            fillAttachEntries(subpage, host, _ -> {});
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

        ModalManager.getInstance().openModalWidget(builder.build(), x, y);
    }

    // ***************************************************************
    // Structural edits
    // ***************************************************************

    private void attachComponent(ComponentAttachableHolder host, Identifier typeId, Consumer<ComponentDataHolder<?>> configure) {
        ComponentDataHolder<?> holder = registry.createDefault(typeId, host, root);
        if (holder == null) return;

        String fixed = holder.fixedName();

        configure.accept(holder);
        attach(host, holder, fixed == null
            ? ComponentAttachableHolder.defaultName(typeId, host.getComponents().keySet())
            : ComponentAttachableHolder.uniqueName(fixed, host.getComponents().keySet()));
    }

    /// Attaches `holder` under `host` as `name`, as one undo step, and focuses it.
    private void attach(ComponentAttachableHolder host, ComponentDataHolder<?> holder, String name) {
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

    /// Removes a listed component and everything attached under it, as its row's Remove does. A component
    /// another patch declares, or one overriding it, stays.
    public void removeListedComponent(ComponentDataHolder<?> holder) {
        Row row = rowOf(holder);

        if (row != null && !row.inherited() && row.shadowed() == null) removeComponent(row);
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

    /// Whether the row at `index` carries the switch box: the panel shows switches, and the component is
    /// this patch's own and one this build knows. A [FailedComponentHolder] re-emits its raw form.
    private boolean hasToggle(int index) {
        Row row = rows.get(index);

        return switchable && !row.inherited() && !(row.holder() instanceof FailedComponentHolder);
    }

    /// The switch box's click box on the row at `index` — the strip left of the top-level names.
    private boolean overToggle(int index, double mouseX, double mouseY) {
        int top = rowTop(index);

        return mouseX >= getX() && mouseX < getX() + nameLeft(0) && mouseY >= top && mouseY < top + ROW_H;
    }

    /// Flips whether a component starts switched on.
    private void toggleComponent(Row row) {
        ComponentDataHolder<?> target = row.holder();
        select(target, row.host());

        boolean enabled = !target.isEnabled();
        Consumer<Boolean> writer = value -> {
            target.setEnabled(value);
            overridingStale = true;
            onChanged.run();
        };

        HistoryManager.getInstance().pushHistory(new DataChange<>(!enabled, enabled, writer));
        writer.accept(enabled);
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

    /// Moves a component to `targetIndex` in `target`'s key order, counted with the component still in place. Into
    /// another host it is keyed under a name free there, and whatever reads it by path follows.
    private void moveComponent(ComponentDataHolder<?> holder, ComponentAttachableHolder target, int targetIndex) {
        Row row = rowOf(holder);
        if (row == null) return;

        ComponentAttachableHolder host = row.host();
        if (host == null) return;

        if (target != host) {
            SequencedMap<String, ComponentDataHolder<?>> from = host.getComponents();
            SequencedMap<String, ComponentDataHolder<?>> to = target.getComponents();
            List<String> fromOrder = List.copyOf(from.keySet());
            String name = row.name();
            String newName = ComponentAttachableHolder.uniqueName(name, to.keySet());
            Row targetRow = target instanceof ComponentDataHolder<?> targetHolder ? rowOf(targetHolder) : null;
            String oldPath = row.path();
            String newPath = targetRow == null ? newName : targetRow.path() + ComponentAttachable.PATH_SEPARATOR + newName;

            List<String> placed = new ArrayList<>(to.keySet());
            placed.add(Math.min(targetIndex, placed.size()), newName);
            List<String> toOrder = List.copyOf(placed);

            Runnable apply = () -> {
                from.remove(name);
                to.put(newName, holder);
                reorder(to, toOrder);
                renamed(oldPath, newPath);
            };

            apply.run();

            HistoryManager.getInstance().pushHistory(new WorkHistory() {
                @Override
                public void undo() {
                    to.remove(newName);
                    from.put(name, holder);
                    reorder(from, fromOrder);
                    renamed(newPath, oldPath);
                    refresh();
                }

                @Override
                public void redo() {
                    apply.run();
                    refresh();
                }
            });

            refresh();
            return;
        }

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
    private void commitRename(Row row, EditorEditBox<String> box) {
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

        String prefix = row.parent() == null ? "" : row.parent().path() + ComponentAttachable.PATH_SEPARATOR;
        String oldPath = prefix + oldName;
        String newPath = prefix + newName;

        rekey(siblings, order, oldName, newName);
        renamed(oldPath, newPath);

        HistoryManager.getInstance().pushHistory(new WorkHistory() {
            @Override
            public void undo() {
                rekey(siblings, order, newName, oldName);
                renamed(newPath, oldPath);
                refresh();
            }

            @Override
            public void redo() {
                rekey(siblings, order, oldName, newName);
                renamed(oldPath, newPath);
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

        EditorEditBox<String> box = nameBoxes.get(holder);
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

    /// Left inset of a name at `depth`, in pixels from the panel's left edge.
    private int nameLeft(int depth) {
        return 6 + (switchable ? TOGGLE_GUTTER : 0) + depth * INDENT;
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