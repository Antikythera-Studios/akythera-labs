package com.yesman.akytheralabs;

import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.world.ability.Ability;
import com.yesman.akythera.api.world.ability.AbilityCue;
import com.yesman.akythera.api.world.ability.AbilityCueScript;
import com.yesman.akythera.api.world.patch.entity.EntityPatch;
import com.yesman.akythera.client.api.particle.AkytheraParticleEmitter;
import com.yesman.akythera.core.animation.animator.BlendingAnimator;
import com.yesman.akythera.core.blueprint.AbilityBlueprint;
import com.yesman.akythera.core.blueprint.AbilityCueBlueprint;
import com.yesman.akythera.core.blueprint.DataDrivenEntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.EntityPatchBlueprint;
import com.yesman.akythera.core.blueprint.script.AnimatorScript;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.DataDrivenAnimatorScript;
import com.yesman.akythera.core.blueprint.script.EntityPatchScript;
import com.yesman.akythera.core.blueprint.script.ScriptVariables;
import com.yesman.akythera.core.blueprint.widget.DataDrivenWidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import com.yesman.akytheralabs.akythera.particle.ParticleModuleScriptHolder;
import com.yesman.akytheralabs.akythera.particle.ParticleModulesProducer;
import com.yesman.akytheralabs.akythera.widget.WidgetBlueprintHolder;
import com.yesman.akytheralabs.dataholder.DataBlockHolder;
import com.yesman.akytheralabs.dataholder.DataBlockRegistries;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.ProjectObjectRegistry;
import com.yesman.akytheralabs.akythera.ability.AbilityBlueprintHolder;
import com.yesman.akytheralabs.akythera.animation.AnimatorScriptHolder;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.data.DataProvider;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.TypeVariable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@NullMarked
public final class FreemarkerExporter {
    private FreemarkerExporter() {}

    /// Sub-directory (and sub-package) the generated [DataProvider] classes land in.
    private static final String PROVIDERS_DIR = "providers";
    /// Sub-directory (and sub-package) the generated `*AnimatorScript` classes land in.
    private static final String SCRIPTS_DIR = "scripts\\animator";
    /// Sub-directory (and sub-package) the generated `*EntityPatchScript` classes land in.
    private static final String PATCHES_DIR = "scripts\\entitypatches";
    /// Sub-directory (and sub-package) the generated `*Ability` classes land in.
    private static final String ABILITIES_DIR = "scripts\\abilities";
    /// Sub-directory (and sub-package) the generated `*CueScript` classes land in.
    private static final String ABILITY_CUES_DIR = "scripts\\abilitycues";
    /// Sub-directory (and sub-package) the generated `*ScreenScript`, `*WidgetScript` and `*HUDScript` classes
    /// land in.
    private static final String WIDGETS_DIR = "scripts\\widgets";
    /// Sub-directory (and sub-package) the generated `*ParticleInitializer` and `*ParticleUpdater` classes land in.
    private static final String PARTICLES_DIR = "scripts\\particles";
    /// Sub-directory (and sub-package) the generated [EnumerableDataBlock] enums land in.
    private static final String DATA_BLOCKS_DIR = "datablocks";

    /// The owner handle [ScriptNodeSourceCompiler] lowers a graph `self` to, per script kind: an animator
    /// script's field is `animator`, an entity-patch script's is `owner`, and an ability is its own.
    private static final String ANIMATOR_SELF = "this.animator";
    private static final String ENTITY_PATCH_SELF = "this.owner";
    private static final String ABILITY_SELF = "this";
    private static final String ABILITY_CUE_SELF = "this.cue";
    private static final String WIDGET_SELF = "this.owner";
    private static final String PARTICLE_MODULE_SELF = "this.emitter";

    /// The line a generated particle module's event starts with, which captures [#PARTICLE_MODULE_SELF].
    private static final List<String> PARTICLE_MODULE_PROLOGUE = List.of("this.emitter = particle.getEmitter();");

    /// The nested class a generated ability keeps its variables in, one per equipped instance, and the name a
    /// function the blueprint declares receives it under.
    private static final String ABILITY_STATE_CLASS = "State";
    private static final String ABILITY_STATE_PARAMETER = "abilityState";

    /// The declared types of [#ANIMATOR_SELF] and [#ENTITY_PATCH_SELF] in the generated classes.
    private static final Class<?> ANIMATOR_SELF_TYPE = BlendingAnimator.class;
    private static final Class<?> ENTITY_PATCH_SELF_TYPE = EntityPatch.class;

    /// Per-registry code-generation descriptor: which template to render, the variable name the template
    /// iterates over (`<#list ${collectionVar} as ...>`), and the suffix appended to the project's
    /// PascalCase name to form the generated class (and thus the output file name).
    private record SourceTemplate(String templateFile, String collectionVar, String classSuffix) {}

