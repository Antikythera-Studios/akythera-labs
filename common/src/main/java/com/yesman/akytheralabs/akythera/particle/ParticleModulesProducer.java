package com.yesman.akytheralabs.akythera.particle;

import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraButton;
import com.yesman.akythera.client.api.gui.WidgetTable;
import com.yesman.akythera.client.api.gui.common.AnchoredWidget;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.particle.AkytheraParticle;
import com.yesman.akythera.client.api.particle.module.DataDrivenParticleModule;
import com.yesman.akythera.client.api.particle.module.ParticleInitializerModule;
import com.yesman.akythera.client.api.particle.module.ParticleUpdateModule;
import com.yesman.akythera.core.blueprint.particle.ParticleInitializerBlueprint;
import com.yesman.akythera.core.blueprint.particle.ParticleUpdaterBlueprint;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FreemarkerExporter;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.editor.EditorWidgetFactory;
import com.yesman.akytheralabs.editor.historytrack.DataChange;
import com.yesman.akytheralabs.editor.historytrack.FileDeletion;
import com.yesman.akytheralabs.editor.historytrack.FileMove;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.editor.historytrack.ListInsert;
import com.yesman.akytheralabs.editor.historytrack.ListRemove;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.screen.ParticleSystemScreen;
import com.yesman.akytheralabs.gui.widget.AkytheraLabsButtonTheme;
import com.yesman.akytheralabs.gui.widget.PopupBox;
import com.yesman.akytheralabs.gui.widget.main.CategorizedSelectModal;
import com.yesman.akytheralabs.gui.widget.main.ExportModalGenerator;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/// The Details rows of a [ParticleModuleList]: a header with a `+` that adds a row, then per module a select box with
/// a `✕`, and under it the parameters of a built-in module.
///
/// The select box lists the modules the emitter's particle takes and the list does not hold yet: the built-in
/// ones, the open project's (blue), the loaded data packs', and the module classes. The project's are edited in
/// place: its `+` adds one named in the list, the rename key renames one, a `✕` deletes one, and a double-click opens
/// its Event Graph on the [ParticleSystemScreen]. Its `Compile`
/// button writes every module node graph of the project as a Java class, see
/// [FreemarkerExporter#exportParticleModules].
///
/// A parameter row edits the literal, with an `ƒ` that opens the parameter's node graph. A parameter with a graph
/// shows a button that opens it instead, with a `✕` that drops the graph for the literal.
public final class ParticleModulesProducer implements EditorWidgetFactory.WidgetProducer {
    private static final Identifier MODULE_ICON = AkytheraLabs.identifier("widget/module");
    private static final int CHOICE_COLOR = 0xFFCCCCCC;

    /// Carry the row settings of a parameter's literal: the defaults, and those of a direction.
    @EditorEditable
    private static final Object PLAIN_CARRIER = new Object();

    @EditorEditable(direction = true)
    private static final Object DIRECTION_CARRIER = new Object();

    private static final EditorEditable PLAIN;
    private static final EditorEditable DIRECTION;

