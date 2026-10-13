package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.MapCodec;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.api.data.serializable.WidgetImage;
import com.yesman.akythera.client.api.gui.common.WidgetTheme;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetComponentBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetLayout;
import com.yesman.akytheralabs.ScriptNodeSourceCompiler;
import com.yesman.akytheralabs.SourceTemplateEngine;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/// Base of every widget holder, the editor twin of one [WidgetComponentBlueprint] type in
/// [WidgetComponentHolders#REGISTRY]. Every widget carries a [WidgetLayoutHolder], and the [WidgetGraphs] of its
/// events and bindings.
///
/// @param <T> the widget's component blueprint
public abstract class WidgetComponentHolder<T extends WidgetComponentBlueprint> extends ComponentDataHolder<T> {
    /// The field every widget holder's codec declares for [#layout]. It decodes through an [Optional]: a default
    /// value is one instance shared by every decode, and a layout is mutable.
    protected static final MapCodec<Optional<WidgetLayoutHolder>> LAYOUT_FIELD = WidgetLayoutHolder.CODEC.optionalFieldOf("layout");

    @EditorEditable(label = LangKeys.GUI_WIDGET_SCREEN_ROW_LAYOUT, order = -100, rebuildOnWrite = true)
    protected WidgetLayoutHolder layout;

    /// The graphs of this widget's events and bindings. [WidgetComponentHolders] persists them beside every
    /// widget's own fields.
    @EditorEditable(label = LangKeys.GUI_WIDGET_SCREEN_ROW_GRAPHS, order = 1000)
    protected WidgetGraphs graphs = new WidgetGraphs();

    /// Where the widget sits among its siblings, its index in its host's map. Set by [#lowerWidgets].
    protected int order;

    protected WidgetComponentHolder(final WidgetLayoutHolder layout) {
        this.layout = layout;
    }

    public WidgetLayoutHolder getLayout() {
        return layout;
    }

    public WidgetGraphs getGraphs() {
        return graphs;
    }

    public void setGraphs(final WidgetGraphs graphs) {
        this.graphs = graphs;
    }

    /// What the canvas of each event and binding of this widget implements, by name: an event takes the arguments
    /// it binds, a binding answers the type it reads. Empty while the widget does not lower.
    public Map<String, BlueprintSignature> graphSignatures() {
        WidgetComponentBlueprint blueprint;

        try {
            blueprint = makeObject();
        } catch (RuntimeException e) {
            return Map.of();
        }

        Map<String, BlueprintSignature> signatures = new LinkedHashMap<>();

        blueprint.events().forEach((name, arguments) -> signatures.put(name, new BlueprintSignature(
            name, BlueprintSignature.Kind.EVENT,
            arguments.stream().map(argument -> new BlueprintSignature.Parameter(argument.name(), argument.type().type())).toList(),
            null
        )));
        blueprint.bindings().forEach((name, type) -> signatures.put(name, new BlueprintSignature(
            name, BlueprintSignature.Kind.FUNCTION, List.of(), type.type()
        )));

        return signatures;
    }

    /// Every asset the graphs' nodes bind.
    @Override
    public Stream<EditorReference> referencedReferences() {
        return graphs.map().values().stream().flatMap(ScriptGraphData::referencedReferences);
    }

    /// `components/widget/<type>.ftl`, by the registered type's path.
    @Override
    public @Nullable String sourceTemplate() {
        Identifier typeId = WidgetComponentHolders.REGISTRY.typeIdOrNull(this);

        return typeId == null ? null : "components/widget/" + typeId.getPath() + ".ftl";
    }

    /// The `layout` and the `order` every widget's template renders, as Java expressions. A widget adds its own
    /// values, rendered by [#sourceOf].
    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("layout", sourceOf(layout.makeObject()));
        dto.put("order", sourceOf(order));

        return dto;
    }

    /// `value` as a Java expression of a widget blueprint's argument: a layout, a message, an id, an image, a color,
    /// a sound event as its holder, a list of these, or a literal [ScriptNodeSourceCompiler#valueLiteral] writes.
    protected static String sourceOf(final @Nullable Object value) {
        return switch (value) {
            case null -> "null";
            case WidgetLayout layout -> "new WidgetLayout("
                + sourceOf(layout.x1()) + ", " + sourceOf(layout.x2()) + ", " + sourceOf(layout.y1()) + ", " + sourceOf(layout.y2()) + ", "
                + "WidgetLayout.Horizontal." + layout.horizontalAnchor().name() + ", WidgetLayout.Vertical." + layout.verticalAnchor().name() + ")";
            case WidgetLayout.Unit unit -> "new WidgetLayout.Unit(" + unit.value() + ", " + unit.fraction() + ")";
            case LocalizedMessage message -> message.isTranslatable()
                ? "LocalizedMessage.translatable(" + ScriptNodeSourceCompiler.valueLiteral(message.text()) + ")"
                : "LocalizedMessage.handwritten(" + ScriptNodeSourceCompiler.valueLiteral(message.text()) + ")";
            case Identifier id -> "Identifier.parse(" + ScriptNodeSourceCompiler.valueLiteral(id.toString()) + ")";
            case WidgetImage image -> "new WidgetImage(" + sourceOf(image.texture()) + ", " + sourceOf(image.color()) + ")";
            case RGBA color -> "new RGBA(" + SourceTemplateEngine.floatLiteral(color.r()) + ", " + SourceTemplateEngine.floatLiteral(color.g()) + ", "
                + SourceTemplateEngine.floatLiteral(color.b()) + ", " + SourceTemplateEngine.floatLiteral(color.a()) + ")";
            case SoundEvent sound -> "BuiltInRegistries.SOUND_EVENT.get(" + sourceOf(sound.location()) + ").orElseThrow()";
            case List<?> list -> "List.of(" + list.stream().map(WidgetComponentHolder::sourceOf).collect(Collectors.joining(", ")) + ")";
            default -> ScriptNodeSourceCompiler.valueLiteral(value);
        };
    }

    /// The ids of the registered themes of `type`, a theme row's candidates.
    protected static List<Identifier> themesOf(final Class<? extends WidgetTheme> type) {
        return WidgetTheme.ENUM_MANAGER.universalValues().stream()
            .filter(type::isInstance)
            .map(WidgetTheme::identifier)
            .toList();
    }

    /// `components` lowered like [ComponentAttachableHolder#lowerComponents], each widget taking its index in the
    /// map as its [WidgetComponentBlueprint#order].
    public static Map<String, ComponentBlueprint<?, ?>> lowerWidgets(final SequencedMap<String, ComponentDataHolder<?>> components) {
        int index = 0;

        for (ComponentDataHolder<?> holder : components.values()) {
            if (holder instanceof WidgetComponentHolder<?> widget) widget.order = index;

            index++;
        }

        return ComponentAttachableHolder.lowerComponents(components);
    }
}
