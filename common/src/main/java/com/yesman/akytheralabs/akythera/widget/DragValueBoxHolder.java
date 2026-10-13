package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.DragValueBox;
import com.yesman.akythera.client.api.gui.DragValueBoxTheme;
import com.yesman.akythera.core.blueprint.widget.DragValueBoxBlueprint;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.editor.EditorEditable;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Editor holder for an `akythera:drag_value_box` ([DragValueBoxBlueprint]).
public final class DragValueBoxHolder extends WidgetComponentHolder<DragValueBoxBlueprint> {
    public static final MapCodec<DragValueBoxHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            LAYOUT_FIELD.forGetter(h -> Optional.of(h.layout)),
            Codec.FLOAT.optionalFieldOf("min", 0.0F).forGetter(h -> h.min),
            Codec.FLOAT.optionalFieldOf("max", 1.0F).forGetter(h -> h.max),
            LocalizedMessage.CODEC.optionalFieldOf("message", LocalizedMessage.EMPTY).forGetter(h -> h.message),
            Identifier.CODEC.optionalFieldOf("theme").forGetter(ParseUtils.provideOptional(DragValueBoxHolder::getTheme))
        ).apply(instance, (layout, min, max, message, theme) -> {
            DragValueBoxHolder holder = createDefault();
            layout.ifPresent(stored -> holder.layout = stored);
            holder.min = min;
            holder.max = max;
            holder.message = message;
            holder.theme = theme.orElse(null);
            return holder;
        })
    );

    @EditorEditable(order = 0)
    private float min;

    @EditorEditable(order = 1)
    private float max = 1.0F;

    @EditorEditable(order = 2)
    private LocalizedMessage message = LocalizedMessage.EMPTY;

    /// Null uses the default drag value box theme.
    @EditorEditable(order = 3, candidates = "themeCandidates")
    private @Nullable Identifier theme;

    private DragValueBoxHolder() {
        super(WidgetLayoutHolder.sized(100, 16));
    }

    public static DragValueBoxHolder createDefault() {
        return new DragValueBoxHolder();
    }

    public @Nullable Identifier getTheme() {
        return theme;
    }

    private List<Identifier> themeCandidates() {
        return themesOf(DragValueBoxTheme.class);
    }

    @Override
    public void readFrom(final DragValueBoxBlueprint object) {
        layout = WidgetLayoutHolder.of(object.layout());
        min = object.min();
        max = object.max();
        message = object.message();
        theme = object.theme();
    }

    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>(super.getDTO());
        dto.put("min", sourceOf(min));
        dto.put("max", sourceOf(max));
        dto.put("message", sourceOf(message));
        dto.put("theme", sourceOf(theme));

        return dto;
    }

    @Override
    public DragValueBoxBlueprint makeObject() {
        return new DragValueBoxBlueprint(layout.makeObject(), order, min, max, message, theme, isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return DragValueBox.class;
    }
}
