package com.yesman.akytheralabs.gui.widget.main;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.CheckBox;
import com.yesman.akythera.client.api.gui.CloseableWidget;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.ProjectTags;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.main.processors.ResourceProcessor;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.EditorComboBox;
import com.yesman.akytheralabs.gui.widget.EditorEditBox;
import com.yesman.akytheralabs.gui.widget.OverlayingWindowModal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/// `Edit > Project Tags`: edits the project's tags ([ProjectTags]) of one registry at a time.
///
/// Each tag is a row holding its name, which unfolds into the entries and tags it holds. The header picks the
/// registry, adds a tag and removes the selected one.
public final class ProjectTagsModal extends OverlayingWindowModal implements CloseableWidget {
    private static final int TITLE_H = 20;
    private static final int PAD = 8;
    private static final int GAP = 4;
    private static final int ROW_H = 16;
    private static final int BUTTON_W = 16;
    private static final int CHECK_BOX_W = 12;
    private static final int SEPARATOR_H = 5;
    private static final int REGISTRY_COMBO_W = 160;
    private static final int TABLE_TOP = TITLE_H + PAD + ROW_H + GAP;

    /// Left inset of a value row, in pixels.
    private static final int VALUE_INDENT = BUTTON_W + GAP + 24;

    /// Path of an added tag, suffixed by a number while taken.
    private static final String NEW_TAG = "new_tag";

    private final Font font;
    private final ProjectTags tags;

    private final AkytheraButton closeButton;
    private final EditorComboBox<ResourceKey<? extends Registry<?>>> registryCombo;
    private final AkytheraButton addButton;
    private final AkytheraButton removeButton;
    private final WidgetTable table;

    private ResourceKey<? extends Registry<?>> registry = ProjectTags.REGISTRIES.getFirst();

    /// The paths of the selected tags.
    private final Set<String> selectedTags = new HashSet<>();

    /// The path a Shift press selects the range from: the last tag pressed without Shift. Null while none is.
    private @Nullable String anchorTag;

    /// The paths of the tags whose values are shown.
    private final Set<String> unfoldedTags = new HashSet<>();

    public static void open(final Font font) {
        ModalManager.getInstance().openModalWidgetAtCenter(new ProjectTagsModal(font));
    }

