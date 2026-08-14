package com.yesman.akytheralabs.gui.panel.components;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.client.gui.widget.WidgetTable;
import com.yesman.akythera.client.gui.widget.common.AnchoredWidget;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
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
import com.yesman.akytheralabs.gui.widget.script.AbstractNodeWidget;
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
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

/// "Components" panel (top-left of the patch editors): a [PanelHeader] with a `+` add button, over a
/// [WidgetTable] listing the patch's component **tree** — one row per component, indented under the
/// component it is attached to.
///
/// Since the engine keys a [com.yesman.akythera.api.component.ComponentAttachable]'s components by
/// name, a row's name *is* the component's identity: it shows as an editable box (transparent until
/// focused, like the Variables panel) and a commit re-keys it in its parent's map. Sibling names are
/// kept unique by [ComponentAttachableHolder#uniqueName], which suffixes `_1`, `_2`, ... rather than
/// rejecting the author's typing.
///
/// The `+` button attaches to the patch itself; a right-click on a row offers "Attach Component" for
/// that row's own children, plus "Rename" / "Remove". Both menus offer only what the host in question
/// accepts — [com.yesman.akytheralabs.akythera.entitypatch.component.RootAttachableComponent] at the
/// root, the host's [ComponentAttachableHolder#attachableTypes] under a component — which is what
/// keeps a bone socket out of the root list and inside an armature's.
///
/// Every edit goes onto the [HistoryManager] undo stack, so the title's unsaved-changes `*` tracks
/// it, and calls [#onChanged]. Edits land on the holder tree directly, as the Details panel's value
/// edits do; the quit path discards them by reloading from disk.
///
/// Note on filtering: the blueprint only accepts components whose owner is `EntityPatch<Entity>`, but
/// that bound (`ComponentBlueprint<?, EntityPatch<Entity>>`) is erased at runtime, so a `MapCodec`
/// cast cannot select them. Instead, the menus offer only the types tagged
/// [ComponentBlueprint#ENTITY_PATCH_COMPONENT_TAG]. The tag is bound from data pack tag files
/// (`data/<namespace>/tags/component_blueprint_key/entity_patch.json`) on world load, and the editor
/// always runs with a singleplayer world loaded, so it is available here.
public final class ComponentEditorPanel extends AdjustablePanelManager.PanelArea {
    private static final int ADD_BTN = 16;
    private static final int ROW_H = 18;
    /// Horizontal inset one level of attachment adds to a row's name.
    private static final int INDENT = 10;

    private final Font font;

    /// The component-type tag this panel's menus offer
    /// ([ComponentBlueprint#ENTITY_PATCH_COMPONENT_TAG] / [ComponentBlueprint#ITEM_PATCH_COMPONENT_TAG]).
    private final TagKey<MapCodec<? extends ComponentBlueprint<?, ?>>> componentTag;

    /// The editor holders of the patch kind being edited — supplies the default holder for a freshly
    /// attached component, and the holder class each suggestion filter tests.
    private final ComponentHolderRegistry registry;

    /// The patch itself: the tree's root host. Its children are the top-level components.
    private final ComponentAttachableHolder root;

    /// The render state the patched entity is drawn through, or null when the patch kind has no
    /// entity behind it — an item patch, or an entity type with no renderer.
    private final @Nullable Class<? extends EntityRenderState> hostRenderState;

    private final Runnable onChanged;

    /// Fired when the focused (left-clicked) component changes; null = nothing focused. The screen
    /// routes this into the Details panel, host included: a component's rows can need the host to know
    /// what to offer (a bone socket's bone list comes from its parent armature component).
    private final BiConsumer<@Nullable ComponentDataHolder<?>, @Nullable ComponentAttachableHolder> onComponentFocused;

    private final PanelHeader header;
    private final WidgetTable table;

