package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.TextArea;
import com.yesman.akythera.core.blueprint.widget.TextAreaBlueprint;
import com.yesman.akytheralabs.editor.EditorEditable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/// Editor holder for an `akythera:text_area` ([TextAreaBlueprint]).
public final class TextAreaHolder extends WidgetComponentHolder<TextAreaBlueprint> {
    public static final MapCodec<TextAreaHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            LAYOUT_FIELD.forGetter(h -> Optional.of(h.layout)),
            LocalizedMessage.CODEC.optionalFieldOf("text", LocalizedMessage.EMPTY).forGetter(h -> h.text),
            Codec.BOOL.optionalFieldOf("editable", false).forGetter(h -> h.editable)
        ).apply(instance, (layout, text, editable) -> {
            TextAreaHolder holder = createDefault();
            layout.ifPresent(stored -> holder.layout = stored);
            holder.text = text;
            holder.editable = editable;
            return holder;
        })
    );

    @EditorEditable(order = 0)
    private LocalizedMessage text = LocalizedMessage.EMPTY;

    @EditorEditable(order = 1)
    private boolean editable;

    private TextAreaHolder() {
        super(WidgetLayoutHolder.sized(120, 50));
    }

    public static TextAreaHolder createDefault() {
        return new TextAreaHolder();
    }

    @Override
    public void readFrom(final TextAreaBlueprint object) {
        layout = WidgetLayoutHolder.of(object.layout());
        text = object.text();
        editable = object.editable();
    }

    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>(super.getDTO());
        dto.put("text", sourceOf(text));
        dto.put("editable", sourceOf(editable));

        return dto;
    }

    @Override
    public TextAreaBlueprint makeObject() {
        return new TextAreaBlueprint(layout.makeObject(), order, text, editable, isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return TextArea.class;
    }
}
