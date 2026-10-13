package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraEditBox;
import com.yesman.akythera.client.api.gui.EditBoxTheme;
import com.yesman.akythera.core.blueprint.widget.EditBoxBlueprint;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.editor.EditorEditable;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Editor holder for an `akythera:edit_box` ([EditBoxBlueprint]).
public final class EditBoxHolder extends WidgetComponentHolder<EditBoxBlueprint> {
    public static final MapCodec<EditBoxHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            LAYOUT_FIELD.forGetter(h -> Optional.of(h.layout)),
            LocalizedMessage.CODEC.optionalFieldOf("placeholder", LocalizedMessage.EMPTY).forGetter(h -> h.placeholder),
            Identifier.CODEC.optionalFieldOf("theme").forGetter(ParseUtils.provideOptional(EditBoxHolder::getTheme))
        ).apply(instance, (layout, placeholder, theme) -> {
            EditBoxHolder holder = createDefault();
            layout.ifPresent(stored -> holder.layout = stored);
            holder.placeholder = placeholder;
            holder.theme = theme.orElse(null);
            return holder;
        })
    );

    /// Shown while the box is empty.
    @EditorEditable(order = 0)
    private LocalizedMessage placeholder = LocalizedMessage.EMPTY;

    /// Null uses the default edit box theme.
    @EditorEditable(order = 1, candidates = "themeCandidates")
    private @Nullable Identifier theme;

    private EditBoxHolder() {
        super(WidgetLayoutHolder.sized(100, 16));
    }

    public static EditBoxHolder createDefault() {
        return new EditBoxHolder();
    }

    public @Nullable Identifier getTheme() {
        return theme;
    }

    private List<Identifier> themeCandidates() {
        return themesOf(EditBoxTheme.class);
    }

    @Override
    public void readFrom(final EditBoxBlueprint object) {
        layout = WidgetLayoutHolder.of(object.layout());
        placeholder = object.placeholder();
        theme = object.theme();
    }

    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>(super.getDTO());
        dto.put("placeholder", sourceOf(placeholder));
        dto.put("theme", sourceOf(theme));

        return dto;
    }

    @Override
    public EditBoxBlueprint makeObject() {
        return new EditBoxBlueprint(layout.makeObject(), order, placeholder, theme, isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return AkytheraEditBox.class;
    }
}
