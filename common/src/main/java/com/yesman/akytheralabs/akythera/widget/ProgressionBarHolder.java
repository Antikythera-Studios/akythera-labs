package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.ProgressionFillDirection;
import com.yesman.akythera.api.data.serializable.ProgressionFillStyle;
import com.yesman.akythera.api.data.serializable.WidgetImage;
import com.yesman.akythera.client.api.gui.ProgressionBar;
import com.yesman.akythera.core.blueprint.widget.ProgressionBarBlueprint;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/// Editor holder for an `akythera:progression_bar` ([ProgressionBarBlueprint]).
public final class ProgressionBarHolder extends WidgetComponentHolder<ProgressionBarBlueprint> {
    public static final MapCodec<ProgressionBarHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            LAYOUT_FIELD.forGetter(h -> Optional.of(h.layout)),
            ProgressionFillStyle.CODEC.optionalFieldOf("fill_style", ProgressionFillStyle.EQUAL).forGetter(h -> h.fillStyle),
            ProgressionFillDirection.CODEC.optionalFieldOf("fill_direction", ProgressionFillDirection.LEFT_TO_RIGHT).forGetter(h -> h.fillDirection),
            Codec.INT.optionalFieldOf("highlight_thickness", 1).forGetter(h -> h.highlightThickness),
            Codec.FLOAT.optionalFieldOf("flash_time", 0.0F).forGetter(h -> h.flashTime),
            Codec.FLOAT.optionalFieldOf("lerp_time", 0.0F).forGetter(h -> h.lerpTime),
            Codec.BOOL.optionalFieldOf("fixed_lerp_time", false).forGetter(h -> h.fixedLerpTime),
            WidgetImage.CODEC.optionalFieldOf("filled_image", ProgressionBarBlueprint.DEFAULT_FILLED_IMAGE).forGetter(h -> h.filledImage.makeObject()),
            WidgetImage.CODEC.optionalFieldOf("empty_image", ProgressionBarBlueprint.DEFAULT_EMPTY_IMAGE).forGetter(h -> h.emptyImage.makeObject()),
            WidgetImage.CODEC.optionalFieldOf("lerp_image", ProgressionBarBlueprint.DEFAULT_LERP_IMAGE).forGetter(h -> h.lerpImage.makeObject()),
            Codec.FLOAT.optionalFieldOf("preview_progression", 0.5F).forGetter(h -> h.previewProgression)
        ).apply(instance, (layout, fillStyle, fillDirection, highlightThickness, flashTime, lerpTime, fixedLerpTime, filled, empty, lerp, preview) -> {
            ProgressionBarHolder holder = createDefault();
            layout.ifPresent(stored -> holder.layout = stored);
            holder.fillStyle = fillStyle;
            holder.fillDirection = fillDirection;
            holder.highlightThickness = highlightThickness;
            holder.flashTime = flashTime;
            holder.lerpTime = lerpTime;
            holder.fixedLerpTime = fixedLerpTime;
            holder.filledImage = WidgetImageRef.of(filled, false);
            holder.emptyImage = WidgetImageRef.of(empty, false);
            holder.lerpImage = WidgetImageRef.of(lerp, false);
            holder.previewProgression = preview;
            return holder;
        })
    );

    @EditorEditable(order = 0)
    private ProgressionFillStyle fillStyle = ProgressionFillStyle.EQUAL;

    @EditorEditable(order = 1)
    private ProgressionFillDirection fillDirection = ProgressionFillDirection.LEFT_TO_RIGHT;

    /// In pixels.
    @EditorEditable(order = 2, min = 0, max = 64)
    private int highlightThickness = 1;

    /// Seconds of real time the lerp area holds its full span after a change.
    @EditorEditable(order = 3)
    private float flashTime;

    /// Seconds of real time an interpolation takes.
    @EditorEditable(order = 4)
    private float lerpTime;

    @EditorEditable(order = 5)
    private boolean fixedLerpTime;

    @EditorEditable(order = 6)
    private WidgetImageRef filledImage = WidgetImageRef.of(ProgressionBarBlueprint.DEFAULT_FILLED_IMAGE, false);

    @EditorEditable(order = 7)
    private WidgetImageRef emptyImage = WidgetImageRef.of(ProgressionBarBlueprint.DEFAULT_EMPTY_IMAGE, false);

    @EditorEditable(order = 8)
    private WidgetImageRef lerpImage = WidgetImageRef.of(ProgressionBarBlueprint.DEFAULT_LERP_IMAGE, false);

    /// How full the canvas draws the bar. Editor-only: the game reads the bar's binding instead.
    @EditorEditable(order = 9, min = 0, max = 1)
    private float previewProgression = 0.5F;

    private ProgressionBarHolder() {
        super(WidgetLayoutHolder.sized(100, 8));
    }

    public static ProgressionBarHolder createDefault() {
        return new ProgressionBarHolder();
    }

    public float getPreviewProgression() {
        return previewProgression;
    }

    @Override
    public void readFrom(final ProgressionBarBlueprint object) {
        layout = WidgetLayoutHolder.of(object.layout());
        fillStyle = object.fillStyle();
        fillDirection = object.fillDirection();
        highlightThickness = object.highlightThickness();
        flashTime = object.flashTime();
        lerpTime = object.lerpTime();
        fixedLerpTime = object.fixedLerpTime();
        filledImage = WidgetImageRef.of(object.filledImage(), false);
        emptyImage = WidgetImageRef.of(object.emptyImage(), false);
        lerpImage = WidgetImageRef.of(object.lerpImage(), false);
    }

    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>(super.getDTO());
        dto.put("fill_style", sourceOf(fillStyle));
        dto.put("fill_direction", sourceOf(fillDirection));
        dto.put("highlight_thickness", sourceOf(highlightThickness));
        dto.put("flash_time", sourceOf(flashTime));
        dto.put("lerp_time", sourceOf(lerpTime));
        dto.put("fixed_lerp_time", sourceOf(fixedLerpTime));
        dto.put("filled_image", sourceOf(filledImage.makeObject()));
        dto.put("empty_image", sourceOf(emptyImage.makeObject()));
        dto.put("lerp_image", sourceOf(lerpImage.makeObject()));

        return dto;
    }

    @Override
    public ProgressionBarBlueprint makeObject() {
        return new ProgressionBarBlueprint(
            layout.makeObject(), order, fillStyle, fillDirection, highlightThickness, flashTime, lerpTime, fixedLerpTime,
            filledImage.makeObject(), emptyImage.makeObject(), lerpImage.makeObject(), isEnabled()
        );
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return Stream.concat(
            super.referencedReferences(),
            Stream.of(filledImage, emptyImage, lerpImage).flatMap(WidgetImageRef::referencedReferences)
        );
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return ProgressionBar.class;
    }
}
