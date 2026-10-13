package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.client.api.gui.Grid;
import com.yesman.akythera.core.blueprint.widget.GridBlueprint;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;

/// Editor holder for an `akythera:grid` ([GridBlueprint]). Hosts any widget, laid out in cells in its map's order.
public final class GridHolder extends WidgetComponentHolder<GridBlueprint> implements ComponentAttachableHolder {
    public static final MapCodec<GridHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            LAYOUT_FIELD.forGetter(h -> Optional.of(h.layout)),
            Codec.INT.optionalFieldOf("cell_width", 32).forGetter(h -> h.cellWidth),
            Codec.INT.optionalFieldOf("cell_height", 32).forGetter(h -> h.cellHeight),
            Codec.INT.optionalFieldOf("padding", 0).forGetter(h -> h.padding),
            ComponentAttachableHolder.componentsField(() -> WidgetComponentHolders.REGISTRY).forGetter(ComponentAttachableHolder::componentsOf)
        ).apply(instance, (layout, cellWidth, cellHeight, padding, components) -> {
            GridHolder holder = createDefault();
            layout.ifPresent(stored -> holder.layout = stored);
            holder.cellWidth = cellWidth;
            holder.cellHeight = cellHeight;
            holder.padding = padding;
            ComponentAttachableHolder.applyComponents(holder, components);
            return holder;
        })
    );

    /// In pixels.
    @EditorEditable(order = 0, min = 1, max = 4096)
    private int cellWidth = 32;

    /// In pixels.
    @EditorEditable(order = 1, min = 1, max = 4096)
    private int cellHeight = 32;

    /// Space between the cells and around them, in pixels.
    @EditorEditable(order = 2, min = 0, max = 4096)
    private int padding;

    private final SequencedMap<String, ComponentDataHolder<?>> components = ComponentAttachableHolder.newComponentMap();

    private GridHolder() {
        super(WidgetLayoutHolder.sized(160, 100));
    }

    public static GridHolder createDefault() {
        return new GridHolder();
    }

    @Override
    public List<Class<?>> attachableTypes() {
        return List.of(WidgetComponentHolder.class);
    }

    @Override
    public SequencedMap<String, ComponentDataHolder<?>> getComponents() {
        return components;
    }

    @Override
    public @Nullable Exception getNestedFailureException() {
        return AbstractDataHolder.firstNestedFailure(components.values());
    }

    @Override
    public void readFrom(final GridBlueprint object) {
        layout = WidgetLayoutHolder.of(object.layout());
        cellWidth = object.cellWidth();
        cellHeight = object.cellHeight();
        padding = object.padding();
        ComponentAttachableHolder.readComponents(this, WidgetComponentHolders.REGISTRY, object.children());
    }

    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>(super.getDTO());
        dto.put("cell_width", sourceOf(cellWidth));
        dto.put("cell_height", sourceOf(cellHeight));
        dto.put("padding", sourceOf(padding));

        return dto;
    }

    @Override
    public GridBlueprint makeObject() {
        return new GridBlueprint(layout.makeObject(), order, cellWidth, cellHeight, padding, lowerWidgets(components), isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return Grid.class;
    }
}
