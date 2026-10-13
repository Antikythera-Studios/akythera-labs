package com.yesman.akytheralabs.gui.widget.script;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.akythera.blueprint.ComponentPaths;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.components.ComponentEditorPanel;
import com.yesman.akytheralabs.gui.screen.ComponentPathHost;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsComboBoxTheme;
import com.yesman.akytheralabs.gui.widget.EditorComboBox;
import com.yesman.akytheralabs.gui.widget.script.node.ComponentGetterNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

/// Reads the component attached at [#path] — what a row dragged out of [ComponentEditorPanel] and
/// dropped on a canvas becomes.
///
/// No inputs: the path is part of the node, and the host is the canvas' owner. The path is picked from
/// the patch's own components, in the combo the node carries and in the Details panel; a path naming
/// none of them reddens the node on sight.
public final class ComponentGetterNodeWidget extends AbstractNodeWidget {
    private static final String ARROW = "▼";

    /// Shown in the title in place of a class, for a path the patch does not hold.
    private static final String NO_COMPONENT = "<none>";

    /// The component read, as a path of [ComponentAttachable#PATH_SEPARATOR]-joined names.
    @EditorEditable(
        label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_COMPONENT_PATH,
        order = 0,
        candidates = "componentPaths",
        emptyCandidates = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_COMPONENT_PATH_EMPTY
    )
    private String path;

    /// The path combo on the row under the output, over [#comboPaths].
    private EditorComboBox<String> combo;

    /// The paths [#paths] held when [#combo] was made.
    private List<String> comboPaths;

    public ComponentGetterNodeWidget(Font font, String path, int graphX, int graphY) {
        super(font, Component.empty(), graphX, graphY);

        this.path = path;
        rebuildCombo(paths());

        setTitleColor(NodeColors.titleTint(PatchComponent.class));
        addOutput(new Pin("", NodeColors.forType(PatchComponent.class), false, PatchComponent.class));

        setMessage(title());
        relayout();
    }

    public String getPath() {
        return path;
    }

    /// Re-points this node at another component — the combo and the Details row.
    public void setPath(String path) {
        this.path = path;
        combo.setValueQuiet(path);
        setMessage(title());
        relayout();
    }

    /// Follows a renamed component: the one this node reads, or the host it hangs under, moves with it.
    public void renamePath(final String from, final String to) {
        String moved = ComponentPaths.moved(path, from, to);

        if (!moved.equals(path)) setPath(moved);
    }

    /// `Get <path> (<component class>)`, the class being the one the named component builds.
    private Component title() {
        return Component.literal("Get " + path);
    }

    /// The components of the patch being edited, empty on a screen that edits none.
    private static SequencedMap<String, Class<? extends PatchComponent>> paths() {
        Screen screen = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();

        return screen instanceof ComponentPathHost host ? host.componentPaths() : new LinkedHashMap<>();
    }

    /// The Details-row candidates for [#path]: the paths of [#paths], in patch order.
    private List<String> componentPaths() {
        return List.copyOf(paths().sequencedKeySet());
    }

    // ***************************************************************
    // The path combo
    // ***************************************************************

    /// Replaces [#combo] with one over `paths`, in [AkytheraLabsComboBoxTheme#BLUEPRINT_COMBO_BOX], as the
    /// body widget the base class hands clicks to.
    private void rebuildCombo(SequencedMap<String, Class<? extends PatchComponent>> paths) {
        EditorComboBox<String> made = new EditorComboBox<>(
            font,
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(0),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            () -> path,
            picked -> {
                if (picked != null && !picked.equals(path)) setPath(picked);
            },
            Math.min(8, paths.size()),
            Component.empty(),
            List.copyOf(paths.sequencedKeySet()),
            candidate -> paths.containsKey(candidate)
                ? candidate + " (" + paths.get(candidate).getSimpleName() + ")"
                : candidate
        );

        made.setTheme(AkytheraLabsComboBoxTheme.BLUEPRINT_COMBO_BOX);
        made.setClearable(false);
        made.setValueQuiet(path);

        bodyWidgets.remove(combo);
        bodyWidgets.add(made);

        combo = made;
        comboPaths = List.copyOf(paths.sequencedKeySet());
    }

    /// One row taller than its pins: the combo sits under the output.
    @Override
    protected void relayout() {
        super.relayout();

        setWidth(Math.max(getWidth(), H_PAD * 2 + font.width(combo.displayString()) + font.width(ARROW) + PIN));
        setHeight(getHeight() + ROW_H);
    }

    /// Places and draws the combo on its row, first swapping in a new one when the patch's components
    /// changed since it was made.
    @Override
    protected void extractBodyOverlay(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        SequencedMap<String, Class<? extends PatchComponent>> paths = paths();

        if (!List.copyOf(paths.sequencedKeySet()).equals(comboPaths)) {
            rebuildCombo(paths);
            relayout();
        }

        combo.setX(getX() + H_PAD);
        combo.setY(getY() + getHeight() - ROW_H - 3);
        combo.setWidth(getWidth() - H_PAD * 2);
        combo.setHeight(ROW_H);

        combo.extractRenderState(g, mouseX, mouseY, a);
    }

    /// A path the patch does not hold reads nothing at runtime, so it is red on sight — no compile
    /// needed.
    @Override
    public List<Component> intrinsicErrors() {
        return paths().containsKey(path)
            ? List.of()
            : List.of(Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_VALIDATION_UNKNOWN_COMPONENT, path));
    }

    @Override
    public NodeData toData() {
        return new ComponentGetterNodeData(path);
    }
}
