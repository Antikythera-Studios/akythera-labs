package com.yesman.akytheralabs.akythera.animation.notify.impl;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.yesman.akythera.api.animation.warping.DataDrivenMotionWarpTarget;
import com.yesman.akythera.api.animation.warping.MotionWarpTarget;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.core.blueprint.warping.MotionWarpTargetBlueprint;
import com.yesman.akythera.core.blueprint.warping.MotionWarpTargetScriptBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FreemarkerExporter;
import com.yesman.akytheralabs.akythera.animation.MotionWarpTargetScriptHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleRefProducer;
import com.yesman.akytheralabs.dataholder.EditorHolderCodecs;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.historytrack.FileDeletion;
import com.yesman.akytheralabs.editor.historytrack.FileMove;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.screen.ScriptBlueprintEditorScreen;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.PopupBox;
import com.yesman.akytheralabs.gui.widget.main.CategorizedSelectModal;
import com.yesman.akytheralabs.gui.widget.main.ExportModalGenerator;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// The Details rows of a [MotionWarpTargetRef]: a select box over the built-in targets, the open project's (blue),
/// the loaded data packs' and the target classes, then an `Edit Node Graph` button while a project target is picked.
///
/// The project's targets are edited in place: the modal's `+` adds one named in the list, the rename key renames
/// one, a `✕` deletes one, and a double-click opens its node graph. Its `Compile` button writes every motion warp
/// target node graph of the project as a Java class, see [FreemarkerExporter#exportMotionWarpTargets].
public final class MotionWarpTargetProducer implements EditorWidgetFactory.WidgetProducer {
    private static final Identifier TARGET_ICON = AkytheraLabs.identifier("widget/script");
    private static final int CHOICE_COLOR = 0xFFCCCCCC;