    static {
        try {
            PLAIN = ParticleModulesProducer.class.getDeclaredField("PLAIN_CARRIER").getAnnotation(EditorEditable.class);
            DIRECTION = ParticleModulesProducer.class.getDeclaredField("DIRECTION_CARRIER").getAnnotation(EditorEditable.class);
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /// Nothing for a caller with one row to give: the rows need a table.
    @Override
    public List<? extends AbstractWidget> produce(final EditorWidgetFactory factory, final EditorWidgetFactory.ValueSlot slot) {
        return List.of();
    }

    @Override
    public void appendRows(final EditorWidgetFactory factory, final WidgetTable table, final EditorWidgetFactory.ValueSlot slot, final int indent) {
        if (!(slot.get() instanceof ParticleModuleList list) || !(slot.owner() instanceof ParticleEmitterHolder emitter)) return;

        Runnable rebuild = slot instanceof EditorWidgetFactory.RebuildingSlot rebuilding ? rebuilding.rebuild() : () -> {};
        List<ParticleModuleHolder> modules = list.modules();
        int gutter = EditorWidgetFactory.ENTRY_BUTTON_GUTTER;

        table.newRow(indent, EditorWidgetFactory.makeLabel(factory.font(), slot.labelComponent()));
        table.addWidget(factory.sectionButton(LocalizedMessage.handwritten("+"), () -> {
            ParticleModuleHolder added = new ParticleModuleHolder(list.isUpdater());

            modules.add(added);
            HistoryManager.getInstance().pushHistory(new ListInsert<>(modules, modules.size() - 1, added, rebuild));
            rebuild.run();
        }));

        for (int i = 0; i < modules.size(); i++) {
            int index = i;
            ParticleModuleHolder module = modules.get(index);

            // Picks a module as one undo step, parameters and all
            Consumer<ModuleRef> pick = picked -> {
                ParticleModuleHolder.Stored before = module.toStored();

                module.setRef(picked);
                HistoryManager.getInstance().pushHistory(new DataChange<>(before, module.toStored(), stored -> {
                    module.load(stored);
                    rebuild.run();
                }));
                rebuild.run();
            };

            table.newRow(indent + EditorWidgetFactory.INDENT, EditorWidgetFactory.makeLabel(factory.font(), Component.literal("#" + (index + 1))));
            table.addWidget(new PopupBox<Object>(
                factory.font(),
                EditorWidgetFactory.columnStart(0, 1, gutter), EditorWidgetFactory.columnSize(0, 1, gutter),
                AnchoredWidget.pixel(0), AnchoredWidget.pixel(EditorWidgetFactory.ROW_H),
                AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH,
                AnchoredWidget.VerticalAnchorType.TOP_HEIGHT,
                module::getRef,
                _ -> {},
                value -> value instanceof ModuleRef ref ? ref.displayLabel() : String.valueOf(value),
                Component.empty(),
                opened -> {
                    boolean updater = list.isUpdater();
                    ResourceKey<Registry<Object>> registryKey = moduleRegistry(updater);
                    Class<?> moduleInterface = updater ? ParticleUpdateModule.class : ParticleInitializerModule.class;
                    Class<? extends AkytheraParticle> particleClass = emitter.getParticle().particleClass();
                    Set<ModuleRef> taken = modules.stream().filter(other -> other != module).map(ParticleModuleHolder::getRef).collect(Collectors.toSet());
                    Set<Identifier> projectIds = projectModules(registryKey);

                    List<CategorizedSelectModal.Choice<ModuleRef>> builtIn = ParticleModuleHolder.builtInTypes(updater).stream()
                        .filter(type -> particleClass == null || ParticleModuleHolder.appliesTo(type, updater).isAssignableFrom(particleClass))
                        .<ModuleRef>map(ModuleRef.BuiltIn::new)
                        .filter(ref -> !taken.contains(ref))
                        .map(ref -> new CategorizedSelectModal.Choice<>(ref.displayLabel(), ref, CHOICE_COLOR, MODULE_ICON))
                        .toList();
                    List<CategorizedSelectModal.Choice<ModuleRef>> project = projectIds.stream()
                        .<ModuleRef>map(id -> ModuleRef.ByData.of(updater, id))
                        .filter(ref -> !taken.contains(ref))
                        .map(ParticleModulesProducer::projectChoice)
                        .toList();
                    List<CategorizedSelectModal.Choice<ModuleRef>> dataPack = StaticRegistryAccess.getRegistryAccess().lookupOrThrow(registryKey).keySet().stream()
                        .filter(id -> !projectIds.contains(id))
                        .<ModuleRef>map(id -> ModuleRef.ByData.of(updater, id))
                        .filter(ref -> !taken.contains(ref))
                        .map(ref -> new CategorizedSelectModal.Choice<>(ref.displayLabel(), ref, CHOICE_COLOR, MODULE_ICON))
                        .toList();
                    // A module class is one a user wrote: the built-in modules and the data pack runner are left out
                    List<CategorizedSelectModal.Choice<ModuleRef>> classes = AkytheraRegistries.Frozen.SCRIPT_TYPE.stream()
                        .filter(type -> moduleInterface.isAssignableFrom(type.type())
                            && !Modifier.isAbstract(type.type().getModifiers())
                            && type.type() != DataDrivenParticleModule.class
                            && !ParticleInitializerBlueprint.class.isAssignableFrom(type.type())
                            && !ParticleUpdaterBlueprint.class.isAssignableFrom(type.type()))
                        .<ModuleRef>map(ModuleRef.ByClass::new)
                        .filter(ref -> !taken.contains(ref))
                        .map(ref -> new CategorizedSelectModal.Choice<>(ref.displayLabel(), ref, CHOICE_COLOR, MODULE_ICON))
                        .toList();

                    CategorizedSelectModal.Category<ModuleRef> projectCategory = new CategorizedSelectModal.Category<>(
                        Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_CATEGORY_PROJECT), project, new ProjectModules(updater)
                    );

                    ModalManager.getInstance().openModalWidgetUnder(opened, new CategorizedSelectModal<>(
                        factory.font(),
                        opened.getWidth(),
                        Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_MODULE_SELECT_TITLE),
                        List.of(
                            new CategorizedSelectModal.Category<>(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_CATEGORY_BUILT_IN), builtIn),
                            projectCategory,
                            new CategorizedSelectModal.Category<>(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_CATEGORY_DATAPACK), dataPack),
                            new CategorizedSelectModal.Category<>(Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_CATEGORY_CLASS), classes)
                        ),
                        pick
                    ).creatingButton(LocalizedMessage.handwritten("+"), projectCategory).footerButton(LocalizedMessage.translatable(LangKeys.GUI_EXPORT_MODAL_COMPILE), () -> {
                        ModalManager.getInstance().closeModal();

                        if (AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() instanceof ParticleSystemScreen screen) {
                            screen.flushNodeGraphs();
                        }

                        ExportModalGenerator.openCompile(
                            factory.font(),
                            Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_COMPILE_MODULES_TITLE),
                            new ExportModalGenerator.Compile(
                                key -> key.registryKey().equals(moduleRegistry(false)) || key.registryKey().equals(moduleRegistry(true)),
                                FreemarkerExporter::exportParticleModules
                            )
                        );
                    }));
                }
            ));
            table.addWidget(factory.sectionButton(LocalizedMessage.handwritten("✕"), () -> {
                modules.remove(index);
                HistoryManager.getInstance().pushHistory(new ListRemove<>(modules, index, module, rebuild));
                rebuild.run();
            }));

            int paramIndent = indent + EditorWidgetFactory.INDENT * 2;

            for (ModuleParamHolder param : module.getParams()) {
                Component title = Component.literal(module.getRef().displayLabel() + ": " + param.displayName());
                Runnable openGraph = () -> {
                    if (AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() instanceof ParticleSystemScreen screen) {
                        screen.openParameterGraph(param, title, rebuild);
                    }
                };

                if (param.hasGraph()) {
                    table.newRow(paramIndent, EditorWidgetFactory.makeLabel(factory.font(), Component.literal(param.displayName())));
                    table.addWidget(
                        AkytheraButton.buttonBuilder(factory.font(), LocalizedMessage.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_BUTTON_EDIT_GRAPH), openGraph)
                            .xUnits(EditorWidgetFactory.columnStart(0, 1, gutter), EditorWidgetFactory.columnSize(0, 1, gutter))
                            .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(EditorWidgetFactory.ROW_H))
                            .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
                            .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
                            .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                            .build()
                    );
                    table.addWidget(factory.sectionButton(LocalizedMessage.handwritten("✕"), () -> {
                        HistoryManager.getInstance().pushHistory(new DataChange<>(param.getGraph(), ScriptGraphData.EMPTY, graph -> {
                            param.setGraph(graph);
                            rebuild.run();
                        }));
                        param.setGraph(ScriptGraphData.EMPTY);
                        rebuild.run();
                    }));
                    continue;
                }

                EditorWidgetFactory.ValueSlot literal = new EditorWidgetFactory.PropertySlot(
                    param.displayName(), param.shape(), param, param::getLiteral, value -> module.writeLiteral(param, Objects.requireNonNull(value)),
                    param.declared().direction() ? DIRECTION : PLAIN, gutter
                );
                boolean bounded = !param.declared().atMost().isEmpty() || !param.declared().atLeast().isEmpty();

                // A bounded literal may land on another value than the one typed, which the rebuilt row shows
                factory.appendSlot(table, bounded ? new EditorWidgetFactory.RebuildingSlot(literal, rebuild) : literal, paramIndent, rebuild);
                table.addWidget(factory.sectionButton(LocalizedMessage.handwritten("ƒ"), openGraph));
            }

            if (module.getRef() instanceof ModuleRef.ByData byData && projectModules(moduleRegistry(list.isUpdater())).contains(byData.id())) {
                table.newRow(paramIndent, EditorWidgetFactory.makeLabel(factory.font(), Component.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_WIDGET_EVENT_GRAPH)));
                table.addWidget(
                    AkytheraButton.buttonBuilder(factory.font(), LocalizedMessage.translatable(LangKeys.GUI_PARTICLE_SYSTEM_SCREEN_BUTTON_EDIT_GRAPH), () -> {
                            if (AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() instanceof ParticleSystemScreen screen) {
                                screen.openModuleGraph(list.isUpdater(), byData.id());
                            }
                        })
                        .xUnits(EditorWidgetFactory.columnStart(0, 1, gutter), EditorWidgetFactory.columnSize(0, 1, gutter))
                        .yUnits(AnchoredWidget.pixel(0), AnchoredWidget.pixel(EditorWidgetFactory.ROW_H))
                        .horizontalAnchorType(AnchoredWidget.HorizontalAnchorType.LEFT_WIDTH)
                        .verticalAnchorType(AnchoredWidget.VerticalAnchorType.TOP_HEIGHT)
                        .theme(AkytheraLabsButtonTheme.AKYTHERA_EDITOR_BUTTON)
                        .build()
                );
            }
        }
    }

    /// The data pack registry of module node graphs: the updaters', or the initializers'.
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static ResourceKey<Registry<Object>> moduleRegistry(final boolean updater) {
        return (ResourceKey)(updater ? AkytheraRegistries.DataPack.PARTICLE_UPDATER_KEY : AkytheraRegistries.DataPack.PARTICLE_INITIALIZER_KEY);
    }

    /// The row of a project module in the module select modal.
    private static CategorizedSelectModal.Choice<ModuleRef> projectChoice(final ModuleRef ref) {
        return new CategorizedSelectModal.Choice<>(ref.displayLabel(), ref, ParticleRefProducer.PROJECT_COLOR, MODULE_ICON);
    }

    /// The project modules of the initializer registry, or of the updater one when `updater`, made, renamed and
    /// deleted in the module select modal. A rename or a delete first closes the module's Event Graph tab, saving it.
    /// Each is one undo step.
    private record ProjectModules(boolean updater) implements CategorizedSelectModal.RowEditor<ModuleRef> {
        @Override
        public boolean accepts(final String name) {
            return Identifier.isValidPath(name) && projectModules(moduleRegistry(updater)).stream().noneMatch(id -> id.getPath().equals(name));
        }

        @Override
        public String nameOf(final CategorizedSelectModal.Choice<ModuleRef> choice) {
            return ((ModuleRef.ByData) choice.value()).id().getPath();
        }

        @Override
        public CategorizedSelectModal.Choice<ModuleRef> create(final String name) {
            Identifier id = AkytheraProject.getCurrentProject().createEntry(moduleRegistry(updater), name, true).identifier();

            return projectChoice(ModuleRef.ByData.of(updater, id));
        }

        @Override
        public CategorizedSelectModal.@Nullable Choice<ModuleRef> rename(final CategorizedSelectModal.Choice<ModuleRef> choice, final String name) {
            AkytheraProject project = AkytheraProject.getCurrentProject();
            ModuleRef.ByData ref = (ModuleRef.ByData) choice.value();
            ResourceKey<Object> oldKey = ref.key();
            String oldName = oldKey.identifier().getPath();

            closeModuleGraph(oldKey.identifier());

            ResourceKey<Object> newKey = project.renameEntry(oldKey, name);

            if (newKey == null) return null;

            HistoryManager.getInstance().pushHistory(new FileMove(
                () -> project.renameEntry(oldKey, name),
                () -> project.renameEntry(newKey, oldName)
            ));

            return projectChoice(ref);
        }

        @Override
        public void remove(final CategorizedSelectModal.Choice<ModuleRef> choice) {
            AkytheraProject project = AkytheraProject.getCurrentProject();
            ResourceKey<Object> key = ((ModuleRef.ByData) choice.value()).key();

            closeModuleGraph(key.identifier());

            Path file = project.getEntryFilePath(key);
            Runnable restore = project.buildRestoreFor(file);

            project.deletePathRecursive(file);
            HistoryManager.getInstance().pushHistory(new FileDeletion(() -> project.deletePathRecursive(file), restore));
        }

        @Override
        public void open(final CategorizedSelectModal.Choice<ModuleRef> choice) {
            if (AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() instanceof ParticleSystemScreen screen) {
                screen.openModuleGraph(updater, ((ModuleRef.ByData) choice.value()).id());
            }
        }

        private void closeModuleGraph(final Identifier id) {
            if (AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() instanceof ParticleSystemScreen screen) {
                screen.closeModuleGraph(updater, id);
            }
        }
    }

    /// The ids of the open project's entries of `registryKey`.
    private static Set<Identifier> projectModules(final ResourceKey<Registry<Object>> registryKey) {
        return AkytheraProject.getCurrentProject().getModifiableEntries(registryKey).stream()
            .map(entry -> entry.getKey().identifier())
            .collect(Collectors.toSet());
    }
}
