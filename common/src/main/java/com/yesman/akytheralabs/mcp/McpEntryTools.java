package com.yesman.akytheralabs.mcp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.ProjectObjectRegistry;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import net.minecraft.resources.ResourceKey;

import java.nio.file.Path;

/// The [McpTool]s that read and write a whole data entry of the open project (a file under `data/`) as the editor
/// holds it in memory, in the form of its `.json` file. A write swaps the entry's holder as
/// [AkytheraProject#reloadEntryFromDisk] does and saves it, and makes an entry that does not exist yet.
final class McpEntryTools {
    private McpEntryTools() {}

    static void registerAll() {
        McpTools.register(McpTool.of(
            "get_entry",
            "Returns a data entry of the open project as JSON, in the form of its .json file, as the editor holds it in "
                + "memory: unsaved edits included. 'path' is the entry's file under 'data/', e.g. "
                + "'data/world/particle/piece/sweep.json'. Use it to learn the form set_entry takes, from an entry of the same "
                + "folder.",
            """
            {
              "type": "object",
              "properties": {
                "path": {"type": "string", "description": "The entry's file, relative to the project root."}
              },
              "required": ["path"]
            }
            """,
            arguments -> McpTools.onClient(() -> {
                AkytheraProject project = McpTools.requireProject();

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(encode(project, requireKey(project, arguments.get("path").getAsString()))));
            })
        ));

        McpTools.register(McpTool.of(
            "set_entry",
            "Writes a data entry of the open project and saves its file. An entry that does not exist is created, with its "
                + "folders, as the content browser's add button does: a particle piece, a particle system, an entity "
                + "patch and so on, by the folder under 'data/' the path is in. 'entry' is the whole entry in the form "
                + "get_entry returns; keys left out take their defaults. 'merge' instead replaces only the given top-level "
                + "keys of an existing entry. This is how a component is attached to or removed from an entity patch or "
                + "item patch (its 'components' map), and how an emitter's particle, spawn rule and modules are set. "
                + "Every editor screen above the main one must be closed first (close_editor_screen), since an open "
                + "screen keeps editing the entry it opened. The write is not an undo step. A project reload "
                + "(reload_project) takes it to the world. Returns the entry as it was read back.",
            """
            {
              "type": "object",
              "properties": {
                "path": {"type": "string", "description": "The entry's file, relative to the project root, e.g. 'data/world/particle/piece/overlay/stain.json'."},
                "entry": {"type": "object", "description": "The whole entry."},
                "merge": {"type": "object", "description": "Top-level keys to replace in the existing entry."}
              },
              "required": ["path"]
            }
            """,
            arguments -> McpTools.onClient(() -> {
                AkytheraProject project = McpTools.requireProject();
                AkytheraEditorLayoutManager layout = AkytheraEditorLayoutManager.getInstance();
                MainEditorScreen mainScreen = layout.getMainScreen();

                if (mainScreen == null) return McpToolResult.error("The editor layout is not open");
                if (layout.getRecentEditorScreen() != mainScreen) return McpToolResult.error("Close the editor screens above the main one first");
                if (arguments.has("entry") == arguments.has("merge")) return McpToolResult.error("Give one of 'entry' and 'merge'");

                ResourceKey<?> key = requireKey(project, arguments.get("path").getAsString());
                boolean created = write(project, key, arguments);

                mainScreen.explorerPanel.rebuildTree();

                JsonObject result = new JsonObject();
                result.addProperty("created", created);
                result.addProperty("id", key.identifier().toString());
                result.add("entry", encode(project, key));

                return McpToolResult.text(McpTools.PRETTY_GSON.toJson(result));
            })
        ));
    }

    /// The key of the entry `path` names.
    private static ResourceKey<?> requireKey(final AkytheraProject project, final String path) {
        Path root = project.getProjectRoot().toAbsolutePath().normalize();
        ResourceKey<?> key = project.entryKeyOf(McpTools.resolveInProject(root, path));

        if (key == null) throw new IllegalArgumentException("Not a data entry file of the project: " + path);

        return key;
    }

    /// The entry under `key` as its file's JSON.
    private static <T> JsonElement encode(final AkytheraProject project, final ResourceKey<T> key) {
        ProjectObjectRegistry<T, DataHolder<T>> registry = project.getModifiableRegistry(key.registryKey());
        DataHolder<T> holder = registry.getDataHolder(key);

        if (holder == null) throw new IllegalArgumentException("No entry " + key.identifier());

        return registry.getCodec().encodeStart(JsonOps.INSTANCE, holder)
            .getOrThrow(message -> new IllegalStateException("The entry does not encode: " + message));
    }

    /// Decodes the `entry` of `arguments`, or the existing entry with its `merge` keys replaced, registers it under
    /// `key` and saves it. True when `key` named no entry before.
    private static <T> boolean write(final AkytheraProject project, final ResourceKey<T> key, final JsonObject arguments) {
        ProjectObjectRegistry<T, DataHolder<T>> registry = project.getModifiableRegistry(key.registryKey());
        boolean created = registry.getDataHolder(key) == null;
        JsonObject json;

        if (arguments.has("entry")) {
            json = arguments.getAsJsonObject("entry");
        } else {
            json = encode(project, key).getAsJsonObject();
            arguments.getAsJsonObject("merge").entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue()));
        }

        DataHolder<T> decoded = registry.getCodec().parse(JsonOps.INSTANCE, json)
            .getOrThrow(message -> new IllegalArgumentException("The entry does not decode: " + message));

        if (created) {
            project.reinsertEntry(key, decoded);
        } else {
            registry.replaceDataHolder(key, decoded);
        }

        project.saveEntry(key);

        return created;
    }
}
