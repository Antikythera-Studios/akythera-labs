package com.yesman.akytheralabs.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.ProjectLocalization;
import com.yesman.akytheralabs.ProjectTags;
import com.yesman.akytheralabs.dataholder.EntityPreviewModel;
import com.yesman.akytheralabs.dataholder.EntityPreviewModelMapping;
import com.yesman.akytheralabs.gui.widget.main.AccessorSelectionModal;
import com.yesman.akytheralabs.gui.widget.main.ExportModalGenerator;
import com.yesman.akytheralabs.gui.widget.main.PreviewMeshSelectModal;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// The [McpTool]s doing what the project modals of the top menu do: Entity Preview Model Mapping, Localization,
/// Project Tags, Project Settings, and Export.
final class McpProjectTools {
    /// A mesh binding as the preview model tools read and write it: `{id, type}`.
    private static final Codec<EntityPreviewModel> MESH_JSON = RecordCodecBuilder.create(i -> i.group(
        Identifier.CODEC.fieldOf("id").forGetter(EntityPreviewModel::modelId),
        EntityModelComponent.ModelType.CODEC.fieldOf("type").forGetter(EntityPreviewModel::modelType)
    ).apply(i, EntityPreviewModel::new));

    private McpProjectTools() {}

    static void registerAll() {
        // ***************************************************************
        // Entity Preview Model Mapping
        // ***************************************************************

        McpTools.register(McpTool.of(
            "get_entity_preview_models",
            "Returns what each entity type is previewed as in the editor, as the Entity Preview Model Mapping modal shows "
                + "it: a 'mesh' ({id, type: 'skeletal_mesh' or 'vanilla_entity'}) and an 'armature' id, null when unbound. "
                + "'resolves' is false when the mesh or armature no longer exists. Without 'entityTypes', lists the entity "
                + "types holding a binding. 'candidates' adds every entity type, skeletal mesh and armature a binding can name.",
            """
            {
              "type": "object",
              "properties": {
                "entityTypes": {"type": "array", "items": {"type": "string"}, "description": "Entity type ids, e.g. 'minecraft:zombie'."},
                "candidates": {"type": "boolean", "description": "Default: false."}
              }
            }
            """,
            arguments -> McpTools.onClient(() -> {
                AkytheraProject project = McpTools.requireProject();
                List<EntityType<?>> types = new ArrayList<>();

                if (arguments.has("entityTypes")) {
                    for (JsonElement id : arguments.getAsJsonArray("entityTypes")) types.add(previewableType(id.getAsString()));
                } else {
                    types.addAll(EntityPreviewModelMapping.boundTypes());
                }

                JsonObject result = new JsonObject();
                result.addProperty("vanillaModelsAnimatable", EntityPreviewModelMapping.vanillaModelsAnimatable());

                JsonArray bindings = new JsonArray();

                for (EntityType<?> type : types) {
                    EntityPreviewModel mesh = EntityPreviewModelMapping.getMesh(type);
                    AssetAccessor<? extends Armature> armature = EntityPreviewModelMapping.getArmature(type);
                    JsonObject binding = new JsonObject();
                    binding.addProperty("entityType", EntityPreviewModelMapping.label(type));
                    binding.add("mesh", mesh == null ? null : MESH_JSON.encodeStart(JsonOps.INSTANCE, mesh).getOrThrow());
                    binding.addProperty("armature", armature == null ? null : armature.identifier().toString());
                    binding.addProperty("resolves", PreviewMeshSelectModal.resolves(mesh) && (armature == null || AccessorSelectionModal.resolves(armature)));
                    bindings.add(binding);
                }

                result.add("bindings", bindings);

                if (arguments.has("candidates") && arguments.get("candidates").getAsBoolean()) {
                    JsonObject candidates = new JsonObject();
                    candidates.add("entityTypes", McpTools.PRETTY_GSON.toJsonTree(EntityPreviewModelMapping.previewableTypes().stream().map(EntityPreviewModelMapping::label).toList()));
                    candidates.add("skeletalMeshes", McpTools.PRETTY_GSON.toJsonTree(McpTools.skeletalMeshIds(project)));
                    candidates.add("armatures", McpTools.PRETTY_GSON.toJsonTree(McpTools.armatureIds(project)));
                    result.add("candidates", candidates);
                }

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(result));
            })
        ));

