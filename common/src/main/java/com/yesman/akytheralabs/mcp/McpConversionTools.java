package com.yesman.akytheralabs.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.gui.widget.main.AssetExportManager;
import com.yesman.akytheralabs.gui.widget.main.RetargetEntry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// The [McpTool]s doing what the Animation Retarget and Model Transformer tools of the top menu do, without their
/// modals.
final class McpConversionTools {
    private McpConversionTools() {}

    static void registerAll() {
        // ***************************************************************
        // Animation Retarget
        // ***************************************************************

        McpTools.register(McpTool.of(
            "inspect_retarget",
            "Reads what the Animation Retarget tool needs to map bones: the bones of a source armature file, the bones "
                + "each animation file animates, and the project armatures to retarget onto. With 'targetArmature', also "
                + "that armature's bones and the saved templates made for it. File paths are absolute or relative to the "
                + "project root.",
            """
            {
              "type": "object",
              "properties": {
                "sourceArmature": {"type": "string", "description": "The armature file (.json or .akasset) the animations were made on."},
                "animations": {"type": "array", "items": {"type": "string"}, "description": "Animation files made on the source armature."},
                "targetArmature": {"type": "string", "description": "A project armature id, as 'targetArmatures' lists them."}
              },
              "required": ["sourceArmature"]
            }
            """,
            arguments -> McpTools.onClient(() -> {
                AkytheraProject project = McpTools.requireProject();
                Path root = project.getProjectRoot().toAbsolutePath().normalize();
                AssetAccessor<Armature> source = AssetExportManager.INSTANCE.loadExternalArmature(McpTools.requireFile(root, arguments.get("sourceArmature").getAsString()));
                JsonObject result = new JsonObject();

                JsonObject sourceJson = new JsonObject();
                sourceJson.addProperty("rootBone", source.get().getRootBone().getName());
                sourceJson.add("bones", McpTools.PRETTY_GSON.toJsonTree(boneNames(source.get())));
                result.add("sourceArmature", sourceJson);

                JsonArray animations = new JsonArray();

                if (arguments.has("animations")) {
                    for (JsonElement path : arguments.getAsJsonArray("animations")) {
                        RetargetEntry entry = AssetExportManager.INSTANCE.loadRetargetEntry(McpTools.requireFile(root, path.getAsString()), source, null);
                        JsonObject animation = new JsonObject();
                        animation.addProperty("file", path.getAsString());
                        animation.add("bones", McpTools.PRETTY_GSON.toJsonTree(entry.boneNames));
                        animations.add(animation);
                    }
                }

                result.add("animations", animations);

                if (arguments.has("targetArmature")) {
                    AssetAccessor<? extends Armature> target = McpTools.requireArmature(project, arguments.get("targetArmature").getAsString());
                    JsonObject targetJson = new JsonObject();
                    targetJson.addProperty("id", target.identifier().toString());
                    targetJson.add("bones", McpTools.PRETTY_GSON.toJsonTree(boneNames(target.get())));

                    JsonArray templates = new JsonArray();

                    for (AssetExportManager.TemplateEntry template : compatibleTemplates(project, target)) {
                        JsonObject templateJson = new JsonObject();
                        templateJson.addProperty("name", template.displayName());
                        templateJson.add("mapping", McpTools.PRETTY_GSON.toJsonTree(template.template().mapping()));
                        templates.add(templateJson);
                    }

                    targetJson.add("templates", templates);
                    result.add("targetArmature", targetJson);
                } else {
                    result.add("targetArmatures", McpTools.PRETTY_GSON.toJsonTree(McpTools.armatureIds(project)));
                }

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(result));
            })
        ));

