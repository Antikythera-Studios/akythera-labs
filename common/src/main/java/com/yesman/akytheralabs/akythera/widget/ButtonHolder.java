package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.api.data.serializable.WidgetImage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.ButtonTheme;
import com.yesman.akythera.core.blueprint.widget.ButtonBlueprint;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/// Editor holder for an `akythera:button` ([ButtonBlueprint]).
public final class ButtonHolder extends WidgetComponentHolder<ButtonBlueprint> {
    public static final MapCodec<ButtonHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            LAYOUT_FIELD.forGetter(h -> Optional.of(h.layout)),
            LocalizedMessage.CODEC.optionalFieldOf("message", LocalizedMessage.EMPTY).forGetter(h -> h.message),
            Identifier.CODEC.optionalFieldOf("theme").forGetter(ParseUtils.provideOptional(ButtonHolder::getTheme)),
            WidgetImage.CODEC.optionalFieldOf("normal_image", ButtonBlueprint.DEFAULT_NORMAL_IMAGE).forGetter(h -> h.normalImage.makeObject()),
            WidgetImage.CODEC.optionalFieldOf("focused_image", ButtonBlueprint.DEFAULT_FOCUSED_IMAGE).forGetter(h -> h.focusedImage.makeObject()),
            WidgetImage.CODEC.optionalFieldOf("disabled_image", ButtonBlueprint.DEFAULT_DISABLED_IMAGE).forGetter(h -> h.disabledImage.makeObject()),
            AkytheraProject.EditorSoundEventAccessor.CODEC.optionalFieldOf("hover_sound").forGetter(ParseUtils.provideOptional(h -> h.hoverSound)),
            AkytheraProject.EditorSoundEventAccessor.CODEC.optionalFieldOf("press_sound").forGetter(ParseUtils.provideOptional(h -> h.pressSound))
        ).apply(instance, (layout, message, theme, normal, focused, disabled, hoverSound, pressSound) -> {
            ButtonHolder holder = createDefault();
            layout.ifPresent(stored -> holder.layout = stored);
            holder.message = message;
            holder.theme = theme.orElse(null);
            holder.normalImage = WidgetImageRef.of(normal, true);
            holder.focusedImage = WidgetImageRef.of(focused, true);
            holder.disabledImage = WidgetImageRef.of(disabled, true);
            holder.hoverSound = hoverSound.orElse(null);
            holder.pressSound = pressSound.orElse(null);
            return holder;
        })
    );

    @EditorEditable(order = 0)
    private LocalizedMessage message = LocalizedMessage.EMPTY;

    /// Null draws the three images instead.
    @EditorEditable(order = 1, candidates = "themeCandidates")
    private @Nullable Identifier theme;

    @EditorEditable(order = 2)
    private WidgetImageRef normalImage = WidgetImageRef.of(ButtonBlueprint.DEFAULT_NORMAL_IMAGE, true);

    @EditorEditable(order = 3)
    private WidgetImageRef focusedImage = WidgetImageRef.of(ButtonBlueprint.DEFAULT_FOCUSED_IMAGE, true);

    @EditorEditable(order = 4)
    private WidgetImageRef disabledImage = WidgetImageRef.of(ButtonBlueprint.DEFAULT_DISABLED_IMAGE, true);

    @EditorEditable(order = 5)
    private AkytheraProject.@Nullable EditorSoundEventAccessor hoverSound;

    @EditorEditable(order = 6)
    private AkytheraProject.@Nullable EditorSoundEventAccessor pressSound;

    private ButtonHolder() {
        super(WidgetLayoutHolder.sized(100, 20));
    }

    public static ButtonHolder createDefault() {
        return new ButtonHolder();
    }

    public @Nullable Identifier getTheme() {
        return theme;
    }

    private List<Identifier> themeCandidates() {
        return themesOf(ButtonTheme.class);
    }

    @Override
    public void readFrom(final ButtonBlueprint object) {
        layout = WidgetLayoutHolder.of(object.layout());
        message = object.message();
        theme = object.theme();
        normalImage = WidgetImageRef.of(object.normalImage(), true);
        focusedImage = WidgetImageRef.of(object.focusedImage(), true);
        disabledImage = WidgetImageRef.of(object.disabledImage(), true);
        hoverSound = object.hoverSound() == null ? null : AkytheraProject.soundEventOf(object.hoverSound().value().location());
        pressSound = object.pressSound() == null ? null : AkytheraProject.soundEventOf(object.pressSound().value().location());
    }

    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>(super.getDTO());
        dto.put("message", sourceOf(message));
        dto.put("theme", sourceOf(theme));
        dto.put("normal_image", sourceOf(normalImage.makeObject()));
        dto.put("focused_image", sourceOf(focusedImage.makeObject()));
        dto.put("disabled_image", sourceOf(disabledImage.makeObject()));
        dto.put("hover_sound", sourceOf(hoverSound));
        dto.put("press_sound", sourceOf(pressSound));

        return dto;
    }

    @Override
    public ButtonBlueprint makeObject() {
        return new ButtonBlueprint(
            layout.makeObject(), order, message, theme, normalImage.makeObject(), focusedImage.makeObject(), disabledImage.makeObject(),
            hoverSound == null ? null : hoverSound.holder(),
            pressSound == null ? null : pressSound.holder(),
            isEnabled()
        );
    }

    @Override
    public Stream<EditorReference> referencedReferences() {
        return Stream.of(
            super.referencedReferences(),
            Stream.of(normalImage, focusedImage, disabledImage).flatMap(WidgetImageRef::referencedReferences),
            Stream.ofNullable(hoverSound),
            Stream.ofNullable(pressSound)
        ).flatMap(references -> references);
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return AkytheraButton.class;
    }
}