        McpTools.register(McpTool.of(
            "set_entity_preview_models",
            "Binds what entity types are previewed as in the editor, as the Entity Preview Model Mapping modal does, and "
                + "shows the change on every open editor screen's preview. In each binding, a present 'mesh' or 'armature' "
                + "replaces that half, null removes it, and an absent one keeps it. 'fillVanilla' first binds every entity "
                + "type to its own vanilla model, as the modal's button does. Nothing is written when one binding is "
                + "invalid. The bindings stay unsaved unless 'save' is true; reloading the project drops unsaved bindings.",
            """
            {
              "type": "object",
              "properties": {
                "bindings": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "entityType": {"type": "string"},
                      "mesh": {"type": ["object", "null"], "properties": {"id": {"type": "string"}, "type": {"type": "string", "enum": ["skeletal_mesh", "vanilla_entity"]}}},
                      "armature": {"type": ["string", "null"]}
                    },
                    "required": ["entityType"]
                  }
                },
                "fillVanilla": {"type": "boolean", "description": "Default: false. Needs Player Animation Library; see get_entity_preview_models."},
                "save": {"type": "boolean", "description": "Writes the project settings, as File > Save does. Default: false."}
              }
            }
            """,
            arguments -> McpTools.onClient(() -> {
                AkytheraProject project = McpTools.requireProject();
                boolean fillVanilla = arguments.has("fillVanilla") && arguments.get("fillVanilla").getAsBoolean();
                List<Runnable> writes = new ArrayList<>();

                if (fillVanilla && !EntityPreviewModelMapping.vanillaModelsAnimatable()) {
                    return McpToolResult.error("Binding vanilla models needs Player Animation Library");
                }

                for (JsonElement element : arguments.has("bindings") ? arguments.getAsJsonArray("bindings") : new JsonArray()) {
                    JsonObject binding = element.getAsJsonObject();
                    EntityType<?> type = previewableType(binding.get("entityType").getAsString());

                    if (binding.has("mesh")) {
                        EntityPreviewModel mesh = binding.get("mesh").isJsonNull() ? null : MESH_JSON.parse(JsonOps.INSTANCE, binding.get("mesh")).getOrThrow(IllegalArgumentException::new);

                        if (!PreviewMeshSelectModal.resolves(mesh)) throw new IllegalArgumentException("The project has no " + mesh.modelType().getSerializedName() + " " + mesh.modelId());

                        writes.add(() -> EntityPreviewModelMapping.setMesh(type, mesh));
                    }

                    if (binding.has("armature")) {
                        AssetAccessor<? extends Armature> armature = binding.get("armature").isJsonNull() ? null : McpTools.requireArmature(project, binding.get("armature").getAsString());
                        writes.add(() -> EntityPreviewModelMapping.setArmature(type, armature));
                    }
                }

                if (fillVanilla) EntityPreviewModelMapping.fillVanillaBindings();

                writes.forEach(Runnable::run);
                EntityPreviewModelMapping.refreshOpenPreviews();

                String reply = "Applied " + writes.size() + " binding changes" + (fillVanilla ? " after binding the vanilla models" : "");

                return save(arguments, project, reply);
            })
        ));

        // ***************************************************************
        // Localization
        // ***************************************************************