        McpTools.register(McpTool.of(
            "retarget_animations",
            "Retargets animation files made on a source armature onto a project armature and writes each as an .akasset "
                + "into 'exportDirectory', as the Animation Retarget tool's Export All does. One bone mapping applies to "
                + "every file: the saved 'template' first, then 'mapping' on top. Clip bones left unmapped are dropped. "
                + "Mapping a clip bone to 'Root' makes it the root motion; otherwise 'generateRootMotion' splits root "
                + "motion off the bone the source root bone maps to. Read the bone names with inspect_retarget first.",
            """
            {
              "type": "object",
              "properties": {
                "sourceArmature": {"type": "string", "description": "The armature file the animations were made on. Absolute, or relative to the project root."},
                "animations": {"type": "array", "items": {"type": "string"}, "minItems": 1, "description": "Animation files made on the source armature."},
                "targetArmature": {"type": "string", "description": "A project armature id."},
                "template": {"type": "string", "description": "A saved template name for the target armature, as inspect_retarget lists them."},
                "mapping": {"type": "object", "additionalProperties": {"type": ["string", "null"]}, "description": "Clip bone -> target armature bone. Null or '' unmaps the clip bone."},
                "generateRootMotion": {"type": "boolean", "description": "Default: false."},
                "exportDirectory": {"type": "string", "description": "A folder relative to the project root, e.g. 'assets/<namespace>/animmodels/animations/<folder>'."},
                "saveTemplateAs": {"type": "string", "description": "Also saves the mapping as a template under this name."}
              },
              "required": ["sourceArmature", "animations", "targetArmature", "exportDirectory"]
            }
            """,
            arguments -> McpTools.onClient(() -> {
                AkytheraProject project = McpTools.requireProject();
                Path root = project.getProjectRoot().toAbsolutePath().normalize();
                AssetAccessor<Armature> source = AssetExportManager.INSTANCE.loadExternalArmature(McpTools.requireFile(root, arguments.get("sourceArmature").getAsString()));
                AssetAccessor<? extends Armature> target = McpTools.requireArmature(project, arguments.get("targetArmature").getAsString());
                AssetExportManager.MappingTemplate mapping = new AssetExportManager.MappingTemplate(target.identifier());

                if (arguments.has("template")) {
                    String name = arguments.get("template").getAsString();
                    AssetExportManager.TemplateEntry template = compatibleTemplates(project, target).stream()
                        .filter(entry -> entry.displayName().equals(name))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No template '" + name + "' for " + target.identifier()));
                    mapping.copyFrom(template.template());
                }

                if (arguments.has("mapping")) {
                    List<String> targetBones = boneNames(target.get());

                    for (Map.Entry<String, JsonElement> pick : arguments.getAsJsonObject("mapping").entrySet()) {
                        String targetBone = pick.getValue().isJsonNull() ? "" : pick.getValue().getAsString();

                        if (targetBone.isEmpty()) {
                            mapping.clearMapping(pick.getKey());
                        } else if (targetBones.contains(targetBone)) {
                            mapping.putMapping(pick.getKey(), targetBone);
                        } else {
                            throw new IllegalArgumentException(target.identifier() + " has no bone '" + targetBone + "'");
                        }
                    }
                }

                List<RetargetEntry> entries = new ArrayList<>();

                for (JsonElement path : arguments.getAsJsonArray("animations")) {
                    RetargetEntry entry = AssetExportManager.INSTANCE.loadRetargetEntry(McpTools.requireFile(root, path.getAsString()), source, target.identifier());
                    entry.template.copyFrom(mapping);
                    entries.add(entry);
                }

                Path destination = McpTools.resolveInProject(root, arguments.get("exportDirectory").getAsString());
                boolean generateRootMotion = arguments.has("generateRootMotion") && arguments.get("generateRootMotion").getAsBoolean();
                List<String> lines = new ArrayList<>();

                for (AssetExportManager.ExportResult<RetargetEntry> result : AssetExportManager.INSTANCE.exportAll(entries, source.get(), destination, generateRootMotion)) {
                    switch (result) {
                        case AssetExportManager.ExportResult.Success<RetargetEntry> success -> {
                            List<String> unmapped = success.entry().boneNames.stream().filter(bone -> mapping.getTarget(bone) == null).toList();
                            lines.add("Wrote " + McpTools.relative(root, success.written().getFirst())
                                + (unmapped.isEmpty() ? "" : ", dropped unmapped bones: " + String.join(", ", unmapped)));
                        }
                        case AssetExportManager.ExportResult.Failure<RetargetEntry> failure -> lines.add("Failed " + failure.entry().label() + ": " + failure.reason());
                    }
                }

                if (arguments.has("saveTemplateAs")) {
                    Path directory = AssetExportManager.INSTANCE.templateDirectory(project).toAbsolutePath().normalize();
                    Path file = McpTools.resolveInProject(directory, arguments.get("saveTemplateAs").getAsString() + ".json");

                    lines.add(AssetExportManager.INSTANCE.saveTemplate(file, mapping)
                        ? "Saved the template " + McpTools.relative(root, file)
                        : "Failed to save the template, see read_console");
                }

                return McpToolResult.text(String.join("\n", lines));
            })
        ));

        // ***************************************************************
        // Model Transformer
        // ***************************************************************