    /// The flattened tree, one entry per visible row, rebuilt by [#rebuildList]. Row order is the
    /// traversal order, so a row index maps straight to a component.
    private final List<Row> rows = new ArrayList<>();

    /// Name boxes by row holder, so "Rename" can focus the right row's box for inline editing.
    private final Map<ComponentDataHolder<?>, AdvancedEditBox<String>> nameBoxes = new IdentityHashMap<>();

    @Nullable
    private ComponentDataHolder<?> selected;

    @Nullable
    private ScreenRectangle lastRect;
    private int addBtnX;
    private int addBtnY;

    /// One listed component: the host it hangs off, the name it is attached under, and its holder.
    /// `depth` is only how far to indent — the structure itself lives in the holders.
    private record Row(int depth, String name, ComponentDataHolder<?> holder, ComponentAttachableHolder host) {}

    public ComponentEditorPanel(
        Font font,
        Screen parentScreen,
        TagKey<MapCodec<? extends ComponentBlueprint<?, ?>>> componentTag,
        ComponentHolderRegistry registry,
        ComponentAttachableHolder root,
        @Nullable Class<? extends EntityRenderState> hostRenderState,
        Runnable onChanged,
        BiConsumer<@Nullable ComponentDataHolder<?>, @Nullable ComponentAttachableHolder> onComponentFocused
    ) {
        this.font = font;
        this.componentTag = componentTag;
        this.registry = registry;
        this.root = root;
        this.hostRenderState = hostRenderState;
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
        table.clearWidgets();
        rows.clear();
        nameBoxes.clear();

        collectRows(root, 0);

        for (Row row : rows) {
            AdvancedEditBox<String> nameBox = nameBox(row);
            nameBoxes.put(row.holder(), nameBox);
            table.newRow().addWidget(nameBox);
        }

        table.initialize(false);
        if (lastRect != null) table.relocate(lastRect);
    }

    /// Depth-first walk of the holder tree, parents immediately before their children — the reading
    /// order of the list and the order [#rowIndexAt] resolves clicks against.
    private void collectRows(ComponentAttachableHolder host, int depth) {
        host.getComponents().forEach((name, holder) -> {
            rows.add(new Row(depth, name, holder, host));
            if (holder instanceof ComponentAttachableHolder child) collectRows(child, depth + 1);
        });
    }

    /// The row's name, editable in place. Transparent until focused, so the list reads as labels
    /// until the author actually renames something.
    private AdvancedEditBox<String> nameBox(Row row) {
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
        box.addCharReplacement(' ', '_');
        box.setFormatter(name -> name + " (" + row.holder().generateEventually().getSimpleName() + ")");
        box.setOnCommitted((_, _) -> commitRename(row, box));

        return box;
    }

    /// Name filter: the engine looks a component up by this string and the source export turns it
    /// into a Java string literal, so anything but quotes and backslashes is fair game — but a name
    /// with leading or trailing space is a trap nobody can see, and [#commitRename] trims it.
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
        graphics.fill(getX(), getY(), getRight(), getBottom(), bgColor);

        // Focused-row highlight, under the table's row content.
        int selectedRow = indexOf(selected);

        if (selectedRow >= 0) {
            int rowTop = table.getY() + 2 + selectedRow * ROW_H;
            graphics.fill(getX(), rowTop, getRight(), rowTop + ROW_H, 0xFF0A2833);
        }

        header.extractRenderState(graphics, mouseX, mouseY, a);
        table.extractRenderState(graphics, mouseX, mouseY, a);

        boolean hovered = overAddButton(mouseX, mouseY);
        graphics.fill(addBtnX, addBtnY, addBtnX + ADD_BTN, addBtnY + ADD_BTN, hovered ? 0xFF3A3A3A : 0xFF2A2A2A);
        graphics.text(
            font, Component.literal("+"),
            addBtnX + (ADD_BTN - font.width("+")) / 2,
            addBtnY + (ADD_BTN - font.lineHeight) / 2,
            0xFFCCCCCC, false
        );

