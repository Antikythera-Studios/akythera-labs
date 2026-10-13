package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.RGBA;
import com.yesman.akythera.api.data.serializable.WidgetImage;
import com.yesman.akythera.client.api.gui.Image;
import com.yesman.akythera.core.blueprint.widget.ImageBlueprint;
import com.yesman.akytheralabs.editor.EditorEditable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/// Editor holder for an `akythera:image` ([ImageBlueprint]).
public final class ImageHolder extends WidgetComponentHolder<ImageBlueprint> {
    /// A freshly added image: a plain white fill.
    private static final WidgetImage DEFAULT_IMAGE = WidgetImage.of(new RGBA(1.0F, 1.0F, 1.0F));

    public static final MapCodec<ImageHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            LAYOUT_FIELD.forGetter(h -> Optional.of(h.layout)),
            WidgetImage.CODEC.optionalFieldOf("image", DEFAULT_IMAGE).forGetter(h -> h.image)
        ).apply(instance, (layout, image) -> {
            ImageHolder holder = createDefault();
            layout.ifPresent(stored -> holder.layout = stored);
            holder.image = image;
            return holder;
        })
    );

    @EditorEditable(order = 0)
    private WidgetImage image = DEFAULT_IMAGE;

    private ImageHolder() {
        super(WidgetLayoutHolder.sized(32, 32));
    }

    public static ImageHolder createDefault() {
        return new ImageHolder();
    }

    @Override
    public void readFrom(final ImageBlueprint object) {
        layout = WidgetLayoutHolder.of(object.layout());
        image = object.image();
    }

    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>(super.getDTO());
        dto.put("image", sourceOf(image));

        return dto;
    }

    @Override
    public ImageBlueprint makeObject() {
        return new ImageBlueprint(layout.makeObject(), order, image, isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return Image.class;
    }
}