    /// One entry per registry type the exporter can generate. Add a row here (plus its `.ftl`) to make a
    /// new registry type exportable.
    private static final Map<ResourceKey<? extends Registry<?>>, SourceTemplate> FTL_FILES = Map.of(
        AkytheraRegistries.DataPack.ANIM_SEQUENCE_KEY,
        new SourceTemplate("animation_sequence_template.ftl", "sequences", "AnimationSequences"),

        AkytheraRegistries.DataPack.BLEND_SPACE_2D_KEY,
        new SourceTemplate("blend_space_2d_template.ftl", "blend_spaces", "BlendSpace2Ds"),

        AkytheraRegistries.DataPack.ANIM_MONTAGE_KEY,
        new SourceTemplate("animation_montage_template.ftl", "montages", "AnimationMontages"),

        AkytheraRegistries.DataPack.ENTITY_PATCH_KEY,
        new SourceTemplate("entity_patch_blueprint_template.ftl", "entity_patches", "EntityPatches"),

        AkytheraRegistries.DataPack.ITEM_PATCH_KEY,
        new SourceTemplate("item_patch_blueprint_template.ftl", "item_patches", "ItemPatches"),

        AkytheraRegistries.DataPack.PARTICLE_SYSTEM_KEY,
        new SourceTemplate("particle_system_template.ftl", "particle_systems", "ParticleSystems"),

        AkytheraRegistries.DataPack.PARTICLE_PIECE_KEY,
        new SourceTemplate("particle_piece_template.ftl", "particle_pieces", "ParticlePieces")
    );

    /// Export the project as [DataProvider] source. JSON objects are generated by each mod platform's datagen
    /// task once the generated providers are compiled into the target mod.
    ///
    /// Applies globally to every registry type registered in [#FTL_FILES]:
    /// - `sourceCodeRoot` — directory the generated `.java` files are written into.
    /// - `packageName` — fills the `${package}` token in every template.
    public static void exportProject(AkytheraProject project, Path sourceCodeRoot, String packageName) {
        for (ResourceKey<? extends Registry<?>> registryKey : FTL_FILES.keySet()) {
            exportRegistryProvider(project, registryKey, sourceCodeRoot, packageName);
        }

        for (DataBlockRegistries.Entry<?, ?> entry : DataBlockRegistries.all()) {
            exportDataBlockEnum(project, entry.registryKey(), sourceCodeRoot, packageName);
        }

        exportAnimatorScripts(project, sourceCodeRoot, packageName);
        exportEntityPatchScripts(project, sourceCodeRoot, packageName);
        exportAbilityScripts(project, sourceCodeRoot, packageName);
        exportAbilityCueScripts(project, sourceCodeRoot, packageName);
        exportWidgetScripts(project, sourceCodeRoot, packageName);
        exportParticleModules(project, sourceCodeRoot, packageName);
    }

    /// One registry's provider class: every entry the project created for `registryKey`, folded into
    /// `<PascalCaseModid><classSuffix>.java` under [#PROVIDERS_DIR]. Does nothing for a registry with
    /// no [#FTL_FILES] descriptor, and for one the project never populated.
    public static void exportRegistryProvider(
        AkytheraProject project, ResourceKey<? extends Registry<?>> registryKey, Path sourceCodeRoot, String packageName
    ) {
        SourceTemplate descriptor = FTL_FILES.get(registryKey);
        if (descriptor == null) return;

        List<Map<String, Object>> entries;

        // An entry's DTO can run a graph compile (entity patch flows) — a broken entry skips this
        // registry's provider instead of aborting every remaining registry's export.
        try {
            entries = collectEntries(project, registryKey);
        } catch (Exception e) {
            AkytheraLabs.LOGGER.error("Failed to collect {} entries for source export", registryKey.identifier(), e);
            return;
        }

        if (entries.isEmpty()) return;

        Path providersRoot = sourceCodeRoot.resolve(PROVIDERS_DIR);
        if (!prepareDirectory(providersRoot)) return;

        String className = ParseUtils.pascalCase(project.getProjectName()) + descriptor.classSuffix();

        Map<String, Object> model = new HashMap<>();
        model.put("package", subPackage(packageName, PROVIDERS_DIR));
        model.put("modid", project.getProjectName());
        model.put("className", className);
        model.put(descriptor.collectionVar(), entries);

        render(descriptor.templateFile(), model, providersRoot.resolve(className + ".java"), registryKey.identifier());
    }