        extractFailureMarks(graphics, mouseX, mouseY);
    }

    /// Red outline on every row whose component holder failed to deserialize (its values were
    /// reset to defaults, or — unknown type — it is an inert raw placeholder), plus a hover
    /// tooltip carrying the failure. Read live off the holders, so the marks drop the moment a
    /// save resolves them.
    private void extractFailureMarks(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Exception hoveredFailure = null;

        for (int i = 0; i < rows.size(); i++) {
            Exception failure = rows.get(i).holder().getDeserializationFailureException();
            if (failure == null) continue;

            int rowTop = table.getY() + 2 + i * ROW_H;
            graphics.outline(getX(), rowTop, getRight() - getX(), ROW_H, AbstractNodeWidget.COLOR_ERROR_OUTLINE);

            if (mouseX >= getX() && mouseX < getRight() && mouseY >= rowTop && mouseY < rowTop + ROW_H) {
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

        if (event.button() == 1) { // right-click → row menu
            if (index >= 0) {
                Row row = rows.get(index);
                select(row.holder(), row.host());
                openRowMenu(row, (int) event.x(), (int) event.y());
            }
            return true;
        }

        if (event.button() != 0 || event.y() < table.getY()) {
            return super.mouseClicked(event, doubleClick);
        }

        if (index < 0) { // left-click on empty space → clear focus
            select(null, null);
            return true;
        }

        Row row = rows.get(index);
        select(row.holder(), row.host());

        // A press on a name box already being renamed repositions its cursor instead.
        AdvancedEditBox<String> box = nameBoxes.get(row.holder());
        if (box != null && box.isFocused()) {
            return super.mouseClicked(event, doubleClick);
        }

        if (doubleClick) beginRename(row.holder());

        return true;
    }

    /// Focuses an already-listed component from outside the panel — the 3D preview picking one.
    /// No-op when it is already focused, which is what keeps a focus round-tripping between the two
    /// views from recursing.
    public void focusComponent(@Nullable ComponentDataHolder<?> holder) {
        select(holder, holder == null ? null : hostOf(holder));
    }

    /// Re-fires the focus callback with the current selection, past [#select]'s identity guard — for a
    /// Details panel another view borrowed and now hands back.
    public void refocusDetails() {
        onComponentFocused.accept(selected, selected == null ? null : hostOf(selected));
    }

    /// Focuses a component, reporting the host it hangs off with it. `host` is passed rather than
    /// looked up because a freshly attached component isn't in [#rows] yet — the list rebuilds after
    /// the focus, not before it.
    private void select(@Nullable ComponentDataHolder<?> holder, @Nullable ComponentAttachableHolder host) {
        if (selected == holder) return;
        selected = holder;
        onComponentFocused.accept(holder, host);
    }

    /// The host of an already-listed component, for the paths that hold nothing but the holder.
    private @Nullable ComponentAttachableHolder hostOf(ComponentDataHolder<?> holder) {
        for (Row row : rows) {
            if (row.holder() == holder) return row.host();
        }

        return null;
    }

    private int indexOf(@Nullable ComponentDataHolder<?> holder) {
        if (holder == null) return -1;

        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).holder() == holder) return i;
        }

        return -1;
    }

    /// ***************************************************************
    /// Menus
    /// ***************************************************************

    /// The component types `host` accepts and this patch kind offers, that also have an editor
    /// holder registered — the menu can only create what it can build a holder for.
    private List<Identifier> attachableTypes(ComponentAttachableHolder host) {
        if (host.attachableTypes().isEmpty()) return List.of();

        return AkytheraRegistries.Frozen.COMPONENT_BLUEPRINT_TYPES.listElements()
            .filter(entry -> entry.is(componentTag))
            .map(entry -> entry.key().identifier())
            .filter(id -> {
                Class<?> holderClass = registry.holderClass(id);
                return holderClass != null && host.accepts(holderClass);
            })
            .filter(this::acceptsRenderState)
            .sorted(Comparator.comparing(Identifier::toString))
            .toList();
    }

    /// Whether `typeId`'s component can draw through this patch's render state, under the same test
    /// `PatchModelRenderer#submit` applies at runtime. A component declaring no state, and a patch
    /// with no known state, both pass.
    private boolean acceptsRenderState(Identifier typeId) {
        Class<? extends EntityRenderState> componentState = registry.renderStateOf(typeId);

        return hostRenderState == null || componentState == null || componentState.isAssignableFrom(hostRenderState);
    }

    /// Opens the "what may be attached here" menu for one host — the header `+` (the patch) and a
    /// row's "Attach Component" submenu are the same list, asked of different hosts.
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
            builder.entry()
                .message(Component.literal(id.getPath()))
                .onPress(_ -> attachComponent(host, id))
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

        builder.entry()
            .message(Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MENU_RENAME))
            .onPress(_ -> beginRename(row.holder()))
            .done();

        builder.entry()
            .message(Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_MENU_REMOVE))
            .onPress(_ -> removeComponent(row))
            .done();

        AkytheraEditorLayoutManager.getInstance().openModalWidget(builder.build(), x, y);
    }

    /// ***************************************************************
    /// Structural edits
    /// ***************************************************************

    private void attachComponent(ComponentAttachableHolder host, Identifier typeId) {
        ComponentDataHolder<?> holder = registry.createDefault(typeId);
        if (holder == null) return;

        String name = ComponentAttachableHolder.defaultName(typeId, host.getComponents().keySet());

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

    /// Removes a component and everything attached under it — the children live on the holder, so
    /// re-inserting the same holder on undo brings the whole subtree back with it.
    private void removeComponent(Row row) {
        SequencedMap<String, ComponentDataHolder<?>> siblings = row.host().getComponents();
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

    /// Commits an inline rename: re-keys the component in its host's map, keeping its position.
    /// A name already taken by a sibling is suffixed rather than refused, so typing never dead-ends
    /// — the box shows what the component ended up called.
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

    /// Renames a key in place: a [SequencedMap] has no re-key, and a plain remove + put would drop
    /// the component to the end of the list, so the map is rebuilt against the original ordering.
    private static void rekey(
        SequencedMap<String, ComponentDataHolder<?>> map, List<String> order, String from, String to
    ) {
        ComponentDataHolder<?> holder = map.remove(from);
        if (holder == null) return;

        map.put(to, holder);
        reorder(map, order.stream().map(name -> name.equals(from) ? to : name).toList());
    }

    /// Restores `map`'s iteration order to `order` (names it no longer contains are skipped, names
    /// it gained since are appended), so undo of a remove or a rename puts the row back where it was.
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

    /// Focuses a row's name box so the author can type a new name inline, mirroring the explorer's
    /// directory rename and the Variables panel's.
    private void beginRename(ComponentDataHolder<?> holder) {
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
        // A removed (or undone-attach) component can no longer stay focused. The check walks the
        // whole tree, not just the top level, since removing a host takes its children out with it.
        if (selected != null && ComponentAttachableHolder.flatten(root).noneMatch(h -> h == selected)) {
            select(null, null);
        }

        rebuildList();
        onChanged.run();
    }

    private int rowIndexAt(double mouseY) {
        int bodyTop = table.getY() + 2;
        if (mouseY < bodyTop) return -1;

        int idx = (int) ((mouseY - bodyTop) / ROW_H);

        return (idx >= 0 && idx < rows.size()) ? idx : -1;
    }

    private boolean overAddButton(double mouseX, double mouseY) {
        return mouseX >= addBtnX && mouseX < addBtnX + ADD_BTN
            && mouseY >= addBtnY && mouseY < addBtnY + ADD_BTN;
    }
}