    private ProjectTagsModal(final Font font) {
        super(AnchoredWidget.fraction(70), AnchoredWidget.fraction(70), Component.translatable(LangKeys.GUI_PROJECT_TAGS_MODAL_TITLE));

        this.font = font;
        this.tags = AkytheraProject.getCurrentProject().getTags();

        this.closeButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.handwritten("X"), () -> ModalManager.getInstance().closeModal())
            .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TITLE_H))
            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(TITLE_H))
            .horizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.TOP_MENU_BUTTON)
            .build();

        this.registryCombo = new EditorComboBox<>(
            font,
            AnchoredWidget.pixel(PAD + (BUTTON_W + GAP) * 2), AnchoredWidget.pixel(REGISTRY_COMBO_W),
            AnchoredWidget.pixel(TITLE_H + PAD), AnchoredWidget.pixel(ROW_H),
            HorizontalAnchorType.RIGHT_WIDTH,
            VerticalAnchorType.TOP_HEIGHT,
            () -> registry,
            picked -> {
                if (picked == null || picked.equals(registry)) return;

                registry = picked;
                selectedTags.clear();
                anchorTag = null;
                unfoldedTags.clear();
                scheduleRebuild();
            },
            12,
            Component.empty(),
            ProjectTags.REGISTRIES,
            key -> key.identifier().toString()
        );
        this.registryCombo.setClearable(false).setSearchable(true);
        this.registryCombo.setValueQuiet(registry);

        this.addButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.handwritten("+"), this::onAddPressed)
            .xUnits(AnchoredWidget.pixel(PAD + BUTTON_W + GAP), AnchoredWidget.pixel(BUTTON_W))
            .yUnits(AnchoredWidget.pixel(TITLE_H + PAD), AnchoredWidget.pixel(ROW_H))
            .horizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EXPLORER_BUTTON)
            .build();

        this.removeButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.handwritten("-"), this::onRemovePressed)
            .xUnits(AnchoredWidget.pixel(PAD), AnchoredWidget.pixel(BUTTON_W))
            .yUnits(AnchoredWidget.pixel(TITLE_H + PAD), AnchoredWidget.pixel(ROW_H))
            .horizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH)
            .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
            .theme(AkytheraLabsButtonTheme.AKYTHERA_EXPLORER_BUTTON)
            .build();

        this.table = new WidgetTable(
            Objects.requireNonNull(AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen()),
            AnchoredWidget.pixel(PAD), AnchoredWidget.pixel(PAD),
            AnchoredWidget.pixel(TABLE_TOP), AnchoredWidget.pixel(PAD),
            HorizontalAnchorType.LEFT_RIGHT,
            VerticalAnchorType.TOP_BOTTOM,
            ROW_H + GAP
        );

        rebuild();
    }

    /// Lists the tags of the current registry again, each followed by its values while unfolded.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void rebuild() {
        AkytheraProject project = AkytheraProject.getCurrentProject();
        String namespace = project.getProjectName();
        Set<Map.Entry<ResourceKey<?>, ?>> mergedEntries = project.getMergedEntries((ResourceKey) registry);
        List<ResourceKey<?>> entries = mergedEntries.stream()
            .<ResourceKey<?>>map(Map.Entry::getKey)
            .sorted(Comparator.comparing(ResourceProcessor::displayId))
            .toList();
        List<TagKey<?>> allTags = Stream.concat(
                (Stream<TagKey<?>>) StaticRegistryAccess.getRegistryAccess().lookupOrThrow((ResourceKey) registry).listTagIds(),
                tags.tagKeysOf(registry, namespace).stream()
            )
            .distinct()
            .sorted(Comparator.comparing((TagKey<?> tag) -> tag.location().toString()))
            .toList();

        table.clearWidgets();

        for (Map.Entry<String, ProjectTags.TagFile> tagEntry : tags.tagsOf(registry).entrySet()) {
            String path = tagEntry.getKey();
            boolean unfolded = unfoldedTags.contains(path);
            boolean selected = selectedTags.contains(path);

            Consumer<MouseButtonEvent> onPressed = event -> {
                List<String> paths = List.copyOf(tags.tagsOf(registry).keySet());

                if (event.hasShiftDown() && anchorTag != null && paths.contains(anchorTag)) {
                    int from = paths.indexOf(anchorTag);
                    int to = paths.indexOf(path);

                    selectedTags.clear();
                    selectedTags.addAll(paths.subList(Math.min(from, to), Math.max(from, to) + 1));
                } else if (event.hasControlDownWithQuirk()) {
                    if (!selectedTags.remove(path)) selectedTags.add(path);

                    anchorTag = path;
                } else {
                    selectedTags.clear();
                    selectedTags.add(path);
                    anchorTag = path;
                }

                scheduleRebuild();
            };

            AkytheraButton foldButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.handwritten(unfolded ? "ᐁ" : "ᐅ"), () -> {
                    if (!unfoldedTags.remove(path)) unfoldedTags.add(path);

                    scheduleRebuild();
                })
                .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(BUTTON_W))
                .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H))
                .horizontalAnchorType(HorizontalAnchorType.LEFT_WIDTH)
                .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
                .theme(AkytheraLabsButtonTheme.AKYTHERA_EXPLORER_BUTTON)
                .build();

            EditorEditBox<String> nameBox = EditorEditBox.stringBox(
                font,
                AnchoredWidget.pixel(BUTTON_W + GAP), AnchoredWidget.pixel(0),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                HorizontalAnchorType.LEFT_RIGHT,
                VerticalAnchorType.TOP_HEIGHT,
                () -> path,
                _ -> {},
                Component.empty()
            );
            nameBox.setOnCommitted((_, name) -> {
                if (name.equals(path)) return;

                if (!Identifier.isValidPath(name) || !tags.rename(registry, path, name)) {
                    nameBox.setValue(path);
                    return;
                }

                if (selectedTags.remove(path)) selectedTags.add(name);
                if (unfoldedTags.remove(path)) unfoldedTags.add(name);
                if (path.equals(anchorTag)) anchorTag = name;

                scheduleRebuild();
            });

            if (!table.children().isEmpty()) table.newSeparator(SEPARATOR_H);

            table.newRow().addWidget(foldButton).addWidget(nameBox).onRowPressed(onPressed);

            if (selected) table.markPressed();
            if (!unfolded) continue;

            TagKey<?> self = TagKey.create((ResourceKey) registry, Identifier.fromNamespaceAndPath(namespace, path));
            List<Object> candidates = new ArrayList<>(entries);

            allTags.stream().filter(tag -> !tag.equals(self)).forEach(candidates::add);

            List<ProjectTags.Value> values = tagEntry.getValue().values();
            Set<Object> usedIds = new HashSet<>();

            values.forEach(value -> usedIds.add(value.id()));

            for (int i = 0; i < values.size(); i++) {
                int index = i;
                boolean element = values.get(i) instanceof ProjectTags.Element;
                Object ownId = values.get(i).id();
                List<Object> valueCandidates = candidates.stream().filter(candidate -> candidate.equals(ownId) || !usedIds.contains(candidate)).toList();

                EditorComboBox<Object> valueCombo = new EditorComboBox<>(
                    font,
                    AnchoredWidget.pixel(0), AnchoredWidget.pixel(BUTTON_W + GAP + (element ? CHECK_BOX_W + GAP : 0)),
                    AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
                    HorizontalAnchorType.LEFT_RIGHT,
                    VerticalAnchorType.TOP_HEIGHT,
                    () -> valueAt(path, index).id(),
                    picked -> {
                        ProjectTags.Value current = valueAt(path, index);

                        // WidgetTable#initialize sets every combo's value, which runs this callback too.
                        if (current.id().equals(picked)) return;

                        switch (picked) {
                            case ResourceKey<?> key -> setValueAt(path, index, new ProjectTags.Element(key, !(current instanceof ProjectTags.Element) || current.required()));
                            case TagKey<?> tag -> setValueAt(path, index, new ProjectTags.Tag(tag, !(current instanceof ProjectTags.Tag) || current.required()));
                            case null, default -> {
                                return;
                            }
                        }

                        scheduleRebuild();
                    },
                    12,
                    Component.empty(),
                    valueCandidates,
                    candidate -> switch (candidate) {
                        case ResourceKey<?> key -> key.identifier().equals(DataHolder.EMPTY_REFERENCE) ? "<none>" : ResourceProcessor.displayId(key);
                        case TagKey<?> tag -> tag.location().equals(DataHolder.EMPTY_REFERENCE) ? "<none>" : "#" + tag.location();
                        default -> Objects.toString(candidate);
                    }
                );
                valueCombo.setCategoryMapper(candidate -> Component.translatable(candidate instanceof TagKey<?>
                    ? LangKeys.GUI_PROJECT_TAGS_MODAL_CATEGORY_TAGS
                    : LangKeys.GUI_PROJECT_TAGS_MODAL_CATEGORY_ENTRIES));
                valueCombo.setClearable(false).setSearchable(true);

                AkytheraButton removeValueButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.handwritten("-"), () -> {
                        List<ProjectTags.Value> next = new ArrayList<>(tags.tagsOf(registry).get(path).values());
                        next.remove(index);
                        tags.put(registry, path, tags.tagsOf(registry).get(path).withValues(next));
                        scheduleRebuild();
                    })
                    .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(BUTTON_W))
                    .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H))
                    .horizontalAnchorType(HorizontalAnchorType.RIGHT_WIDTH)
                    .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
                    .theme(AkytheraLabsButtonTheme.AKYTHERA_EXPLORER_BUTTON)
                    .build();

                table.newRow(VALUE_INDENT).addWidget(valueCombo).onRowPressed(onPressed);

                if (selected) table.markPressed();

                if (element) {
                    Component required = Component.translatable(LangKeys.GUI_PROJECT_TAGS_MODAL_REQUIRED);
                    CheckBox requiredCheckBox = new CheckBox(
                        font,
                        AnchoredWidget.pixel(BUTTON_W + GAP), AnchoredWidget.pixel(CHECK_BOX_W),
                        AnchoredWidget.pixel(0), AnchoredWidget.pixel(CHECK_BOX_W),
                        HorizontalAnchorType.RIGHT_WIDTH,
                        VerticalAnchorType.TOP_HEIGHT,
                        () -> valueAt(path, index).required(),
                        checked -> setValueAt(path, index, valueAt(path, index).withRequired(checked)),
                        required,
                        false
                    );
                    requiredCheckBox.setTooltip(Tooltip.create(required));
                    table.addWidget(requiredCheckBox);
                }

                table.addWidget(removeValueButton);
            }

            AkytheraButton addValueButton = AkytheraButton.buttonBuilder(font, LocalizedMessage.handwritten("+"), () -> {
                    List<ProjectTags.Value> next = new ArrayList<>(tags.tagsOf(registry).get(path).values());
                    next.add(new ProjectTags.Element(ResourceKey.create((ResourceKey) registry, DataHolder.EMPTY_REFERENCE), true));
                    tags.put(registry, path, tags.tagsOf(registry).get(path).withValues(next));
                    scheduleRebuild();
                })
                .xUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(BUTTON_W))
                .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H))
                .horizontalAnchorType(HorizontalAnchorType.CENTER_WIDTH)
                .verticalAnchorType(VerticalAnchorType.TOP_HEIGHT)
                .theme(AkytheraLabsButtonTheme.AKYTHERA_EXPLORER_BUTTON)
                .build();

            table.newRow(VALUE_INDENT).addWidget(addValueButton).onRowPressed(onPressed);

            if (selected) table.markPressed();
        }

        table.initialize(false);
        table.relocate(getRectangle());
        removeButton.active = !selectedTags.isEmpty();
    }

    /// Runs [#rebuild] after the input being handled, which may come from a widget the rebuild replaces.
    private void scheduleRebuild() {
        Minecraft.getInstance().schedule(this::rebuild);
    }

    private ProjectTags.Value valueAt(final String path, final int index) {
        return tags.tagsOf(registry).get(path).values().get(index);
    }

    private void setValueAt(final String path, final int index, final ProjectTags.Value value) {
        List<ProjectTags.Value> next = new ArrayList<>(tags.tagsOf(registry).get(path).values());
        next.set(index, value);
        tags.put(registry, path, tags.tagsOf(registry).get(path).withValues(next));
    }

    private void onAddPressed() {
        String path = NEW_TAG;

        for (int i = 1; tags.tagsOf(registry).containsKey(path); i++) path = NEW_TAG + "_" + i;

        tags.put(registry, path, ProjectTags.TagFile.EMPTY);
        selectedTags.clear();
        selectedTags.add(path);
        anchorTag = path;
        unfoldedTags.add(path);
        scheduleRebuild();
    }

    private void onRemovePressed() {
        for (String path : selectedTags) {
            tags.remove(registry, path);
            unfoldedTags.remove(path);
        }

        selectedTags.clear();
        anchorTag = null;
        scheduleRebuild();
    }

    @Override
    public void destroy() {
        AkytheraProject.getCurrentProject().saveTags();
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() {
        return List.of(closeButton, registryCombo, addButton, removeButton, table);
    }

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    public void setFocused(final @Nullable GuiEventListener focused) {
        // Re-focusing the already-focused child clears the focus in vanilla's implementation.
        if (getFocused() == focused) return;

        super.setFocused(focused);
    }

    @Override
    public boolean isMouseOverHeader(final double mouseX, final double mouseY) {
        return mouseX >= getX() && mouseY >= getY() && mouseX < closeButton.getX() && mouseY < getY() + TITLE_H;
    }

    @Override
    public boolean mouseClicked(final @NonNull MouseButtonEvent event, final boolean doubleClick) {
        // Route to children, then consume to prevent click-through to the screen below
        super.mouseClicked(event, doubleClick);

        return true;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        return getChildAt(mouseX, mouseY).filter(child -> child.mouseScrolled(mouseX, mouseY, scrollX, scrollY)).isPresent();
    }

    @Override
    public boolean keyPressed(final @NonNull KeyEvent event) {
        GuiEventListener focused = getFocused();
        if (focused != null && focused.keyPressed(event)) return true;

        if (event.isEscape()) {
            ModalManager.getInstance().closeModal();
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(final @NonNull CharacterEvent event) {
        GuiEventListener focused = getFocused();
        return focused != null && focused.charTyped(event);
    }

    @Override
    protected void extractWidgetRenderState(final @NonNull GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);

        graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF1E1E1E);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), 0xFF3A3A3A);
        graphics.fill(getX(), getY(), getRight(), getY() + TITLE_H, 0xFF2A2A2A);
        graphics.text(font, getMessage(), getX() + PAD, getY() + (TITLE_H - font.lineHeight) / 2, 0xFFCCCCCC, false);

        for (GuiEventListener child : children()) {
            ((AbstractWidget) child).extractRenderState(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    protected void updateWidgetNarration(final @NonNull NarrationElementOutput narration) {
    }
}