    /// One editable [EnumerableDataBlock] type as a `<PascalCaseModid><BlockType>.java` enum: a constant
    /// per project entry, each handing its own identifier path to the type's manager, so the generated
    /// enum carries the ids the data pack export writes. A type with no entries emits nothing.
    ///
    /// The generated enums still need wiring by hand — `registerEnumCls` on the mod's data block
    /// initializer — which the template's header documents.
    public static void exportDataBlockEnum(
        AkytheraProject project, ResourceKey<? extends Registry<?>> registryKey, Path sourceCodeRoot, String packageName
    ) {
        DataBlockRegistries.Entry<?, ?> entry = DataBlockRegistries.entryOf(registryKey);
        if (entry == null) return;

        List<Map<String, Object>> constants = collectDataBlockConstants(project, entry);
        if (constants.isEmpty()) return;

        Path dataBlocksRoot = sourceCodeRoot.resolve(DATA_BLOCKS_DIR);
        if (!prepareDirectory(dataBlocksRoot)) return;

        String className = SourceNames.dataBlockEnumClass(project, entry.blockType());

        Map<String, Object> model = new HashMap<>();
        model.put("package", subPackage(packageName, DATA_BLOCKS_DIR));
        model.put("modid", project.getProjectName());
        model.put("className", className);
        model.put("blockType", entry.blockType().getSimpleName());
        model.put("managerAccess", managerAccess(entry));
        model.put("constants", constants);
        // Shape off a default instance; the per-constant values ride in each row's `args`.
        model.put("properties", entry.holderFactory().get().sourceProperties().stream()
            .map(property -> Map.of(
                "type", property.type(), "field", property.field(), "accessor", property.accessor()))
            .toList());

        render("datablock_enum_template.ftl", model, dataBlocksRoot.resolve(className + ".java"), entry.registryKey().identifier());
    }

    /// Every animator-script entry the project holds, through [#exportAnimatorScript].
    private static void exportAnimatorScripts(AkytheraProject project, Path sourceCodeRoot, String packageName) {
        ProjectObjectRegistry<DataDrivenAnimatorScript.AnimatorScriptBlueprint, DataHolder<DataDrivenAnimatorScript.AnimatorScriptBlueprint>> registry =
            project.getModifiableRegistry(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY);

        if (registry == null) return;

        for (var entry : registry.dataHolderEntrySet()) {
            exportAnimatorScript(project, entry.getKey(), sourceCodeRoot, packageName);
        }
    }

    /// Unlike the [#FTL_FILES] registries (one provider class per registry), each animator-script entry
    /// becomes its own `<PascalCaseEntryName>AnimatorScript.java` — a handwritten-style
    /// [AnimatorScript] implementation. The entry's flat
    /// graphs are first lowered to the runtime blueprint (the holder's `makeObject`, the same compile
    /// the datapack export uses), then [BlueprintSourceWriter] writes its methods and
    /// [ScriptNodeSourceCompiler] the pose getter into `animator_script_template.ftl`. A failure is logged
    /// and skipped so one broken graph can't abort a whole-project export.
    public static void exportAnimatorScript(
        AkytheraProject project,
        ResourceKey<DataDrivenAnimatorScript.AnimatorScriptBlueprint> entryKey,
        Path sourceCodeRoot,
        String packageName
    ) {
        DataHolder<DataDrivenAnimatorScript.AnimatorScriptBlueprint> holder = project.getDataHolderOrThrow(entryKey);
        if (!notBroken(entryKey, holder)) return;

        Path scriptsRoot = sourceCodeRoot.resolve(SCRIPTS_DIR);
        if (!prepareDirectory(scriptsRoot)) return;

        String className = ParseUtils.pascalCase(entryKey.identifier().getPath()) + "AnimatorScript";

        try {
            DataDrivenAnimatorScript.AnimatorScriptBlueprint blueprint = holder.makeObject();

            List<String> memberVariables = blueprint.body().variables().stream()
                .map(ScriptVariables.VariableInfo::name)
                .toList();

            Map<String, Object> model = new HashMap<>();
            model.put("package", subPackage(packageName, SCRIPTS_DIR));
            model.put("modid", project.getProjectName());
            model.put("className", className);
            model.put("fields", animatorFields(blueprint.body().variables()));
            model.put("methods", BlueprintSourceWriter.methods(
                AnimatorScriptHolder.layout(), blueprint.body(), AnimatorScript.class, memberVariables, null,
                ANIMATOR_SELF, ANIMATOR_SELF_TYPE,
                Map.of(DataDrivenAnimatorScript.CONSTRUCT, List.of("this.animator = animator;"))));
            model.put("poseBody", ScriptNodeSourceCompiler.compileReturningFlow(
                blueprint.poseGetter(), memberVariables, List.of("layers"), ANIMATOR_SELF, ANIMATOR_SELF_TYPE, "Pose.EMPTY_POSE"));

            render("animator_script_template.ftl", model, scriptsRoot.resolve(className + ".java"), entryKey.identifier());
        } catch (Exception e) {
            AkytheraLabs.LOGGER.error("Failed to export animator script {} as source", entryKey.identifier(), e);
        }
    }

    /// Every entity-patch entry the project holds, through [#exportEntityPatchScript].
    private static void exportEntityPatchScripts(AkytheraProject project, Path sourceCodeRoot, String packageName) {
        ProjectObjectRegistry<EntityPatchBlueprint, DataHolder<EntityPatchBlueprint>> registry =
            project.getModifiableRegistry(AkytheraRegistries.DataPack.ENTITY_PATCH_KEY);

        if (registry == null) return;

        for (var entry : registry.dataHolderEntrySet()) {
            exportEntityPatchScript(project, entry.getKey(), sourceCodeRoot, packageName);
        }
    }