        McpTools.register(McpTool.of(
            "get_localization",
            "Returns the project's lang entries, as the Localization modal shows them: 'language', the main language that "
                + "an empty text falls back to, 'exportAllLanguages', and 'keys', each key with its text in the asked "
                + "language ('' when that language leaves it empty).",
            """
            {
              "type": "object",
              "properties": {
                "language": {"type": "string", "description": "A language code, e.g. 'ko_kr'. Default: the main language."},
                "under": {"type": "string", "description": "Only this key and the keys beneath it, e.g. 'gui.ability_screen'."}
              }
            }
            """,
            arguments -> McpTools.onClient(() -> {
                ProjectLocalization localization = McpTools.requireProject().getLocalization();
                String language = arguments.has("language") ? arguments.get("language").getAsString() : localization.getLanguage();
                String under = arguments.has("under") ? arguments.get("under").getAsString() : null;
                JsonObject keys = new JsonObject();

                for (String key : localization.keys()) {
                    if (under == null || ProjectLocalization.isUnder(key, under)) keys.addProperty(key, localization.text(key, language));
                }

                JsonObject result = new JsonObject();
                result.addProperty("language", localization.getLanguage());
                result.addProperty("exportAllLanguages", localization.isExportAllLanguages());
                result.add("keys", keys);

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(result));
            })
        ));

        McpTools.register(McpTool.of(
            "edit_localization",
            "Edits the project's lang entries as the Localization modal does, applying the ops in order, and saves them. "
                + "Ops: {op:'set_text', key, text, language?} (adds a new key; '' clears that language; language defaults to "
                + "the main one), {op:'add', key}, {op:'remove', key} (and every key beneath it), {op:'move', from, to} (and "
                + "every key beneath it), {op:'set_main_language', language}, {op:'set_export_all', value}, {op:'import', "
                + "file} (a lang .json; one named after a language, e.g. 'ko_kr.json', fills that language and makes it the "
                + "main one). A failing op stops the call; the ops before it stay applied and saved.",
            """
            {
              "type": "object",
              "properties": {
                "ops": {"type": "array", "items": {"type": "object"}}
              },
              "required": ["ops"]
            }
            """,
            arguments -> McpTools.onClient(() -> {
                AkytheraProject project = McpTools.requireProject();
                Path root = project.getProjectRoot().toAbsolutePath().normalize();
                ProjectLocalization localization = project.getLocalization();
                Set<String> languages = Minecraft.getInstance().getLanguageManager().getLanguages().keySet();
                JsonArray ops = arguments.getAsJsonArray("ops");
                int applied = 0;
                String failure = null;

                for (JsonElement element : ops) {
                    JsonObject op = element.getAsJsonObject();

                    try {
                        switch (op.get("op").getAsString()) {
                            case "set_text" -> localization.setText(validKey(op.get("key").getAsString()),
                                op.has("language") ? knownLanguage(languages, op.get("language").getAsString()) : localization.getLanguage(),
                                op.get("text").getAsString());
                            case "add" -> localization.add(validKey(op.get("key").getAsString()));
                            case "remove" -> localization.removeTree(op.get("key").getAsString());
                            case "move" -> {
                                if (!localization.moveTree(op.get("from").getAsString(), validKey(op.get("to").getAsString()))) {
                                    throw new IllegalArgumentException("A moved key would land on a key that stays");
                                }
                            }
                            case "set_main_language" -> localization.setLanguage(knownLanguage(languages, op.get("language").getAsString()));
                            case "set_export_all" -> localization.setExportAllLanguages(op.get("value").getAsBoolean());
                            case "import" -> localization.importLangFile(McpTools.requireFile(root, op.get("file").getAsString()), languages);
                            default -> throw new IllegalArgumentException("Unknown op: " + op.get("op").getAsString());
                        }
                    } catch (RuntimeException e) {
                        failure = "Op " + applied + " failed: " + e.getMessage();
                        break;
                    }

                    applied++;
                }

                project.saveLocalization();

                return failure == null
                    ? McpToolResult.text("Applied " + applied + " ops and saved the localization")
                    : McpToolResult.error("Applied and saved " + applied + " ops. " + failure);
            })
        ));

        // ***************************************************************
        // Project Tags
        // ***************************************************************

        McpTools.register(McpTool.of(
            "get_project_tags",
            "Returns the project's tags, as the Project Tags modal shows them: for each registry, each tag path with its "
                + "vanilla tag JSON ({values, replace}). Every tag is in the project's namespace.",
            """
            {
              "type": "object",
              "properties": {
                "registry": {"type": "string", "description": "A registry id, e.g. 'minecraft:entity_type'. Default: every registry."}
              }
            }
            """,
            arguments -> McpTools.onClient(() -> {
                ProjectTags tags = McpTools.requireProject().getTags();
                JsonObject result = new JsonObject();

                for (ResourceKey<? extends Registry<?>> registry : ProjectTags.REGISTRIES) {
                    if (arguments.has("registry") && registry != tagRegistry(arguments.get("registry").getAsString())) continue;

                    JsonObject registryTags = new JsonObject();
                    Codec<ProjectTags.TagFile> codec = ProjectTags.TagFile.codec(registry);
                    tags.tagsOf(registry).forEach((path, tag) -> registryTags.add(path, codec.encodeStart(JsonOps.INSTANCE, tag).getOrThrow()));
                    result.add(registry.identifier().toString(), registryTags);
                }

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(result));
            })
        ));

        McpTools.register(McpTool.of(
            "edit_project_tags",
            "Edits the project's tags as the Project Tags modal does, applying the ops in order, and saves them. Ops: "
                + "{op:'put', registry, path, tag:<vanilla tag JSON {values, replace}>} (adds or replaces), {op:'remove', "
                + "registry, path}, {op:'rename', registry, from, to}. A failing op stops the call; the ops before it stay "
                + "applied and saved.",
            """
            {
              "type": "object",
              "properties": {
                "ops": {"type": "array", "items": {"type": "object"}}
              },
              "required": ["ops"]
            }
            """,
            arguments -> McpTools.onClient(() -> {
                AkytheraProject project = McpTools.requireProject();
                ProjectTags tags = project.getTags();
                int applied = 0;
                String failure = null;

                for (JsonElement element : arguments.getAsJsonArray("ops")) {
                    JsonObject op = element.getAsJsonObject();

                    try {
                        ResourceKey<? extends Registry<?>> registry = tagRegistry(op.get("registry").getAsString());

                        switch (op.get("op").getAsString()) {
                            case "put" -> {
                                String path = op.get("path").getAsString();

                                if (!Identifier.isValidPath(path)) throw new IllegalArgumentException("Not a valid tag path: " + path);

                                tags.put(registry, path, ProjectTags.TagFile.codec(registry).parse(JsonOps.INSTANCE, op.get("tag")).getOrThrow(IllegalArgumentException::new));
                            }
                            case "remove" -> tags.remove(registry, op.get("path").getAsString());
                            case "rename" -> {
                                String to = op.get("to").getAsString();

                                if (!Identifier.isValidPath(to) || !tags.rename(registry, op.get("from").getAsString(), to)) {
                                    throw new IllegalArgumentException("Cannot rename to " + to + ": invalid, taken, or the tag is missing");
                                }
                            }
                            default -> throw new IllegalArgumentException("Unknown op: " + op.get("op").getAsString());
                        }
                    } catch (RuntimeException e) {
                        failure = "Op " + applied + " failed: " + e.getMessage();
                        break;
                    }

                    applied++;
                }

                project.saveTags();

                return failure == null
                    ? McpToolResult.text("Applied " + applied + " ops and saved the tags")
                    : McpToolResult.error("Applied and saved " + applied + " ops. " + failure);
            })
        ));

        // ***************************************************************
        // Project Settings
        // ***************************************************************

        McpTools.register(McpTool.of(
            "get_project_settings",
            "Returns the settings the Project Settings modal edits: the project 'name', its 'version' and whether it has "
                + "an 'icon'.",
            """
            {"type": "object", "properties": {}}
            """,
            _ -> McpTools.onClient(() -> {
                AkytheraProject project = McpTools.requireProject();
                JsonObject result = new JsonObject();
                result.addProperty("name", project.getProjectName());
                result.addProperty("version", project.getVersion().toString());
                result.addProperty("icon", Files.isRegularFile(project.getProjectRoot().resolve(AkytheraProject.PROJECT_ICON_FILE)));

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(result));
            })
        ));

        McpTools.register(McpTool.of(
            "set_project_settings",
            "Changes the project settings as the Project Settings modal does. A new 'name' renames the project folder; the "
                + "paths of every tool stay relative to it. 'icon' copies a .png in as the project icon. The name and icon "
                + "are written at once; the version stays unsaved unless 'save' is true.",
            """
            {
              "type": "object",
              "properties": {
                "name": {"type": "string", "description": "Lowercase letters, digits and '_'."},
                "version": {"type": "string", "description": "Semantic versioning, e.g. '1.2.0+alpha.1'."},
                "icon": {"type": "string", "description": "A .png file, absolute or relative to the project root."},
                "save": {"type": "boolean", "description": "Writes the project settings, as File > Save does. Default: false."}
              }
            }
            """,
            arguments -> McpTools.onClient(() -> {
                AkytheraProject project = McpTools.requireProject();
                AkytheraProject.SemanticVersioning version = arguments.has("version") ? AkytheraProject.SemanticVersioning.parse(arguments.get("version").getAsString()) : null;
                Path icon = arguments.has("icon") ? McpTools.requireFile(project.getProjectRoot().toAbsolutePath().normalize(), arguments.get("icon").getAsString()) : null;

                if (icon != null && !icon.getFileName().toString().endsWith(".png")) throw new IllegalArgumentException("The icon is no .png: " + icon);

                if (arguments.has("name")) {
                    String name = arguments.get("name").getAsString();

                    if (!name.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Not a valid project name: " + name);

                    if (!name.equals(project.getProjectName())) {
                        if (Files.exists(AkytheraProject.getProjectsRootDirectory().resolve(name))) throw new IllegalArgumentException("A project named " + name + " exists");
                        if (!project.renameProject(name)) return McpToolResult.error("Failed to rename the project, see read_console");
                    }
                }

                if (version != null) project.setVersion(version);
                if (icon != null) FileSystemHelper.copyReplace(icon, project.getProjectRoot().resolve(AkytheraProject.PROJECT_ICON_FILE));

                return save(arguments, project, "Applied the settings");
            })
        ));

        // ***************************************************************
        // Export
        // ***************************************************************

        McpTools.register(McpTool.of(
            "export_project",
            "Exports the project, as File > Export and the Localization modal's Export do. 'pack': a data pack zip into "
                + "'dataPath' (holding the assets too with 'includeAssets') and a resource pack zip into 'resourcePath'. "
                + "'source_code': generated .java into 'dataPath' under 'packageName' (default: derived from the path after "
                + "'src/main/java/'), and the assets as loose files into 'resourcePath'. 'lang_files': the lang files into "
                + "the resource root 'resourcePath'. Paths are absolute or relative to the project root; one left out takes "
                + "the folder the last export of that kind used, and '' skips that half. 'pack' and 'source_code' leave "
                + "broken entries out and refuse while there are any, unless 'force'.",
            """
            {
              "type": "object",
              "properties": {
                "kind": {"type": "string", "enum": ["pack", "source_code", "lang_files"]},
                "dataPath": {"type": "string"},
                "resourcePath": {"type": "string"},
                "includeAssets": {"type": "boolean", "description": "'pack' only. Default: false."},
                "packageName": {"type": "string", "description": "'source_code' only."},
                "force": {"type": "boolean", "description": "Default: false."}
              },
              "required": ["kind"]
            }
            """,
            arguments -> McpTools.onClient(() -> {
                AkytheraProject project = McpTools.requireProject();
                Path root = project.getProjectRoot().toAbsolutePath().normalize();
                String kind = arguments.get("kind").getAsString();
                boolean force = arguments.has("force") && arguments.get("force").getAsBoolean();

                if (!kind.equals("lang_files")) {
                    project.compileAll();
                    List<ResourceKey<?>> broken = project.findBrokenEntries();

                    if (!broken.isEmpty() && !force) {
                        return McpToolResult.error("These entries are broken and would be left out; fix them or pass force=true:\n"
                            + broken.stream().map(project::getRelativeEntryPath).sorted().collect(Collectors.joining("\n")));
                    }
                }

                String fallback = root.toString();

                switch (kind) {
                    case "pack" -> {
                        String dataDir = exportPath(arguments, "dataPath", root, project.getLastExportDataPath().isBlank() ? fallback : project.getLastExportDataPath());
                        String resourceDir = exportPath(arguments, "resourcePath", root, project.getLastExportResourcePath().isBlank() ? fallback : project.getLastExportResourcePath());
                        boolean includeAssets = arguments.has("includeAssets") && arguments.get("includeAssets").getAsBoolean();

                        if (dataDir.isBlank() && resourceDir.isBlank()) return McpToolResult.error("Both paths are blank");

                        ExportModalGenerator.exportAsPack(project, dataDir, resourceDir, includeAssets);
                        return McpToolResult.text("Exported the pack to " + String.join(" and ", List.of(dataDir, resourceDir).stream().filter(dir -> !dir.isBlank()).toList()));
                    }
                    case "source_code" -> {
                        String dataDir = exportPath(arguments, "dataPath", root, project.getLastSourceDataPath());
                        String resourceDir = exportPath(arguments, "resourcePath", root, project.getLastSourceResourcePath());
                        String packageName = arguments.has("packageName") ? arguments.get("packageName").getAsString() : ExportModalGenerator.derivePackageName(dataDir);

                        if (dataDir.isBlank() && resourceDir.isBlank()) return McpToolResult.error("Both paths are blank");

                        ExportModalGenerator.exportAsSourceCode(project, dataDir, resourceDir, packageName);
                        return McpToolResult.text("Exported the source code to " + String.join(" and ", List.of(dataDir, resourceDir).stream().filter(dir -> !dir.isBlank()).toList()));
                    }
                    case "lang_files" -> {
                        String resourceDir = exportPath(arguments, "resourcePath", root, project.getLastSourceResourcePath().isBlank() ? fallback : project.getLastSourceResourcePath());

                        if (resourceDir.isBlank()) return McpToolResult.error("The resource path is blank");

                        project.saveSourceExportPaths(project.getLastSourceDataPath(), resourceDir);
                        project.exportLangFiles(Path.of(resourceDir));
                        return McpToolResult.text("Exported the lang files to " + resourceDir);
                    }
                    default -> {
                        return McpToolResult.error("Unknown kind: " + kind);
                    }
                }
            })
        ));
    }

    // ***************************************************************
    // Helpers
    // ***************************************************************

    /// The entity type @param `id` names, among those the previewer can draw.
    private static EntityType<?> previewableType(final String id) {
        EntityType<?> type = EntityPreviewModelMapping.typeOf(McpTools.requireId(id));

        if (type == null || !EntityPreviewModelMapping.previewableTypes().contains(type)) throw new IllegalArgumentException("Not a previewable entity type: " + id);

        return type;
    }

    /// @param `reply`, after writing the project settings when the call asked to `save`.
    private static McpToolResult save(final JsonObject arguments, final AkytheraProject project, final String reply) {
        if (!arguments.has("save") || !arguments.get("save").getAsBoolean()) return McpToolResult.text(reply + ". Unsaved until File > Save");
        if (!McpTools.saveProjectSettings(project)) return McpToolResult.error(reply + ", but the editor layout is not open to save them");

        return McpToolResult.text(reply + " and saved the project settings");
    }

    private static String validKey(final String key) {
        if (!ProjectLocalization.isValidKey(key)) throw new IllegalArgumentException("Not a valid lang key: " + key);

        return key;
    }

    private static String knownLanguage(final Set<String> languages, final String code) {
        if (!languages.contains(code)) throw new IllegalArgumentException("Not a language the game offers: " + code);

        return code;
    }

    private static ResourceKey<? extends Registry<?>> tagRegistry(final String id) {
        return ProjectTags.REGISTRIES.stream()
            .filter(registry -> registry.identifier().toString().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No project tags for the registry " + id));
    }

    /// The argument @param `key` as an absolute path, or @param `fallback` when the call left it out. Blank stays blank.
    private static String exportPath(final JsonObject arguments, final String key, final Path root, final String fallback) {
        if (!arguments.has(key)) return fallback;

        String path = arguments.get(key).getAsString();

        return path.isBlank() ? "" : McpTools.resolveAgainstProject(root, path).toString();
    }
}
