package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.ComboBox;
import com.yesman.akythera.client.api.gui.ComboBoxTheme;
import com.yesman.akythera.core.blueprint.widget.ComboBoxBlueprint;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.editor.EditorEditable;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Editor holder for an `akythera:combo_box` ([ComboBoxBlueprint]).
public final class ComboBoxHolder extends WidgetComponentHolder<ComboBoxBlueprint> {
    public static final MapCodec<ComboBoxHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            LAYOUT_FIELD.forGetter(h -> Optional.of(h.layout)),
            LocalizedMessage.CODEC.optionalFieldOf("title", LocalizedMessage.EMPTY).forGetter(h -> h.title),
            LocalizedMessage.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(h -> h.items),
            Codec.INT.optionalFieldOf("max_rows", 8).forGetter(h -> h.maxRows),
            Codec.BOOL.optionalFieldOf("searchable", false).forGetter(h -> h.searchable),
            Codec.BOOL.optionalFieldOf("clearable", true).forGetter(h -> h.clearable),
            Identifier.CODEC.optionalFieldOf("theme").forGetter(ParseUtils.provideOptional(ComboBoxHolder::getTheme))
        ).apply(instance, (layout, title, items, maxRows, searchable, clearable, theme) -> {
            ComboBoxHolder holder = createDefault();
            layout.ifPresent(stored -> holder.layout = stored);
            holder.title = title;
            holder.items = new ArrayList<>(items);
            holder.maxRows = maxRows;
            holder.searchable = searchable;
            holder.clearable = clearable;
            holder.theme = theme.orElse(null);
            return holder;
        })
    );

    @EditorEditable(order = 0)
    private LocalizedMessage title = LocalizedMessage.EMPTY;

    @EditorEditable(order = 1, defaultElement = "newItem")
    private List<LocalizedMessage> items = new ArrayList<>();

    /// Rows the dropdown shows before it scrolls.
    @EditorEditable(order = 2, min = 1, max = 64)
    private int maxRows = 8;

    @EditorEditable(order = 3)
    private boolean searchable;

    @EditorEditable(order = 4)
    private boolean clearable = true;

    /// Null uses the default combo box theme.
    @EditorEditable(order = 5, candidates = "themeCandidates")
    private @Nullable Identifier theme;

    private ComboBoxHolder() {
        super(WidgetLayoutHolder.sized(100, 16));
    }

    public static ComboBoxHolder createDefault() {
        return new ComboBoxHolder();
    }

    /// [#items]' element a `+` adds.
    private static LocalizedMessage newItem() {
        return LocalizedMessage.EMPTY;
    }

    public @Nullable Identifier getTheme() {
        return theme;
    }

    private List<Identifier> themeCandidates() {
        return themesOf(ComboBoxTheme.class);
    }

    @Override
    public void readFrom(final ComboBoxBlueprint object) {
        layout = WidgetLayoutHolder.of(object.layout());
        title = object.title();
        items = new ArrayList<>(object.items());
        maxRows = object.maxRows();
        searchable = object.searchable();
        clearable = object.clearable();
        theme = object.theme();
    }

    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>(super.getDTO());
        dto.put("title", sourceOf(title));
        dto.put("items", sourceOf(items));
        dto.put("max_rows", sourceOf(maxRows));
        dto.put("searchable", sourceOf(searchable));
        dto.put("clearable", sourceOf(clearable));
        dto.put("theme", sourceOf(theme));

        return dto;
    }

    @Override
    public ComboBoxBlueprint makeObject() {
        return new ComboBoxBlueprint(layout.makeObject(), order, title, List.copyOf(items), maxRows, searchable, clearable, theme, isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return ComboBox.class;
    }
}
