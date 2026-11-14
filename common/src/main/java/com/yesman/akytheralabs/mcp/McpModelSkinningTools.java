package com.yesman.akytheralabs.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.core.asset.mesh.SkeletalMesh;
import com.yesman.akythera.core.asset.armature.Bone;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.akythera.asset.BendingSettings;
import com.yesman.akytheralabs.akythera.asset.ModelSkinHolder;
import com.yesman.akytheralabs.gui.screen.ModelSkinningScreen;
import com.yesman.akytheralabs.gui.widget.main.AssetExportManager;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/// The [McpTool]s that read and edit the model on the open Model Skinning screen, as its panels, brush and buttons do.
final class McpModelSkinningTools {
    private McpModelSkinningTools() {}

    static void registerAll() {
        McpTools.register(McpTool.of(
            "get_model_skinning",
            "Returns the model on the open Model Skinning screen (opened with run_editor_action 'model_skinning') as JSON: "
                + "the picked 'model', 'armature' and 'texture', the 'exportName' an export writes under, the 'paintedBone', "
                + "the mesh 'parts' (each with the indices of its positions and its bounds), and 'bones', each armature bone "
                + "with how many positions it weighs. 'positions' adds those positions' locations and bone weights. "
                + "'candidates' adds every model, armature and skeletal mesh the ops can name. Coordinates are model space, "
                + "in blocks.",
            """
            {
              "type": "object",
              "properties": {
                "positions": {"type": "array", "items": {"type": "integer", "minimum": 0}},
                "candidates": {"type": "boolean", "description": "Default: false."}
              }
            }
            """,
            arguments -> McpTools.onClient(() -> {
                ModelSkinHolder holder = requireScreen().getHolder();
                JsonObject result = new JsonObject();
                result.addProperty("model", holder.getModel() == null ? null : ModelSkinHolder.modelKey(holder.getModel()));
                result.addProperty("armature", holder.getArmature().identifier().toString());
                result.addProperty("texture", holder.getTexture() == null ? null : holder.getTexture().texturePath().toString());
                result.addProperty("exportName", holder.exportName());
                result.addProperty("paintedBone", holder.getPaintedBone() == null ? null : holder.getPaintedBone().getName());

                JsonArray parts = new JsonArray();

                for (ModelSkinHolder.Part part : holder.parts()) {
                    JsonObject partJson = new JsonObject();
                    partJson.addProperty("name", part.name());
                    partJson.add("positions", McpTools.PRETTY_GSON.toJsonTree(part.positions()));
                    partJson.add("min", vectorJson(part.min()));
                    partJson.add("max", vectorJson(part.max()));
                    parts.add(partJson);
                }

                result.add("parts", parts);

                Map<String, Integer> weightedPositions = new LinkedHashMap<>();
                holder.getBones().forEach(bone -> weightedPositions.put(bone.getName(), 0));

                for (int position = 0; position < holder.positionCount(); position++) {
                    for (ModelSkinHolder.Influence influence : holder.influencesOf(position)) {
                        if (influence.getBone() != null && influence.getWeight() > 0.0F) weightedPositions.merge(influence.getBone().getName(), 1, Integer::sum);
                    }
                }

                result.add("bones", McpTools.PRETTY_GSON.toJsonTree(weightedPositions));

                if (arguments.has("positions")) {
                    JsonArray positions = new JsonArray();

                    for (JsonElement element : arguments.getAsJsonArray("positions")) {
                        int position = requirePosition(holder, element.getAsInt());
                        JsonObject positionJson = new JsonObject();
                        JsonObject weights = new JsonObject();

                        for (ModelSkinHolder.Influence influence : holder.influencesOf(position)) {
                            if (influence.getBone() != null) weights.addProperty(influence.getBone().getName(), influence.getWeight());
                        }

                        positionJson.addProperty("position", position);
                        positionJson.add("location", vectorJson(holder.position(position)));
                        positionJson.add("weights", weights);
                        positions.add(positionJson);
                    }

                    result.add("positions", positions);
                }

                if (arguments.has("candidates") && arguments.get("candidates").getAsBoolean()) {
                    AkytheraProject project = McpTools.requireProject();
                    JsonObject candidates = new JsonObject();
                    candidates.add("models", McpTools.PRETTY_GSON.toJsonTree(holder.models().stream().map(ModelSkinHolder::modelKey).toList()));
                    candidates.add("armatures", McpTools.PRETTY_GSON.toJsonTree(McpTools.armatureIds(project)));
                    candidates.add("skeletalMeshes", McpTools.PRETTY_GSON.toJsonTree(McpTools.skeletalMeshIds(project)));
                    result.add("candidates", candidates);
                }

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(result));
            })
        ));

