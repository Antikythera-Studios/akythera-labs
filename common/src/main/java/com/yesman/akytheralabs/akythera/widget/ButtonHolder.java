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
import com.yesman.akytheralabs.editor.EditorEditable;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Editor holder for an `akythera:button` ([ButtonBlueprint]).
public final class ButtonHolder extends WidgetComponentHolder<ButtonBlueprint> {
    public static final MapCodec<ButtonHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            LAYOUT_FIELD.forGetter(h -> Optional.of(h.layout)),
            LocalizedMessage.CODEC.optionalFieldOf("message", LocalizedMessage.EMPTY).forGetter(h -> h.message),
            Identifier.CODEC.optionalFieldOf("theme").forGetter(ParseUtils.provideOptional(ButtonHolder::getTheme)),
            WidgetImage.CODEC.optionalFieldOf("normal_image", ButtonBlueprint.DEFAULT_NORMAL_IMAGE).forGetter(h -> h.normalImage),
            WidgetImage.CODEC.optionalFieldOf("focused_image", ButtonBlueprint.DEFAULT_FOCUSED_IMAGE).forGetter(h -> h.focusedImage),
            WidgetImage.CODEC.optionalFieldOf("disabled_image", ButtonBlueprint.DEFAULT_DISABLED_IMAGE).forGetter(h -> h.disabledImage),
            SoundEvent.CODEC.optionalFieldOf("hover_sound").forGetter(h -> Optional.ofNullable(h.hoverSound).map(ParseUtils::holderOf)),
            SoundEvent.CODEC.optionalFieldOf("press_sound").forGetter(h -> Optional.ofNullable(h.pressSound).map(ParseUtils::holderOf))
        ).apply(instance, (layout, message, theme, normal, focused, disabled, hoverSound, pressSound) -> {
            ButtonHolder holder = createDefault();
            layout.ifPresent(stored -> holder.layout = stored);
            holder.message = message;
            holder.theme = theme.orElse(null);
            holder.normalImage = normal;
            holder.focusedImage = focused;
            holder.disabledImage = disabled;
            holder.hoverSound = hoverSound.map(Holder::value).orElse(null);
            holder.pressSound = pressSound.map(Holder::value).orElse(null);
            return holder;
        })
    );

    @EditorEditable(order = 0)
    private LocalizedMessage message = LocalizedMessage.EMPTY;

    /// Null draws the three images instead.
    @EditorEditable(order = 1, candidates = "themeCandidates")
    private @Nullable Identifier theme;

    @EditorEditable(order = 2, sprite = true)
    private WidgetImage normalImage = ButtonBlueprint.DEFAULT_NORMAL_IMAGE;

    @EditorEditable(order = 3, sprite = true)
    private WidgetImage focusedImage = ButtonBlueprint.DEFAULT_FOCUSED_IMAGE;

    @EditorEditable(order = 4, sprite = true)
    private WidgetImage disabledImage = ButtonBlueprint.DEFAULT_DISABLED_IMAGE;

    @EditorEditable(order = 5)
    private @Nullable SoundEvent hoverSound;

    @EditorEditable(order = 6)
    private @Nullable SoundEvent pressSound;

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
        normalImage = object.normalImage();
        focusedImage = object.focusedImage();
        disabledImage = object.disabledImage();
        hoverSound = object.hoverSound() == null ? null : object.hoverSound().value();
        pressSound = object.pressSound() == null ? null : object.pressSound().value();
    }

    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>(super.getDTO());
        dto.put("message", sourceOf(message));
        dto.put("theme", sourceOf(theme));
        dto.put("normal_image", sourceOf(normalImage));
        dto.put("focused_image", sourceOf(focusedImage));
        dto.put("disabled_image", sourceOf(disabledImage));
        dto.put("hover_sound", sourceOf(hoverSound));
        dto.put("press_sound", sourceOf(pressSound));

        return dto;
    }

    @Override
    public ButtonBlueprint makeObject() {
        return new ButtonBlueprint(
            layout.makeObject(), order, message, theme, normalImage, focusedImage, disabledImage,
            hoverSound == null ? null : ParseUtils.holderOf(hoverSound),
            pressSound == null ? null : ParseUtils.holderOf(pressSound),
            isEnabled()
        );
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return AkytheraButton.class;
    }
}
