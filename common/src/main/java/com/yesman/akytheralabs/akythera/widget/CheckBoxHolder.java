package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.CheckBox;
import com.yesman.akythera.client.api.gui.CheckBoxTheme;
import com.yesman.akythera.core.blueprint.widget.CheckBoxBlueprint;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.editor.EditorEditable;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Editor holder for an `akythera:check_box` ([CheckBoxBlueprint]).
public final class CheckBoxHolder extends WidgetComponentHolder<CheckBoxBlueprint> {
    public static final MapCodec<CheckBoxHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            LAYOUT_FIELD.forGetter(h -> Optional.of(h.layout)),
            LocalizedMessage.CODEC.optionalFieldOf("message", LocalizedMessage.EMPTY).forGetter(h -> h.message),
            Identifier.CODEC.optionalFieldOf("theme").forGetter(ParseUtils.provideOptional(CheckBoxHolder::getTheme)),
            Codec.BOOL.optionalFieldOf("preview_checked", false).forGetter(h -> h.previewChecked)
        ).apply(instance, (layout, message, theme, previewChecked) -> {
            CheckBoxHolder holder = createDefault();
            layout.ifPresent(stored -> holder.layout = stored);
            holder.message = message;
            holder.theme = theme.orElse(null);
            holder.previewChecked = previewChecked;
            return holder;
        })
    );

    @EditorEditable(order = 0)
    private LocalizedMessage message = LocalizedMessage.EMPTY;

    /// Null uses the default check box theme.
    @EditorEditable(order = 1, candidates = "themeCandidates")
    private @Nullable Identifier theme;

    /// Whether the canvas draws the box checked. Editor-only: the game starts every box unchecked.
    @EditorEditable(order = 2)
    private boolean previewChecked;

    private CheckBoxHolder() {
        super(WidgetLayoutHolder.sized(100, 14));
    }

    public static CheckBoxHolder createDefault() {
        return new CheckBoxHolder();
    }

    public @Nullable Identifier getTheme() {
        return theme;
    }

    public boolean isPreviewChecked() {
        return previewChecked;
    }

    private List<Identifier> themeCandidates() {
        return themesOf(CheckBoxTheme.class);
    }

    @Override
    public void readFrom(final CheckBoxBlueprint object) {
        layout = WidgetLayoutHolder.of(object.layout());
        message = object.message();
        theme = object.theme();
    }

    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>(super.getDTO());
        dto.put("message", sourceOf(message));
        dto.put("theme", sourceOf(theme));

        return dto;
    }

    @Override
    public CheckBoxBlueprint makeObject() {
        return new CheckBoxBlueprint(layout.makeObject(), order, message, theme, isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return CheckBox.class;
    }
}
