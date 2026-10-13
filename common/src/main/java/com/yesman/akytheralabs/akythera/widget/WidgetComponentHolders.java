package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.core.Akythera;
import com.yesman.akythera.core.blueprint.widget.WidgetUsage;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.dataholder.ComponentHolderRegistry;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// The widget [ComponentHolderRegistry]: the editor holders for the component types of
/// [AkytheraRegistries.Frozen#WIDGET_COMPONENT_TYPES].
public final class WidgetComponentHolders {
    private WidgetComponentHolders() {}

    public static final Identifier BUTTON = Akythera.identifier("button");
    public static final Identifier CHECK_BOX = Akythera.identifier("check_box");
    public static final Identifier COMBO_BOX = Akythera.identifier("combo_box");
    public static final Identifier EDIT_BOX = Akythera.identifier("edit_box");
    public static final Identifier DRAG_VALUE_BOX = Akythera.identifier("drag_value_box");
    public static final Identifier TEXT_AREA = Akythera.identifier("text_area");
    public static final Identifier IMAGE = Akythera.identifier("image");
    public static final Identifier PROGRESSION_BAR = Akythera.identifier("progression_bar");
    public static final Identifier COLOR_PICKER = Akythera.identifier("color_picker");
    public static final Identifier GRID = Akythera.identifier("grid");
    public static final Identifier HORIZONTAL_CONTAINER = Akythera.identifier("horizontal_container");
    public static final Identifier VERTICAL_CONTAINER = Akythera.identifier("vertical_container");
    public static final Identifier WIDGET_BLUEPRINT = Akythera.identifier("widget_blueprint");

    public static final ComponentHolderRegistry REGISTRY = new ComponentHolderRegistry(AkytheraRegistries.Frozen.WIDGET_COMPONENT_TYPES);

    private static final Component CONTAINER = Component.translatable(LangKeys.GUI_WIDGET_SCREEN_MENU_CATEGORY_CONTAINER);
    private static final Component WIDGET = Component.translatable(LangKeys.GUI_WIDGET_SCREEN_MENU_CATEGORY_WIDGET);
    private static final Component USER_WIDGETS = Component.translatable(LangKeys.GUI_WIDGET_SCREEN_MENU_CATEGORY_USER_WIDGETS);

    /// A widget type that takes input, refused anywhere in a [WidgetUsage#HUD] blueprint. Read off the type's
    /// default blueprint, so a type registered elsewhere is covered too.
    private static final ComponentHolderRegistry.TypeAttachRule TAKES_INPUT = (typeId, _, root) -> {
        if (!(root instanceof WidgetBlueprintHolder blueprint) || blueprint.usage() != WidgetUsage.HUD) return null;

        try {
            return REGISTRY.createDefault(typeId, null) instanceof WidgetComponentHolder<?> widget && widget.makeObject().acceptsInput()
                ? Component.translatable(LangKeys.GUI_WIDGET_SCREEN_MENU_REFUSED_HUD_INPUT)
                : null;
        } catch (RuntimeException e) {
            return null; // a default too incomplete to lower, such as a widget blueprint placing nothing yet
        }
    };