    /// The entity-patch analogue of [#exportAnimatorScript]: the entry becomes its own
    /// `<PascalCaseEntryName>PatchScript.java`, a handwritten-style [EntityPatchScript]. The entry's graphs
    /// are lowered to the runtime [EntityPatchBlueprint] (the holder's `makeObject`, the same compile the
    /// datapack export uses), and [BlueprintSourceWriter] writes each event and function as a method, with
    /// `self` bound to the generated class's `owner` field.
    ///
    /// The one shape unlike the animator: components aren't graph-generated but a plain list, so each
    /// attached component holder renders its own constructor expression ([#componentSources]) that the
    /// template collects into `componentsToAttach()`.
    ///
    /// An entry parented on a source code script is already source, and is skipped.
    public static void exportEntityPatchScript(
        AkytheraProject project,
        ResourceKey<EntityPatchBlueprint> entryKey,
        Path sourceCodeRoot,
        String packageName
    ) {
        DataHolder<EntityPatchBlueprint> holder = project.getDataHolderOrThrow(entryKey);
        if (!notBroken(entryKey, holder)) return;

        Path patchesRoot = sourceCodeRoot.resolve(PATCHES_DIR);
        if (!prepareDirectory(patchesRoot)) return;

        // Name from the entry's leaf only, not its directory: `minecraft/player` -> `PlayerPatchScript`
        // (the folder is organisational, not part of the type's identity).
        String idPath = entryKey.identifier().getPath();
        String entryName = idPath.substring(idPath.lastIndexOf('/') + 1);
        String className = ParseUtils.pascalCase(entryName) + "PatchScript";

        try {
            if (!(holder.makeObject() instanceof DataDrivenEntityPatchBlueprint blueprint)) {
                AkytheraLabs.LOGGER.info("Skipped {} for script export: its parent is a source code script", entryKey.identifier());
                return;
            }

            List<String> memberVariables = blueprint.body().variables().stream()
                .map(ScriptVariables.VariableInfo::name)
                .toList();

            Map<String, Object> model = new HashMap<>();
            model.put("package", subPackage(packageName, PATCHES_DIR));
            model.put("modid", project.getProjectName());
            model.put("className", className);
            model.put("fields", animatorFields(blueprint.body().variables()));
            model.put("methods", BlueprintSourceWriter.methods(
                ScriptModularizeHelper.scanBlueprintLayout(EntityPatchScript.class), blueprint.body(),
                EntityPatchScript.class, memberVariables, null, ENTITY_PATCH_SELF, ENTITY_PATCH_SELF_TYPE, Map.of()));
            model.put("components", componentSources(holder));

            render("entity_patch_script_template.ftl", model, patchesRoot.resolve(className + ".java"), entryKey.identifier());
        } catch (Exception e) {
            AkytheraLabs.LOGGER.error("Failed to export entity patch script {} as source", entryKey.identifier(), e);
        }
    }

    /// Every ability entry the project holds, through [#exportAbilityScript].
    private static void exportAbilityScripts(AkytheraProject project, Path sourceCodeRoot, String packageName) {
        ProjectObjectRegistry<AbilityBlueprint, DataHolder<AbilityBlueprint>> registry =
            project.getModifiableRegistry(AkytheraRegistries.DataPack.ABILITY_KEY);

        if (registry == null) return;

        for (var entry : registry.dataHolderEntrySet()) {
            exportAbilityScript(project, entry.getKey(), sourceCodeRoot, packageName);
        }
    }

    /// An ability entry as its own `<PascalCaseEntryName>Ability.java`: a subclass of the class its parent
    /// constructor builds, whose events, overridden functions, declared functions and property values are
    /// real methods.
    ///
    /// One [Ability] serves every instance that equips it, so the blueprint's variables go to a nested
    /// [#ABILITY_STATE_CLASS] the ability builds per instance, which binds the parent's state type parameter.
    /// An entry whose parent binds that parameter itself and that declares variables is skipped; the editor
    /// refuses to make one.
    public static void exportAbilityScript(
        AkytheraProject project,
        ResourceKey<AbilityBlueprint> entryKey,
        Path sourceCodeRoot,
        String packageName
    ) {
        DataHolder<AbilityBlueprint> holder = project.getDataHolderOrThrow(entryKey);
        if (!notBroken(entryKey, holder) || !(holder instanceof AbilityBlueprintHolder abilityHolder)) return;

        Path abilitiesRoot = sourceCodeRoot.resolve(ABILITIES_DIR);
        if (!prepareDirectory(abilitiesRoot)) return;

        String idPath = entryKey.identifier().getPath();
        String className = ParseUtils.pascalCase(idPath.substring(idPath.lastIndexOf('/') + 1)) + "Ability";

        try {
            AbilityBlueprint blueprint = holder.makeObject();
            Class<?> base = abilityHolder.ownerType();
            List<ScriptVariables.VariableInfo> variables = blueprint.body().variables();
            BlueprintSourceWriter.MemberHolder state = null;

            if (!variables.isEmpty()) {
                TypeVariable<?> slot = AbilityBlueprintHolder.stateSlot(abilityHolder.getCtor());

                if (slot == null) {
                    AkytheraLabs.LOGGER.error(
                        "Skipped {} for script export: it declares variables, but its parent {} binds its own state",
                        entryKey.identifier(), base.getName()
                    );

                    return;
                }

                // Qualified, since the supertype clause lies outside the class body the nested class is in scope in.
                state = new BlueprintSourceWriter.MemberHolder(
                    slot, className + "." + ABILITY_STATE_CLASS, ABILITY_STATE_PARAMETER);
            }

            List<String> memberVariables = variables.stream().map(ScriptVariables.VariableInfo::name).toList();

            String superCall;

            try {
                base.getConstructor(Holder.Reference.class);
                superCall = "super(blueprint);";
            } catch (NoSuchMethodException e) {
                superCall = "super();";
            }

            Map<String, Object> model = new HashMap<>();
            model.put("package", subPackage(packageName, ABILITIES_DIR));
            model.put("modid", project.getProjectName());
            model.put("className", className);
            model.put("supertype", BlueprintSourceWriter.supertype(base, state));
            model.put("superCall", superCall);
            model.put("fields", animatorFields(variables));
            model.put("methods", BlueprintSourceWriter.methods(
                abilityHolder.layout(), blueprint.body(), base, memberVariables, state, ABILITY_SELF, base, Map.of()));

            if (state != null) {
                model.put("stateClass", ABILITY_STATE_CLASS);
                model.put("clientBound", syncedFields(variables, PacketFlow.CLIENTBOUND));
                model.put("serverBound", syncedFields(variables, PacketFlow.SERVERBOUND));
            }

            render("ability_script_template.ftl", model, abilitiesRoot.resolve(className + ".java"), entryKey.identifier());
        } catch (Exception e) {
            AkytheraLabs.LOGGER.error("Failed to export ability {} as source", entryKey.identifier(), e);
        }
    }

