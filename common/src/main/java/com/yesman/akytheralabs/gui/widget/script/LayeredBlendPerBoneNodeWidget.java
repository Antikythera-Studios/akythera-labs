package com.yesman.akytheralabs.gui.widget.script;

import com.google.gson.JsonPrimitive;
import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.core.animation.driver.LayeredBlendPerBoneDriver;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.ValueShape;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.node.LayeredBlendPerBoneNodeData;
import com.yesman.akytheralabs.gui.widget.script.node.NodeData;
import com.yesman.akytheralabs.gui.widget.script.node.PinLiteral;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/// Hands branches of the skeleton over to a second pose (UE's "Layered blend per bone"): each bone mixes
/// `Layered` onto `Base` by the weight the node's [LayeredBlendPerBoneDriver.LayerSetup] gives it, scaled by
/// `Blend Weight`. The setup is edited in the Details panel.
///
/// Compile rule: lowers to the `layered_blend_per_bone` script function, with the setup as a literal and
/// `Blend Weight` sampled live.
public final class LayeredBlendPerBoneNodeWidget extends AbstractBlendNodeWidget {
    /// The input pin of `Blend Weight`.
    private static final int BLEND_WEIGHT_PIN = 2;

    /// How the Details panel holds [#setupValue].
    private final ValueShape setupShape = ValueShape.of(AkytheraScriptTypes.LAYER_SETUP.value());

    /// The setup as the Details panel edits it.
    private Object setupValue;

    /// The last [#setupValue] that built a setup.
    private LayeredBlendPerBoneDriver.LayerSetup setup;

    public LayeredBlendPerBoneNodeWidget(Font font, LayeredBlendPerBoneDriver.LayerSetup setup, int graphX, int graphY) {
        super(font, Component.literal("Layered Blend per Bone"), "Base", "Layered", graphX, graphY);

        this.setup = setup;
        this.setupValue = setupShape.toEditor(setup);

        addInput(new Pin("Blend Weight", NodeColors.forType(float.class), false, float.class));
        setPinLiterals(List.of(new PinLiteral(BLEND_WEIGHT_PIN, AkytheraScriptTypes.FLOAT.value(), new JsonPrimitive(1.0F))));

        relayout();
    }

    /// The Details rows: the setup, then the literal inputs.
    public List<EditorWidgetFactory.PropertySlot> detailSlots() {
        EditorWidgetFactory.PropertySlot setupSlot = new EditorWidgetFactory.PropertySlot(
            "Layer Setup", setupShape, this, () -> setupValue, value -> {
                setupValue = value;

                try {
                    setup = (LayeredBlendPerBoneDriver.LayerSetup) setupShape.fromEditor(value);
                } catch (IllegalStateException ignored) {
                    // An incomplete row keeps the last setup that built
                }
            });

        return Stream.concat(Stream.of(setupSlot), literalSlots().stream()).toList();
    }

    @Override
    public List<Component> intrinsicErrors() {
        List<Component> errors = new ArrayList<>();

        if (setup.branchFilters().isEmpty() && setup.weightSteps().isEmpty()) {
            errors.add(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VALIDATION_EMPTY_LAYER_SETUP));
        }

        if (setup.branchFilters().stream().anyMatch(filter -> filter.boneName().isBlank())
            || setup.weightSteps().stream().anyMatch(step -> step.boneName().isBlank())
        ) {
            errors.add(Component.translatable(LangKeys.GUI_ANIMATOR_SCRIPT_SCREEN_VALIDATION_UNNAMED_LAYER_BONE));
        }

        return errors;
    }

    @Override
    public NodeData toData() {
        return new LayeredBlendPerBoneNodeData(setup);
    }
}
