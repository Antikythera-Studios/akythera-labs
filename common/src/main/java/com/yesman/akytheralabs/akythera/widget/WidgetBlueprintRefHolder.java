package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.client.api.gui.AkytheraWidget;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akythera.core.blueprint.widget.DataDrivenWidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprintComponentBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetComponentBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetUsage;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.BlueprintSourceWriter;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/// Editor holder for an `akythera:widget_blueprint` ([WidgetBlueprintComponentBlueprint]): another widget
/// blueprint placed in this one.
public final class WidgetBlueprintRefHolder extends WidgetComponentHolder<WidgetBlueprintComponentBlueprint> {
    public static final MapCodec<WidgetBlueprintRefHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            LAYOUT_FIELD.forGetter(h -> Optional.of(h.layout)),
            EditorHolderCodecs.entry(AkytheraRegistries.DataPack.WIDGET_KEY).optionalFieldOf("widget")
                .forGetter(ParseUtils.provideOptional(WidgetBlueprintRefHolder::getWidget))
        ).apply(instance, (layout, widget) -> {
            WidgetBlueprintRefHolder holder = createDefault();
            layout.ifPresent(stored -> holder.layout = stored);
            holder.widget = widget.orElse(null);
            return holder;
        })
    );

    /// The placed blueprint, a [WidgetUsage#WIDGET] one. Null until one is picked.
    @EditorEditable(
        label = LangKeys.GUI_WIDGET_SCREEN_ROW_WIDGET,
        order = 0,
        candidates = "widgetCandidates",
        candidateLabel = "widgetLabel",
        searchable = true
    )
    private @Nullable Holder<WidgetBlueprint> widget;

    private WidgetBlueprintRefHolder() {
        super(WidgetLayoutHolder.sized(100, 100));
    }

    public static WidgetBlueprintRefHolder createDefault() {
        return new WidgetBlueprintRefHolder();
    }

    /// A holder placing `widget`.
    public static WidgetBlueprintRefHolder of(final Holder<WidgetBlueprint> widget) {
        WidgetBlueprintRefHolder holder = new WidgetBlueprintRefHolder();
        holder.widget = widget;
        return holder;
    }

    public @Nullable Holder<WidgetBlueprint> getWidget() {
        return widget;
    }

    private List<Holder<WidgetBlueprint>> widgetCandidates(final WidgetBlueprintHolder root) {
        return placeableIn(root);
    }

    /// The blueprints `root` may place: the project's and the world's [WidgetUsage#WIDGET] blueprints, other mods'
    /// compiled ones included, placing nothing that leads back to `root`, and under a HUD root holding no widget
    /// that takes input, placed ones included.
    public static List<Holder<WidgetBlueprint>> placeableIn(final WidgetBlueprintHolder root) {
        boolean hud = root.usage() == WidgetUsage.HUD;

        return AkytheraProject.getCurrentProject().getMergedHolders(AkytheraRegistries.DataPack.WIDGET_KEY).stream()
            .filter(holder -> holder.unwrapKey().filter(key -> placeable(key, root.getOwnResourceKey(), hud, new HashSet<>())).isPresent())
            .toList();
    }

    private Component widgetLabel(final Holder<WidgetBlueprint> holder) {
        return Component.literal(EditorHolderCodecs.entryId(holder).toString());
    }

    /// Whether the blueprint `key` may be placed in the blueprint `rootKey`: a widget blueprint that is not
    /// `rootKey`, that places nothing failing the same test, and with `hud` holds no widget taking input. Reads the
    /// project's holder of `key` when it has one, else the world's entry.
    ///
    /// @param visited the blueprints already passed on this walk
    private static boolean placeable(
        final ResourceKey<WidgetBlueprint> key, final @Nullable ResourceKey<WidgetBlueprint> rootKey, final boolean hud, final Set<ResourceKey<WidgetBlueprint>> visited
    ) {
        if (key.equals(rootKey)) return false;
        if (!visited.add(key)) return true;

        Optional<WidgetBlueprintHolder> project = AkytheraProject.getCurrentProject().getDataHolder(key);

        if (project.isPresent()) {
            WidgetBlueprintHolder holder = project.get();

            if (holder.usage() != WidgetUsage.WIDGET || hud && !holder.inputWidgetPaths().isEmpty()) return false;

            return ComponentAttachableHolder.flatten(holder).allMatch(component ->
                !(component instanceof WidgetBlueprintRefHolder reference)
                    || reference.widget == null
                    || placeable(ResourceKey.create(AkytheraRegistries.DataPack.WIDGET_KEY, EditorHolderCodecs.entryId(reference.widget)), rootKey, hud, visited));
        }

        WidgetBlueprint world = StaticRegistryAccess.getRegistryAccess().lookupOrThrow(AkytheraRegistries.DataPack.WIDGET_KEY).getValue(key);

        if (world == null || world.usage() != WidgetUsage.WIDGET) return false;

        return !(world instanceof DataDrivenWidgetBlueprint dataDriven) || placeable(dataDriven.components(), rootKey, hud, visited);
    }

    /// Whether every widget of `components` passes [#placeable]'s test, the placed blueprints and the children
    /// included.
    private static boolean placeable(
        final Map<String, ComponentBlueprint<?, ?>> components, final @Nullable ResourceKey<WidgetBlueprint> rootKey, final boolean hud, final Set<ResourceKey<WidgetBlueprint>> visited
    ) {
        return components.values().stream().allMatch(component -> switch (component) {
            case WidgetBlueprintComponentBlueprint reference -> placeable(reference.widget(), rootKey, hud, visited);
            case WidgetComponentBlueprint widget -> !(hud && widget.acceptsInput() && widget.children().isEmpty())
                && placeable(widget.children(), rootKey, hud, visited);
            default -> true;
        });
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return widget == null ? super.referencedReferences() : Stream.concat(super.referencedReferences(), EditorHolderCodecs.referenceOf(widget));
    }

    @Override
    public void readFrom(final WidgetBlueprintComponentBlueprint object) {
        layout = WidgetLayoutHolder.of(object.layout());
        widget = EditorHolderCodecs.resolve(AkytheraRegistries.DataPack.WIDGET_KEY, object.widget().identifier());
    }

    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>(super.getDTO());
        dto.put("widget", widget == null ? "null" : BlueprintSourceWriter.registryKey(AkytheraRegistries.DataPack.WIDGET_KEY, EditorHolderCodecs.entryId(widget).toString()));

        return dto;
    }

    /// @throws IllegalStateException while no blueprint is picked
    @Override
    public WidgetBlueprintComponentBlueprint makeObject() {
        if (widget == null) throw new IllegalStateException("No widget blueprint is picked");

        return new WidgetBlueprintComponentBlueprint(
            layout.makeObject(), order, ResourceKey.create(AkytheraRegistries.DataPack.WIDGET_KEY, EditorHolderCodecs.entryId(widget)), isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return AkytheraWidget.class;
    }
}