    /// Every ability cue entry the project holds, through [#exportAbilityCueScript].
    private static void exportAbilityCueScripts(AkytheraProject project, Path sourceCodeRoot, String packageName) {
        ProjectObjectRegistry<AbilityCueBlueprint, DataHolder<AbilityCueBlueprint>> registry =
            project.getModifiableRegistry(AkytheraRegistries.DataPack.ABILITY_CUE_KEY);

        if (registry == null) return;

        for (var entry : registry.dataHolderEntrySet()) {
            exportAbilityCueScript(project, entry.getKey(), sourceCodeRoot, packageName);
        }
    }

    /// An ability cue entry as its own `<PascalCaseEntryName>CueScript.java`: an [AbilityCueScript] whose
    /// events, declared functions and property values are real methods, with `self` bound to the generated
    /// class's `cue` field, captured on activation.
    public static void exportAbilityCueScript(
        AkytheraProject project,
        ResourceKey<AbilityCueBlueprint> entryKey,
        Path sourceCodeRoot,
        String packageName
    ) {
        DataHolder<AbilityCueBlueprint> holder = project.getDataHolderOrThrow(entryKey);
        if (!notBroken(entryKey, holder)) return;

        Path cuesRoot = sourceCodeRoot.resolve(ABILITY_CUES_DIR);
        if (!prepareDirectory(cuesRoot)) return;

        String idPath = entryKey.identifier().getPath();
        String className = ParseUtils.pascalCase(idPath.substring(idPath.lastIndexOf('/') + 1)) + "CueScript";

        try {
            AbilityCueBlueprint blueprint = holder.makeObject();

            List<String> memberVariables = blueprint.body().variables().stream()
                .map(ScriptVariables.VariableInfo::name)
                .toList();

            Map<String, Object> model = new HashMap<>();
            model.put("package", subPackage(packageName, ABILITY_CUES_DIR));
            model.put("modid", project.getProjectName());
            model.put("className", className);
            model.put("fields", animatorFields(blueprint.body().variables()));
            model.put("methods", BlueprintSourceWriter.methods(
                ScriptModularizeHelper.scanBlueprintLayout(AbilityCueScript.class), blueprint.body(),
                AbilityCueScript.class, memberVariables, null, ABILITY_CUE_SELF, AbilityCue.class,
                Map.of("onActivate", List.of("this.cue = cue;"))));

            render("ability_cue_script_template.ftl", model, cuesRoot.resolve(className + ".java"), entryKey.identifier());
        } catch (Exception e) {
            AkytheraLabs.LOGGER.error("Failed to export ability cue {} as source", entryKey.identifier(), e);
        }
    }

    /// Every widget blueprint entry the project holds, through [#exportWidgetScript].
    private static void exportWidgetScripts(AkytheraProject project, Path sourceCodeRoot, String packageName) {
        ProjectObjectRegistry<WidgetBlueprint, DataHolder<WidgetBlueprint>> registry =
            project.getModifiableRegistry(AkytheraRegistries.DataPack.WIDGET_KEY);

        if (registry == null) return;

        for (var entry : registry.dataHolderEntrySet()) {
            exportWidgetScript(project, entry.getKey(), sourceCodeRoot, packageName);
        }
    }

