package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.client.api.gui.HorizontalWidgetContainer;
import com.yesman.akythera.client.api.gui.VerticalWidgetContainer;
import com.yesman.akythera.core.blueprint.widget.LinearContainerBlueprint;
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
import java.util.function.Supplier;

/// Editor holder for an `akythera:horizontal_container` or `akythera:vertical_container`
/// ([LinearContainerBlueprint]). Hosts any widget, laid out one after another in its map's order. One subclass
/// per direction, a registered type keying its own holder class.
public abstract sealed class LinearContainerHolder extends WidgetComponentHolder<LinearContainerBlueprint> implements ComponentAttachableHolder
    permits LinearContainerHolder.Horizontal, LinearContainerHolder.Vertical
{
    /// Space between the children and around them along the axis, in pixels.
    @EditorEditable(order = 0, min = 0, max = 4096)
    protected int padding;

    private final SequencedMap<String, ComponentDataHolder<?>> components = ComponentAttachableHolder.newComponentMap();

    private LinearContainerHolder(final WidgetLayoutHolder layout) {
        super(layout);
    }

    /// The editor codec of the direction `factory` builds.
    private static <H extends LinearContainerHolder> MapCodec<H> editorCodec(final Supplier<H> factory) {
        return RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                LAYOUT_FIELD.forGetter(h -> Optional.of(h.layout)),
                Codec.INT.optionalFieldOf("padding", 0).forGetter(h -> h.padding),
                ComponentAttachableHolder.componentsField(() -> WidgetComponentHolders.REGISTRY).forGetter(ComponentAttachableHolder::componentsOf)
            ).apply(instance, (layout, padding, components) -> {
                H holder = factory.get();
                layout.ifPresent(stored -> holder.layout = stored);
                holder.padding = padding;
                ComponentAttachableHolder.applyComponents(holder, components);
                return holder;
            })
        );
    }

    /// Whether the children are laid out top to bottom rather than left to right.
    public abstract boolean vertical();

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
    public void readFrom(final LinearContainerBlueprint object) {
        layout = WidgetLayoutHolder.of(object.layout());
        padding = object.padding();
        ComponentAttachableHolder.readComponents(this, WidgetComponentHolders.REGISTRY, object.children());
    }

    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>(super.getDTO());
        dto.put("vertical", sourceOf(vertical()));
        dto.put("padding", sourceOf(padding));

        return dto;
    }

    @Override
    public LinearContainerBlueprint makeObject() {
        return new LinearContainerBlueprint(vertical(), layout.makeObject(), order, padding, lowerWidgets(components), isEnabled());
    }

    /// Lays its children out left to right.
    public static final class Horizontal extends LinearContainerHolder {
        public static final MapCodec<Horizontal> EDITOR_CODEC = editorCodec(Horizontal::new);

        private Horizontal() {
            super(WidgetLayoutHolder.sized(160, 20));
        }

        public static Horizontal createDefault() {
            return new Horizontal();
        }

        @Override
        public boolean vertical() {
            return false;
        }

        @Override
        public Class<? extends PatchComponent> generateEventually() {
            return HorizontalWidgetContainer.class;
        }
    }

    /// Lays its children out top to bottom.
    public static final class Vertical extends LinearContainerHolder {
        public static final MapCodec<Vertical> EDITOR_CODEC = editorCodec(Vertical::new);

        private Vertical() {
            super(WidgetLayoutHolder.sized(100, 100));
        }

        public static Vertical createDefault() {
            return new Vertical();
        }

        @Override
        public boolean vertical() {
            return true;
        }

        @Override
        public Class<? extends PatchComponent> generateEventually() {
            return VerticalWidgetContainer.class;
        }
    }
}
