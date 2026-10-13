package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.client.api.gui.ColorPickerWidget;
import com.yesman.akythera.core.blueprint.widget.ColorPickerBlueprint;
import com.yesman.akytheralabs.editor.EditorEditable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/// Editor holder for an `akythera:color_picker` ([ColorPickerBlueprint]).
public final class ColorPickerHolder extends WidgetComponentHolder<ColorPickerBlueprint> {
    public static final MapCodec<ColorPickerHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            LAYOUT_FIELD.forGetter(h -> Optional.of(h.layout)),
            RGBA.INT_LIST_CODEC.optionalFieldOf("color", new RGBA(1.0F, 1.0F, 1.0F)).forGetter(h -> h.color)
        ).apply(instance, (layout, color) -> {
            ColorPickerHolder holder = createDefault();
            layout.ifPresent(stored -> holder.layout = stored);
            holder.color = color;
            return holder;
        })
    );

    /// The color the picker starts at.
    @EditorEditable(order = 0)
    private RGBA color = new RGBA(1.0F, 1.0F, 1.0F);

    private ColorPickerHolder() {
        super(WidgetLayoutHolder.sized(40, 16));
    }

    public static ColorPickerHolder createDefault() {
        return new ColorPickerHolder();
    }

    @Override
    public void readFrom(final ColorPickerBlueprint object) {
        layout = WidgetLayoutHolder.of(object.layout());
        color = object.color();
    }

    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>(super.getDTO());
        dto.put("color", sourceOf(color));

        return dto;
    }

    @Override
    public ColorPickerBlueprint makeObject() {
        return new ColorPickerBlueprint(layout.makeObject(), order, color, isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return ColorPickerWidget.class;
    }
}