    /// A widget blueprint entry as its own `<PascalCaseEntryName>ScreenScript.java` (or `WidgetScript`,
    /// `HUDScript`, by its usage): the script interface of its container, constructed with the container, whose
    /// events, overridden functions, declared functions and property values are methods with `self` bound to the
    /// `owner` field.
    ///
    /// The widgets are rendered by their own `components/widget/*.ftl` into `componentsToAttach()`, and each graph
    /// a widget holds for one of its events or bindings becomes a private method, which `widgetEvent()` and
    /// `widgetBinding()` answer the widget's `<path>.<name>` with.
    ///
    /// An entry parented on a source code script is already source, and is skipped.
    public static void exportWidgetScript(
        AkytheraProject project,
        ResourceKey<WidgetBlueprint> entryKey,
        Path sourceCodeRoot,
        String packageName
    ) {
        DataHolder<WidgetBlueprint> holder = project.getDataHolderOrThrow(entryKey);
        if (!notBroken(entryKey, holder) || !(holder instanceof WidgetBlueprintHolder widgetHolder)) return;

        Path widgetsRoot = sourceCodeRoot.resolve(WIDGETS_DIR);
        if (!prepareDirectory(widgetsRoot)) return;

        String idPath = entryKey.identifier().getPath();
        String className = ParseUtils.pascalCase(idPath.substring(idPath.lastIndexOf('/') + 1)) + switch (widgetHolder.usage()) {
            case SCREEN -> "ScreenScript";
            case WIDGET -> "WidgetScript";
            case HUD -> "HUDScript";
        };

        try {
            if (!(holder.makeObject() instanceof DataDrivenWidgetBlueprint blueprint)) {
                AkytheraLabs.LOGGER.info("Skipped {} for script export: its parent is a source code script", entryKey.identifier());
                return;
            }

            BlueprintLayout layout = widgetHolder.layout();
            Class<?> ownerType = widgetHolder.ownerType();
            List<String> memberVariables = blueprint.body().variables().stream()
                .map(ScriptVariables.VariableInfo::name)
                .toList();
            List<Map<String, Object>> widgetMethods = new ArrayList<>();
            List<Map<String, Object>> events = new ArrayList<>();
            List<Map<String, Object>> bindings = new ArrayList<>();

            for (WidgetBlueprintHolder.WidgetGraphSite site : widgetHolder.widgetGraphSites()) {
                BlueprintSignature signature = site.signature();
                boolean binding = signature.returnType() != null;
                FlowNode flow = (binding ? blueprint.widgetBindings() : blueprint.widgetEvents()).get(site.key());

                if (flow == null) continue;

                String pascal = ParseUtils.pascalCase(site.key());
                String method = Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
                Class<?> returnType = binding ? ParseUtils.unbox(signature.returnType()) : void.class;
                List<String> names = signature.parameters().stream().map(BlueprintSignature.Parameter::name).toList();
                String parameters = signature.parameters().stream()
                    .map(parameter -> "final " + SourceNames.typeName(ParseUtils.unbox(parameter.type())) + " " + parameter.name())
                    .collect(Collectors.joining(", "));

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("annotations", List.of());
                row.put("declaration", "private " + SourceNames.typeName(returnType) + " " + method + "(" + parameters + ")");
                row.put("body", ScriptNodeSourceCompiler.compileMethod(
                    flow, memberVariables, names, WIDGET_SELF, ownerType, null,
                    binding ? BlueprintSourceWriter.defaultLiteral(returnType) : null, null));
                widgetMethods.add(row);

                if (binding) {
                    bindings.add(Map.of("key", site.key(), "method", method));
                } else {
                    List<BlueprintSignature.Parameter> arguments = signature.parameters();

                    events.add(Map.of("key", site.key(), "method", method, "arguments", IntStream.range(0, arguments.size())
                        .mapToObj(index -> "(" + SourceNames.typeName(ParseUtils.box(arguments.get(index).type())) + ") arguments[" + index + "]")
                        .collect(Collectors.joining(", "))));
                }
            }

            Map<String, Object> model = new HashMap<>();
            model.put("package", subPackage(packageName, WIDGETS_DIR));
            model.put("modid", project.getProjectName());
            model.put("entryId", entryKey.identifier().toString());
            model.put("className", className);
            model.put("scriptInterface", SourceNames.typeName(layout.provider()));
            model.put("ownerType", SourceNames.typeName(ownerType));
            model.put("fields", animatorFields(blueprint.body().variables()));
            model.put("methods", BlueprintSourceWriter.methods(
                layout, blueprint.body(), layout.provider(), memberVariables, null, WIDGET_SELF, ownerType, Map.of()));
            model.put("widgetMethods", widgetMethods);
            model.put("components", ComponentDataHolder.sourcesOf(widgetHolder.getComponents(), true));
            model.put("events", events);
            model.put("bindings", bindings);

            render("widget_script_template.ftl", model, widgetsRoot.resolve(className + ".java"), entryKey.identifier());
        } catch (Exception e) {
            AkytheraLabs.LOGGER.error("Failed to export widget blueprint {} as source", entryKey.identifier(), e);
        }
    }