        McpTools.register(McpTool.of(
            "inspect_model",
            "Reads a legacy .json model as the Model Transformer tool does: the export name it starts with, the highest "
                + "bone id its weights address (-1 when no bone deforms it), and the bones, with their ids, of the armature "
                + "it carries (null when it carries none). The path is absolute or relative to the project root.",
            """
            {
              "type": "object",
              "properties": {
                "file": {"type": "string"}
              },
              "required": ["file"]
            }
            """,
            arguments -> McpTools.onClient(() -> {
                Path root = McpTools.requireProject().getProjectRoot().toAbsolutePath().normalize();
                Path file = McpTools.requireFile(root, arguments.get("file").getAsString());
                AssetExportManager.ModelProbe probe = AssetExportManager.INSTANCE.probeModel(readModel(file));
                JsonObject result = new JsonObject();
                result.addProperty("exportName", AssetExportManager.seedExportName(file));
                result.addProperty("highestWeightBoneId", probe.highestWeightBoneId());

                if (probe.hasArmature()) {
                    JsonArray bones = new JsonArray();

                    AssetExportManager.INSTANCE.loadExternalArmature(file).get().visitBones(bone -> {
                        JsonObject boneJson = new JsonObject();
                        boneJson.addProperty("id", bone.getId());
                        boneJson.addProperty("name", bone.getName());
                        bones.add(boneJson);
                    });

                    result.add("armatureBones", bones);
                } else {
                    result.add("armatureBones", null);
                }

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(result));
            })
        ));

        McpTools.register(McpTool.of(
            "transform_models",
            "Converts legacy .json models into .akasset files under 'exportDirectory', as the Model Transformer tool's "
                + "Export All does: each mesh into 'meshes/<exportName>', and, while a model keeps the armature its file "
                + "carries, that armature into 'armatures/<exportName>'. 'armature' rides a project armature instead and "
                + "exports the mesh alone. 'boneRenames' renames bones of the carried armature on the way out. "
                + "'weightRemap' moves the vertices a source weight id deforms onto a bone of the armature the model rides. "
                + "Read the models with inspect_model first.",
            """
            {
              "type": "object",
              "properties": {
                "models": {
                  "type": "array",
                  "minItems": 1,
                  "items": {
                    "type": "object",
                    "properties": {
                      "file": {"type": "string", "description": "Absolute, or relative to the project root."},
                      "exportName": {"type": "string", "description": "Default: the name inspect_model shows."},
                      "armature": {"type": "string", "description": "A project armature id to ride."},
                      "boneRenames": {"type": "object", "additionalProperties": {"type": "string"}, "description": "Carried bone name -> exported name."},
                      "weightRemap": {"type": "object", "additionalProperties": {"type": "string"}, "description": "Source weight id -> bone name."}
                    },
                    "required": ["file"]
                  }
                },
                "exportDirectory": {"type": "string", "description": "A folder relative to the project root, e.g. 'assets/<namespace>/animmodels'."},
                "generateRootInOrigin": {"type": "boolean", "description": "Default: false."}
              },
              "required": ["models", "exportDirectory"]
            }
            """,
            arguments -> McpTools.onClient(() -> {
                AkytheraProject project = McpTools.requireProject();
                Path root = project.getProjectRoot().toAbsolutePath().normalize();
                List<AssetExportManager.ModelExport> exports = new ArrayList<>();
                Set<String> exportNames = new HashSet<>();
                List<String> warnings = new ArrayList<>();

                for (JsonElement element : arguments.getAsJsonArray("models")) {
                    JsonObject model = element.getAsJsonObject();
                    Path file = McpTools.requireFile(root, model.get("file").getAsString());
                    String fileName = file.getFileName().toString();
                    JsonObject json = readModel(file);
                    boolean carriesArmature = AssetExportManager.INSTANCE.probeModel(json).hasArmature();
                    String exportName = model.has("exportName") ? model.get("exportName").getAsString() : AssetExportManager.seedExportName(file);

                    if (!AssetExportManager.isValidExportName(exportName)) throw new IllegalArgumentException("Not a valid export name: " + exportName);
                    if (!exportNames.add(exportName)) throw new IllegalArgumentException("Two models export as " + exportName);

                    boolean keepsOwnArmature = !model.has("armature") && carriesArmature;
                    Armature armature = model.has("armature")
                        ? McpTools.requireArmature(project, model.get("armature").getAsString()).get()
                        : carriesArmature ? AssetExportManager.INSTANCE.loadExternalArmature(file).get() : null;
                    Map<String, String> renames = new LinkedHashMap<>();

                    if (model.has("boneRenames")) {
                        if (!keepsOwnArmature) throw new IllegalArgumentException(fileName + ": bone renames need the armature the file carries");

                        for (Map.Entry<String, JsonElement> rename : model.getAsJsonObject("boneRenames").entrySet()) {
                            String to = rename.getValue().getAsString().trim();

                            if (armature.getBoneByName(rename.getKey()) == null) throw new IllegalArgumentException(fileName + " has no bone '" + rename.getKey() + "'");
                            if (!to.isEmpty() && !to.equals(rename.getKey())) renames.put(rename.getKey(), to);
                        }

                        Set<String> exportedBones = new HashSet<>();
                        armature.visitBones(bone -> exportedBones.add(renames.getOrDefault(bone.getName(), bone.getName())));

                        if (exportedBones.size() != armature.getBoneCount()) throw new IllegalArgumentException(fileName + ": the renames give two bones one name");
                    }

                    Map<Integer, String> weightRemap = new LinkedHashMap<>();

                    if (model.has("weightRemap")) {
                        if (armature == null) throw new IllegalArgumentException(fileName + " rides no armature to move weights onto");

                        for (Map.Entry<String, JsonElement> remap : model.getAsJsonObject("weightRemap").entrySet()) {
                            String bone = remap.getValue().getAsString();

                            if (armature.getBoneByName(bone) == null) throw new IllegalArgumentException("The armature of " + fileName + " has no bone '" + bone + "'");

                            weightRemap.put(Integer.parseInt(remap.getKey()), bone);
                        }
                    }

                    Map<Integer, Integer> resolvedRemap = armature == null ? Map.of() : AssetExportManager.INSTANCE.resolveWeightRemap(weightRemap, armature);
                    AssetExportManager.INSTANCE.remapWeightBoneIds(json, resolvedRemap);
                    int highestWeightBoneId = AssetExportManager.INSTANCE.probeModel(json).highestWeightBoneId();

                    if (armature != null && armature.getBoneCount() <= highestWeightBoneId) {
                        warnings.add("Warning: " + fileName + "'s weights address bone " + highestWeightBoneId + ", beyond the "
                            + armature.getBoneCount() + " bones of its armature, so it does not skin");
                    }

                    exports.add(new AssetExportManager.ModelExport(file, exportName, renames, resolvedRemap, keepsOwnArmature));
                }

                Path destination = McpTools.resolveInProject(root, arguments.get("exportDirectory").getAsString());
                boolean generateRootInOrigin = arguments.has("generateRootInOrigin") && arguments.get("generateRootInOrigin").getAsBoolean();
                List<String> lines = new ArrayList<>();

                for (AssetExportManager.ExportResult<Path> result : AssetExportManager.INSTANCE.exportAllModels(exports, destination, generateRootInOrigin)) {
                    switch (result) {
                        case AssetExportManager.ExportResult.Success<Path> success -> lines.add("Wrote "
                            + success.written().stream().map(written -> McpTools.relative(root, written)).collect(Collectors.joining(", ")));
                        case AssetExportManager.ExportResult.Failure<Path> failure -> lines.add("Failed " + failure.entry().getFileName() + ": " + failure.reason());
                    }
                }

                lines.addAll(warnings);

                return McpToolResult.text(String.join("\n", lines));
            })
        ));
    }

    // ***************************************************************
    // Helpers
    // ***************************************************************

    private static List<String> boneNames(final Armature armature) {
        List<String> names = new ArrayList<>(armature.getBoneCount());
        armature.visitBones(bone -> names.add(bone.getName()));

        return names;
    }

    /// The saved retarget templates made for @param `target`.
    private static List<AssetExportManager.TemplateEntry> compatibleTemplates(final AkytheraProject project, final AssetAccessor<? extends Armature> target) {
        return AssetExportManager.INSTANCE.loadTemplates(AssetExportManager.INSTANCE.templateDirectory(project)).stream()
            .filter(entry -> Objects.equals(entry.template().armatureId(), target.identifier()))
            .toList();
    }

    /// The legacy model JSON at @param `file`.
    private static JsonObject readModel(final Path file) {
        JsonObject model = FileSystemHelper.readJsonObject(file);

        if (model == null) throw new IllegalArgumentException("Not a JSON model: " + file);

        return model;
    }
}