    @Override
    public List<? extends AbstractWidget> produce(final EditorWidgetFactory factory, final EditorWidgetFactory.ValueSlot slot) {
        return List.of(new PopupBox<Object>(
            factory.font(),
            EditorWidgetFactory.columnStart(slot, 0, 1), EditorWidgetFactory.columnSize(slot, 0, 1),
            AnchoredWidget.pixel(0), AnchoredWidget.pixel(EditorWidgetFactory.ROW_H),
            AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
            AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
            slot::get,
            _ -> {},
            value -> value instanceof MotionWarpTargetRef ref ? ref.displayLabel() : Objects.toString(value),
            Component.empty(),
            opened -> {
                Set<ResourceKey<MotionWarpTargetScriptBlueprint>> projectKeys = projectTargets();

                // A built-in target is a type that decodes from no field
                List<CategorizedSelectModal.Choice<MotionWarpTargetRef>> builtIn = AkytheraRegistries.Frozen.MOTION_WARP_TARGET_TYPES.stream()
                    .flatMap(codec -> codec.codec().parse(JsonOps.INSTANCE, new JsonObject()).result().stream())
                    .filter(blueprint -> !(blueprint instanceof MotionWarpTargetBlueprint.ByClass || blueprint instanceof MotionWarpTargetBlueprint.ByData))
                    .<MotionWarpTargetRef>map(MotionWarpTargetRef.Blueprint::new)
                    .map(MotionWarpTargetProducer::choice)
                    .toList();
                List<CategorizedSelectModal.Choice<MotionWarpTargetRef>> project = projectKeys.stream()
                    .sorted(Comparator.comparing(key -> key.identifier().toString()))
                    .map(MotionWarpTargetProducer::projectChoice)
                    .toList();
                List<CategorizedSelectModal.Choice<MotionWarpTargetRef>> dataPack = AkytheraProject.getCurrentProject()
                    .getMergedEntries(AkytheraRegistries.DataPack.MOTION_WARP_TARGET_KEY).stream()
                    .map(Map.Entry::getKey)
                    .filter(key -> !projectKeys.contains(key))
                    .sorted(Comparator.comparing(key -> key.identifier().toString()))
                    .map(key -> MotionWarpTargetRef.of(new MotionWarpTargetBlueprint.ByData(key)))
                    .map(MotionWarpTargetProducer::choice)
                    .toList();
                // A target class is one a user wrote: the built-in targets and the data pack runner are left out
                Set<Identifier> projectScriptTypes = FreemarkerExporter.projectScriptTypes();
                List<CategorizedSelectModal.Choice<MotionWarpTargetRef>> classes = AkytheraRegistries.Frozen.SCRIPT_TYPE.stream()
                    .filter(type -> MotionWarpTarget.class.isAssignableFrom(type.type())
                        && !projectScriptTypes.contains(AkytheraRegistries.Frozen.SCRIPT_TYPE.getKey(type))
                        && !type.type().isInterface()
                        && !Modifier.isAbstract(type.type().getModifiers())
                        && type.type() != DataDrivenMotionWarpTarget.class
                        && !MotionWarpTargetBlueprint.class.isAssignableFrom(type.type()))
                    .<MotionWarpTargetRef>map(type -> new MotionWarpTargetRef.Blueprint(new MotionWarpTargetBlueprint.ByClass(type)))
                    .map(MotionWarpTargetProducer::choice)
                    .toList();

                CategorizedSelectModal.Category<MotionWarpTargetRef> projectCategory = new CategorizedSelectModal.Category<>(
                    Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_CATEGORY_PROJECT), project, new ProjectTargets()
                );

                ModalManager.getInstance().openModalWidgetUnder(opened, new CategorizedSelectModal<>(
                    factory.font(),
                    opened.getWidth(),
                    slot.labelComponent(),
                    List.of(
                        new CategorizedSelectModal.Category<>(Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_CATEGORY_BUILT_IN), builtIn),
                        projectCategory,
                        new CategorizedSelectModal.Category<>(Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_CATEGORY_DATAPACK), dataPack),
                        new CategorizedSelectModal.Category<>(Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_CATEGORY_CLASS), classes)
                    ),
                    picked -> {
                        EditorWidgetFactory.pushSlotChange(slot, picked, opened);
                        opened.valueSetter().accept(picked);
                    }
                ).creatingButton(LocalizedMessage.handwritten("+"), projectCategory).footerButton(LocalizedMessage.translatable(LangKeys.GUI_EXPORT_MODAL_COMPILE), () -> {
                    ModalManager.getInstance().closeModal();
                    ExportModalGenerator.openCompile(
                        factory.font(),
                        Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_COMPILE_WARP_TARGETS_TITLE),
                        new ExportModalGenerator.Compile(
                            key -> key.registryKey().equals(AkytheraRegistries.DataPack.MOTION_WARP_TARGET_KEY),
                            FreemarkerExporter::exportMotionWarpTargets
                        )
                    );
                }));
            }
        ));
    }

    @Override
    public void appendRows(final EditorWidgetFactory factory, final WidgetTable table, final EditorWidgetFactory.ValueSlot slot, final int indent) {
        EditorWidgetFactory.WidgetProducer.super.appendRows(factory, table, slot, indent);

        if (!(slot.get() instanceof MotionWarpTargetRef.Entry entry) || !projectTargets().contains(entry.key())) return;

        table.newRow(indent + EditorWidgetFactory.INDENT, EditorWidgetFactory.makeLabel(factory.font(), Component.translatable(LangKeys.GUI_NOTIFY_TRACKS_FIELD_EVENT_GRAPH)));
        table.addWidget(
            AkytheraButton.buttonBuilder(factory.font(), LocalizedMessage.translatable(LangKeys.GUI_NOTIFY_TRACKS_BUTTON_EDIT_GRAPH), () -> openGraph(entry.key()))
                .xUnits(EditorWidgetFactory.columnStart(slot, 0, 1), EditorWidgetFactory.columnSize(slot, 0, 1))
                .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(EditorWidgetFactory.ROW_H))
                .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
                .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
                .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                .build()
        );
    }

    private static CategorizedSelectModal.Choice<MotionWarpTargetRef> choice(final MotionWarpTargetRef target) {
        return new CategorizedSelectModal.Choice<>(target.displayLabel(), target, CHOICE_COLOR, TARGET_ICON);
    }

    /// The row of a project target in the select modal.
    private static CategorizedSelectModal.Choice<MotionWarpTargetRef> projectChoice(final ResourceKey<MotionWarpTargetScriptBlueprint> key) {
        MotionWarpTargetRef ref = new MotionWarpTargetRef.Entry(EditorHolderCodecs.resolve(AkytheraRegistries.DataPack.MOTION_WARP_TARGET_KEY, key.identifier()));

        return new CategorizedSelectModal.Choice<>(ref.displayLabel(), ref, ParticleRefProducer.PROJECT_COLOR, TARGET_ICON);
    }

    /// The keys of the open project's motion warp targets.
    private static Set<ResourceKey<MotionWarpTargetScriptBlueprint>> projectTargets() {
        return AkytheraProject.getCurrentProject()
            .<MotionWarpTargetScriptBlueprint, MotionWarpTargetScriptHolder>getModifiableEntries(AkytheraRegistries.DataPack.MOTION_WARP_TARGET_KEY).stream()
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    /// Opens the node graph of the project target `key`.
    private static void openGraph(final ResourceKey<MotionWarpTargetScriptBlueprint> key) {
        AkytheraEditorLayoutManager.getInstance().openNewScreen(new ScriptBlueprintEditorScreen<>(
            key, AkytheraProject.getCurrentProject().<MotionWarpTargetScriptBlueprint, MotionWarpTargetScriptHolder>getDataHolderOrThrow(key)));
    }

    /// The project's motion warp targets, made, renamed and deleted in the select modal. Each is one undo step.
    private static final class ProjectTargets implements CategorizedSelectModal.RowEditor<MotionWarpTargetRef> {
        @Override
        public boolean accepts(final String name) {
            return Identifier.isValidPath(name) && projectTargets().stream().noneMatch(key -> key.identifier().getPath().equals(name));
        }

        @Override
        public String nameOf(final CategorizedSelectModal.Choice<MotionWarpTargetRef> choice) {
            return ((MotionWarpTargetRef.Entry) choice.value()).key().identifier().getPath();
        }

        @Override
        public CategorizedSelectModal.Choice<MotionWarpTargetRef> create(final String name) {
            return projectChoice(AkytheraProject.getCurrentProject().createEntry(AkytheraRegistries.DataPack.MOTION_WARP_TARGET_KEY, name, true));
        }

        @Override
        public CategorizedSelectModal.@Nullable Choice<MotionWarpTargetRef> rename(final CategorizedSelectModal.Choice<MotionWarpTargetRef> choice, final String name) {
            AkytheraProject project = AkytheraProject.getCurrentProject();
            ResourceKey<MotionWarpTargetScriptBlueprint> oldKey = ((MotionWarpTargetRef.Entry) choice.value()).key();
            String oldName = oldKey.identifier().getPath();
            ResourceKey<MotionWarpTargetScriptBlueprint> newKey = project.renameEntry(oldKey, name);

            if (newKey == null) return null;

            HistoryManager.getInstance().pushHistory(new FileMove(
                () -> project.renameEntry(oldKey, name),
                () -> project.renameEntry(newKey, oldName)
            ));

            return projectChoice(newKey);
        }

        @Override
        public void remove(final CategorizedSelectModal.Choice<MotionWarpTargetRef> choice) {
            AkytheraProject project = AkytheraProject.getCurrentProject();
            Path file = project.getEntryFilePath(((MotionWarpTargetRef.Entry) choice.value()).key());
            Runnable restore = project.buildRestoreFor(file);

            project.deletePathRecursive(file);
            HistoryManager.getInstance().pushHistory(new FileDeletion(() -> project.deletePathRecursive(file), restore));
        }

        @Override
        public void open(final CategorizedSelectModal.Choice<MotionWarpTargetRef> choice) {
            openGraph(((MotionWarpTargetRef.Entry) choice.value()).key());
        }
    }
}