    /// Every particle module node graph the project holds, initializers and updaters, through
    /// [#exportParticleModule].
    public static void exportParticleModules(AkytheraProject project, Path sourceCodeRoot, String packageName) {
        for (ResourceKey<? extends Registry<Object>> registryKey : List.of(ParticleModulesProducer.moduleRegistry(false), ParticleModulesProducer.moduleRegistry(true))) {
            for (var entry : project.<Object, DataHolder<Object>>getModifiableEntries(registryKey)) {
                exportParticleModule(project, entry.getKey(), sourceCodeRoot, packageName);
            }
        }
    }

    /// The class [#exportParticleModule] writes the module node graph `entryKey` as: `<PascalCaseEntryName>` then
    /// `ParticleUpdater` for an entry of the updater registry, `ParticleInitializer` otherwise.
    public static String particleModuleClass(ResourceKey<?> entryKey) {
        String idPath = entryKey.identifier().getPath();
        boolean updater = entryKey.registryKey().equals(AkytheraRegistries.DataPack.PARTICLE_UPDATER_KEY);

        return ParseUtils.pascalCase(idPath.substring(idPath.lastIndexOf('/') + 1)) + (updater ? "ParticleUpdater" : "ParticleInitializer");
    }

    /// A particle module node graph as its own [#particleModuleClass]: a `ParticleInitializerModule` or a
    /// `ParticleUpdateModule` whose event and declared functions are methods, with `self` bound to the `emitter`
    /// field the event captures from its particle.
    public static void exportParticleModule(AkytheraProject project, ResourceKey<?> entryKey, Path sourceCodeRoot, String packageName) {
        @SuppressWarnings("unchecked")
        DataHolder<Object> holder = project.getDataHolderOrThrow((ResourceKey<Object>) entryKey);
        if (!notBroken(entryKey, holder) || !(holder instanceof ParticleModuleScriptHolder<?> moduleHolder)) return;

        Path particlesRoot = sourceCodeRoot.resolve(PARTICLES_DIR);
        if (!prepareDirectory(particlesRoot)) return;

        String className = particleModuleClass(entryKey);

        try {
            BlueprintBody body = moduleHolder.compileBody();
            BlueprintLayout layout = moduleHolder.layout();
            List<String> memberVariables = body.variables().stream().map(ScriptVariables.VariableInfo::name).toList();

            Map<String, Object> model = new HashMap<>();
            model.put("package", subPackage(packageName, PARTICLES_DIR));
            model.put("modid", project.getProjectName());
            model.put("entryId", entryKey.identifier().toString());
            model.put("className", className);
            model.put("moduleInterface", SourceNames.typeName(layout.provider()));
            model.put("fields", animatorFields(body.variables()));
            model.put("methods", BlueprintSourceWriter.methods(
                layout, body, layout.provider(), memberVariables, null, PARTICLE_MODULE_SELF, AkytheraParticleEmitter.class,
                layout.events().stream().collect(Collectors.toMap(BlueprintLayout.Signature::name, _ -> PARTICLE_MODULE_PROLOGUE))));

            render("particle_module_template.ftl", model, particlesRoot.resolve(className + ".java"), entryKey.identifier());
        } catch (Exception e) {
            AkytheraLabs.LOGGER.error("Failed to export particle module {} as source", entryKey.identifier(), e);
        }
    }

    /// Renders `templateFile` against `model` into `outputFile`, reporting the write under `id`.
    private static void render(String templateFile, Map<String, Object> model, Path outputFile, Identifier id) {
        try {
            Template template = SourceTemplateEngine.getTemplate(templateFile);

            try (Writer writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
                template.process(model, writer);
            }

            AkytheraLabs.LOGGER.info("Exported {} -> {}", id, outputFile);
        } catch (IOException | TemplateException e) {
            AkytheraLabs.LOGGER.error("Failed to export {} from template {}", id, templateFile, e);
        }
    }