        McpTools.register(McpTool.of(
            "edit_model_skinning",
            "Edits the model on the open Model Skinning screen, applying the ops in order, as its panels, brush and buttons "
                + "do. Ops: {op:'set_model', model:<key from candidates>|null}, {op:'set_armature', armature}, "
                + "{op:'set_texture', texture:<texture path>|null}, {op:'auto_weight', referenceMesh:<skeletal mesh id>} "
                + "(weights the model from that mesh, as the Auto Weight button), {op:'paint', bone, weight, positions?|part?|"
                + "box?:{min:[x,y,z], max:[x,y,z]}} (gives the bone that weight on the picked positions; their other bones "
                + "scale so each sums to 1), {op:'set_vertex_weights', position, weights:{<bone>: weight}}, "
                + "{op:'add_bending', part, axis:'x'|'y'|'z', coordinate, rootBone?, bendingBone?, inflationBone?, "
                + "direction?:'front'|'behind'} (as the Add Bending button, slicing the part there), {op:'export', "
                + "directory, name?} (writes the mesh as a skeletal mesh .akasset into a folder of the project). A picked "
                + "model or armature rebuilds the mesh, dropping painted weights. 'paint' and 'set_vertex_weights' are one "
                + "undo step each. A failing op stops the call; the ops before it stay applied.",
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
                ModelSkinningScreen screen = requireScreen();
                ModelSkinHolder holder = screen.getHolder();
                List<String> lines = new ArrayList<>();
                int applied = 0;
                String failure = null;

                for (JsonElement element : arguments.getAsJsonArray("ops")) {
                    JsonObject op = element.getAsJsonObject();

                    try {
                        switch (op.get("op").getAsString()) {
                            case "set_model" -> {
                                String key = op.get("model").isJsonNull() ? null : op.get("model").getAsString();
                                Object model = key == null ? null : holder.models().stream()
                                    .filter(candidate -> ModelSkinHolder.modelKey(candidate).equals(key))
                                    .findFirst()
                                    .orElseThrow(() -> new IllegalArgumentException("No model " + key + "; see the candidates of get_model_skinning"));

                                holder.setModel(model);
                            }
                            case "set_armature" -> holder.setArmature(McpTools.requireArmature(project, op.get("armature").getAsString()));
                            case "set_texture" -> holder.setTexture(op.get("texture").isJsonNull() ? null : AkytheraProject.textureOf(McpTools.requireId(op.get("texture").getAsString())));
                            case "auto_weight" -> {
                                requireMesh(holder);

                                String id = op.get("referenceMesh").getAsString();
                                AssetAccessor<?> accessor = project.listAllMeshAccessors()
                                    .filter(candidate -> candidate.checkType(AssetAccessor.Type.SKELETAL_MESH) && candidate.identifier().toString().equals(id))
                                    .findFirst()
                                    .orElseThrow(() -> new IllegalArgumentException("No skeletal mesh " + id + " in the project"));

                                if (!(accessor.getOrHandleException(AkytheraLabs.LOGGER::error) instanceof SkeletalMesh reference)) {
                                    throw new IllegalArgumentException("The mesh " + id + " does not load, see read_console");
                                }

                                holder.rebuild(reference);
                            }
                            case "paint" -> {
                                requireMesh(holder);

                                Bone bone = requireBone(holder, op.get("bone").getAsString());
                                float weight = op.get("weight").getAsFloat();
                                int[] positions;

                                if (op.has("positions")) {
                                    positions = StreamSupport.stream(op.getAsJsonArray("positions").spliterator(), false)
                                        .mapToInt(index -> requirePosition(holder, index.getAsInt()))
                                        .toArray();
                                } else if (op.has("part")) {
                                    String part = op.get("part").getAsString();
                                    positions = holder.parts().stream()
                                        .filter(candidate -> candidate.name().equals(part))
                                        .findFirst()
                                        .map(ModelSkinHolder.Part::positions)
                                        .orElseThrow(() -> new IllegalArgumentException("The mesh has no part " + part));
                                } else if (op.has("box")) {
                                    Vector3f min = vectorOf(op.getAsJsonObject("box").get("min"));
                                    Vector3f max = vectorOf(op.getAsJsonObject("box").get("max"));
                                    positions = IntStream.range(0, holder.positionCount())
                                        .filter(position -> {
                                            Vector3f at = holder.position(position);

                                            return at.x >= min.x && at.y >= min.y && at.z >= min.z && at.x <= max.x && at.y <= max.y && at.z <= max.z;
                                        })
                                        .toArray();
                                } else {
                                    throw new IllegalArgumentException("A paint op needs 'positions', 'part' or 'box'");
                                }

                                holder.setPaintedBone(bone);
                                screen.editWeights(() -> holder.paintPositions(positions, weight));
                                lines.add("Painted " + bone.getName() + " on " + positions.length + " positions");
                            }
                            case "set_vertex_weights" -> {
                                requireMesh(holder);

                                int position = requirePosition(holder, op.get("position").getAsInt());
                                Map<Bone, Float> influences = new LinkedHashMap<>();

                                for (Map.Entry<String, JsonElement> influence : op.getAsJsonObject("weights").entrySet()) {
                                    influences.put(requireBone(holder, influence.getKey()), influence.getValue().getAsFloat());
                                }

                                screen.editWeights(() -> holder.setInfluences(position, influences));
                            }
                            case "add_bending" -> {
                                requireMesh(holder);

                                String axisName = op.get("axis").getAsString();
                                int axis = axisName.length() == 1 ? "xyz".indexOf(axisName) : -1;

                                if (axis < 0) throw new IllegalArgumentException("Not an axis: " + axisName);

                                ModelSkinHolder.Slice slice = holder.sliceAcross(op.get("part").getAsString(), axis, op.get("coordinate").getAsFloat());

                                if (slice == null) throw new IllegalArgumentException("The mesh has no part " + op.get("part").getAsString());

                                Bone rootBone = optionalBone(holder, op, "rootBone");
                                Bone bendingBone = optionalBone(holder, op, "bendingBone");

                                if (rootBone != null && rootBone.equals(bendingBone)) throw new IllegalArgumentException("The root and bending bones are one bone");

                                holder.addBending(slice, new BendingSettings(
                                    rootBone,
                                    bendingBone,
                                    optionalBone(holder, op, "inflationBone"),
                                    op.has("direction") ? BendingSettings.Direction.valueOf(op.get("direction").getAsString().toUpperCase(Locale.ROOT)) : BendingSettings.Direction.FRONT
                                ));
                            }
                            case "export" -> {
                                requireMesh(holder);

                                Path folder = McpTools.resolveInProject(root, op.get("directory").getAsString());
                                String name = op.has("name") ? op.get("name").getAsString() : holder.exportName();

                                if (!AssetExportManager.isValidExportName(name)) throw new IllegalArgumentException("Not a valid export name: " + name);

                                try {
                                    lines.add("Wrote " + McpTools.relative(root, screen.exportTo(folder, name)));
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
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

                screen.refresh();
                lines.add("Applied " + applied + " ops");

                return failure == null ? McpToolResult.text(String.join("\n", lines)) : McpToolResult.error(String.join("\n", lines) + ". " + failure);
            })
        ));
    }

    // ***************************************************************
    // Helpers
    // ***************************************************************

    private static ModelSkinningScreen requireScreen() {
        if (!(AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen() instanceof ModelSkinningScreen screen)) {
            throw new IllegalStateException("The Model Skinning screen is not on top; open it with run_editor_action 'model_skinning'");
        }

        return screen;
    }

    private static void requireMesh(final ModelSkinHolder holder) {
        if (!holder.hasMesh()) throw new IllegalStateException("No mesh yet: pick a model and an armature first");
    }

    private static int requirePosition(final ModelSkinHolder holder, final int position) {
        if (position < 0 || position >= holder.positionCount()) throw new IllegalArgumentException("The mesh has no position " + position);

        return position;
    }

    private static Bone requireBone(final ModelSkinHolder holder, final String name) {
        return holder.getBones().stream()
            .filter(bone -> bone.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("The armature has no bone " + name));
    }

    /// The bone the op's @param `key` names, or null when the op leaves it out.
    private static @Nullable Bone optionalBone(final ModelSkinHolder holder, final JsonObject op, final String key) {
        return op.has(key) && !op.get(key).isJsonNull() ? requireBone(holder, op.get(key).getAsString()) : null;
    }

    private static JsonArray vectorJson(final Vector3f vector) {
        JsonArray json = new JsonArray();
        json.add(vector.x);
        json.add(vector.y);
        json.add(vector.z);

        return json;
    }

    private static Vector3f vectorOf(final JsonElement json) {
        JsonArray array = json.getAsJsonArray();

        return new Vector3f(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
    }
}