    /// `codec` with the widget's [WidgetGraphs] beside the fields it lists, under `graphs`.
    private static <H extends WidgetComponentHolder<?>> MapCodec<H> withGraphs(final MapCodec<H> codec) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
            codec.forGetter(holder -> holder),
            WidgetGraphs.CODEC.optionalFieldOf("graphs", new WidgetGraphs()).forGetter(WidgetComponentHolder::getGraphs)
        ).apply(instance, (holder, graphs) -> {
            holder.setGraphs(graphs);
            return holder;
        }));
    }

    static {
        REGISTRY.register(BUTTON, ButtonHolder.class, _ -> ButtonHolder.createDefault(), withGraphs(ButtonHolder.EDITOR_CODEC));
        REGISTRY.register(CHECK_BOX, CheckBoxHolder.class, _ -> CheckBoxHolder.createDefault(), withGraphs(CheckBoxHolder.EDITOR_CODEC));
        REGISTRY.register(COMBO_BOX, ComboBoxHolder.class, _ -> ComboBoxHolder.createDefault(), withGraphs(ComboBoxHolder.EDITOR_CODEC));
        REGISTRY.register(EDIT_BOX, EditBoxHolder.class, _ -> EditBoxHolder.createDefault(), withGraphs(EditBoxHolder.EDITOR_CODEC));
        REGISTRY.register(DRAG_VALUE_BOX, DragValueBoxHolder.class, _ -> DragValueBoxHolder.createDefault(), withGraphs(DragValueBoxHolder.EDITOR_CODEC));
        REGISTRY.register(TEXT_AREA, TextAreaHolder.class, _ -> TextAreaHolder.createDefault(), withGraphs(TextAreaHolder.EDITOR_CODEC));
        REGISTRY.register(IMAGE, ImageHolder.class, _ -> ImageHolder.createDefault(), withGraphs(ImageHolder.EDITOR_CODEC));
        REGISTRY.register(PROGRESSION_BAR, ProgressionBarHolder.class, _ -> ProgressionBarHolder.createDefault(), withGraphs(ProgressionBarHolder.EDITOR_CODEC));
        REGISTRY.register(COLOR_PICKER, ColorPickerHolder.class, _ -> ColorPickerHolder.createDefault(), withGraphs(ColorPickerHolder.EDITOR_CODEC));
        REGISTRY.register(GRID, GridHolder.class, _ -> GridHolder.createDefault(), withGraphs(GridHolder.EDITOR_CODEC));
        REGISTRY.register(HORIZONTAL_CONTAINER, LinearContainerHolder.Horizontal.class,
            _ -> LinearContainerHolder.Horizontal.createDefault(), withGraphs(LinearContainerHolder.Horizontal.EDITOR_CODEC));
        REGISTRY.register(VERTICAL_CONTAINER, LinearContainerHolder.Vertical.class,
            _ -> LinearContainerHolder.Vertical.createDefault(), withGraphs(LinearContainerHolder.Vertical.EDITOR_CODEC));
        REGISTRY.register(WIDGET_BLUEPRINT, WidgetBlueprintRefHolder.class, _ -> WidgetBlueprintRefHolder.createDefault(), withGraphs(WidgetBlueprintRefHolder.EDITOR_CODEC));

        REGISTRY.setCommonRule(TAKES_INPUT);

        // Containers; the other built-in widgets; the widget blueprints of the project and the world, each placed
        // with its blueprint picked. Neither an empty placement nor another mod's widget types are offered
        REGISTRY.setAttachMenu(new ComponentHolderRegistry.AttachMenu() {
            @Override
            public List<Component> categories() {
                return List.of(CONTAINER, WIDGET, USER_WIDGETS);
            }

            @Override
            public @Nullable Component categoryOf(final Identifier typeId) {
                if (typeId.equals(GRID) || typeId.equals(HORIZONTAL_CONTAINER) || typeId.equals(VERTICAL_CONTAINER)) return CONTAINER;

                return typeId.getNamespace().equals(Akythera.MODID) && !typeId.equals(WIDGET_BLUEPRINT) ? WIDGET : null;
            }

            @Override
            public List<ComponentHolderRegistry.Preset> presets(final ComponentAttachableHolder root) {
                if (!(root instanceof WidgetBlueprintHolder blueprint)) return List.of();

                return WidgetBlueprintRefHolder.placeableIn(blueprint).stream()
                    .map(widget -> {
                        Identifier id = EditorHolderCodecs.entryId(widget);

                        return new ComponentHolderRegistry.Preset(
                            USER_WIDGETS, id.getNamespace(), Component.literal(id.getPath()), WIDGET_BLUEPRINT, id.getPath().substring(id.getPath().lastIndexOf('/') + 1),
                            () -> WidgetBlueprintRefHolder.of(widget)
                        );
                    })
                    .toList();
            }
        });
    }
}