    /// Creates `directory`, reporting whether it is ready to be written into.
    private static boolean prepareDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            return true;
        } catch (IOException e) {
            AkytheraLabs.LOGGER.error("Failed to create export directory {}", directory, e);
            return false;
        }
    }

    /// One `{ name, path, args }` row per project entry of `entry`'s type, from
    /// [SourceNames#dataBlockConstants]: `name` is the enum constant, `path` the identifier path that
    /// constant registers itself under, and `args` its [DataBlockHolder.SourceProperty] values in
    /// constructor-parameter order.
    private static <T extends EnumerableDataBlock> List<Map<String, Object>> collectDataBlockConstants(
        AkytheraProject project, DataBlockRegistries.Entry<T, ?> entry
    ) {
        ProjectObjectRegistry<T, DataBlockHolder<T>> registry = project.getModifiableRegistry(entry.registryKey());
        Map<String, List<DataBlockHolder.SourceProperty>> byPath = new LinkedHashMap<>();

        if (registry != null) {
            registry.dataHolderEntrySet().forEach(holder ->
                byPath.put(holder.getKey().identifier().getPath(), holder.getValue().sourceProperties()));
        }

        return SourceNames.dataBlockConstants(project, entry).entrySet().stream()
            .<Map<String, Object>>map(constant -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", constant.getValue());
                row.put("path", constant.getKey());
                row.put("args", byPath.getOrDefault(constant.getKey(), List.of()).stream()
                    .map(DataBlockHolder.SourceProperty::value).toList());

                return row;
            })
            .toList();
    }

    /// `"FSMAnimationState.ENUM_MANAGER"` — the static field holding `entry`'s manager, as an
    /// expression the generated enum's constructor calls. Found by identity across the block type and
    /// its super-interfaces, so a manager declared on a super-interface resolves to the type that
    /// actually declares it.
    private static String managerAccess(DataBlockRegistries.Entry<?, ?> entry) {
        for (Class<?> cls : DataBlockRegistries.managerDeclarers(entry.blockType())) {
            for (Field field : cls.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || field.getType() != EnumerableDataBlockManager.class) {
                    continue;
                }

                try {
                    if (field.get(null) == entry.manager()) {
                        return cls.getSimpleName() + "." + field.getName();
                    }
                } catch (IllegalAccessException e) {
                    AkytheraLabs.LOGGER.warn("Unreadable manager field {}#{}", cls.getName(), field.getName(), e);
                }
            }
        }

        throw new IllegalStateException("No manager field declared for data block type " + entry.blockType().getName());
    }

    /// Whether `holder` is healthy enough to generate source for, by [DataHolder#getFailureException]
    /// — the same test [AkytheraProject#exportDataPack] applies.
    private static boolean notBroken(ResourceKey<?> key, DataHolder<?> holder) {
        Exception failure = holder.getFailureException();
        if (failure == null) return true;

        AkytheraLabs.LOGGER.warn("Skipping broken {} for source export: {}", key.identifier(), failure.getMessage());
        return false;
    }

    /// The attached components as `{ name, source }` rows, in the holder's stored order — see
    /// [ComponentDataHolder#sourcesOf].
    private static List<Map<String, String>> componentSources(DataHolder<EntityPatchBlueprint> holder) {
        if (!(holder instanceof EntityPatchBlueprintHolder patchHolder)) {
            return List.of();
        }

        return ComponentDataHolder.sourcesOf(patchHolder.getComponents(), true);
    }

    /// Template rows for the generated `@ScriptField` members: Java type simple name, field name,
    /// and (when the variable carries one) its default value as a Java literal.
    /// The variables that travel `flow`, each as its `name` and the `scriptType` expression it crosses the
    /// wire through.
    private static List<Map<String, Object>> syncedFields(List<ScriptVariables.VariableInfo> variables, PacketFlow flow) {
        return variables.stream()
            .filter(variable -> variable.travels(flow))
            .<Map<String, Object>>map(variable -> Map.of(
                "name", variable.name(),
                "scriptType", NodeTreeSourceRenderer.scriptType(variable.type())
            ))
            .toList();
    }

    private static List<Map<String, Object>> animatorFields(List<ScriptVariables.VariableInfo> variables) {
        return variables.stream()
            .<Map<String, Object>>map(variable -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("type", SourceNames.typeName(variable.type()));
                row.put("name", variable.name());

                String defaultLiteral = ScriptNodeSourceCompiler.defaultValueLiteral(variable);
                if (defaultLiteral != null) row.put("defaultValue", defaultLiteral);

                return row;
            })
            .toList();
    }

    /// Build the template model rows for one registry: every entry the project created for that registry,
    /// id-path sorted for stable output. Each row exposes `asId` (the createId argument), `asVariableName`
    /// (the `public static final ResourceKey` field name), and `dto` — the entry holder's own
    /// [DTOProvider#getDTO], the custom token map the template splices into the generated source.
    private static List<Map<String, Object>> collectEntries(
        AkytheraProject project, ResourceKey<? extends Registry<?>> registryKey
    ) {
        @SuppressWarnings("unchecked")
        ResourceKey<? extends Registry<Object>> typed = (ResourceKey<? extends Registry<Object>>) registryKey;
        ProjectObjectRegistry<Object, ?> registry = project.getModifiableRegistry(typed);

        if (registry == null) {
            return List.of();
        }

        return registry.dataHolderEntrySet().stream()
            .filter(entry -> notBroken(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(entry -> entry.getKey().identifier().getPath()))
            .map(entry -> {
                String path = entry.getKey().identifier().getPath();
                DataHolder<?> holder = entry.getValue();

                if (!(holder instanceof DTOProvider provider)) {
                    throw new IllegalStateException(
                        holder.getClass().getName() + " must implement DTOProvider to be exportable"
                    );
                }

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("asId", path);
                row.put("asVariableName", SourceNames.screamingSnake(path));
                row.put("dto", provider.getDTO());

                return row;
            })
            .toList();
    }

    /// Append a sub-package segment to the chosen base package, tolerating a blank base (the modal
    /// leaves the package field empty when the export dir isn't under `src/main/java`).
    private static String subPackage(String base, String segment) {
        return base.isBlank() ? segment : base + "." + segment.replaceAll("\\\\", ".");
    }
}
