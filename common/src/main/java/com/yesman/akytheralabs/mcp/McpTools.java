package com.yesman.akytheralabs.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.yesman.akythera.api.asset.AssetAccessor;
import com.yesman.akythera.client.api.gui.AkytheraEditBox;
import com.yesman.akythera.client.api.gui.modal.Modal;
import com.yesman.akythera.client.api.gui.modal.ModalManager;
import com.yesman.akythera.client.api.gui.viewer.CameraComponent;
import com.yesman.akythera.client.api.gui.viewer.EntityModelComponent;
import com.yesman.akythera.client.api.gui.viewer.Model3DViewer;
import com.yesman.akythera.core.asset.armature.Armature;
import com.yesman.akytheralabs.AkytheraEditorLayoutManager;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.dev.ProjectReloader;
import com.yesman.akytheralabs.editor.debug.EditorLogBuffer;
import com.yesman.akytheralabs.editor.historytrack.HistoryManager;
import com.yesman.akytheralabs.gui.panel.AdjustablePanelManager;
import com.yesman.akytheralabs.gui.panel.main.ExplorerPanel;
import com.yesman.akytheralabs.gui.screen.MainEditorScreen;
import com.yesman.akytheralabs.gui.widget.TabPanel;
import com.yesman.akytheralabs.gui.widget.main.ContentWidget;
import com.yesman.akytheralabs.gui.widget.main.TopMenuBar;
import com.yesman.akytheralabs.gui.widget.main.TopMenuOption;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import org.apache.logging.log4j.Level;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Every [McpTool] [AkytheraMcpServer] offers. Addons add their own through [#register].
public final class McpTools {
    private static final long TIMEOUT_SECONDS = 15;
    private static final long RELOAD_TIMEOUT_SECONDS = 180;
    private static final Map<String, McpTool> TOOLS = Collections.synchronizedMap(new LinkedHashMap<>());

    static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private McpTools() {}

    /// Adds @param `tool`, replacing a tool of the same name.
    public static void register(final McpTool tool) {
        TOOLS.put(tool.name(), tool);
    }

    public static List<McpTool> all() {
        synchronized (TOOLS) {
            return List.copyOf(TOOLS.values());
        }
    }

    public static @Nullable McpTool get(final String name) {
        return TOOLS.get(name);
    }

    static {
        // ***************************************************************
        // Editor
        // ***************************************************************

        register(McpTool.of(
            "get_editor_state",
            "Returns the editor state as JSON: whether the editor is open, the loaded project and its root folder, "
                + "the open editor screens from bottom to top, the open modal, the game screen over the world (a pause "
                + "screen stops input and ticking), and the local player in the world.",
            """
            {"type": "object", "properties": {}}
            """,
            _ -> onClient(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                AkytheraEditorLayoutManager layout = AkytheraEditorLayoutManager.getInstance();
                AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();
                JsonObject state = new JsonObject();

                state.addProperty("editorOpen", layout.isActive());
                state.addProperty("editorShown", layout.isLayoutShown());

                if (project != null) {
                    JsonObject projectJson = new JsonObject();
                    projectJson.addProperty("name", project.getProjectName());
                    projectJson.addProperty("root", project.getProjectRoot().toAbsolutePath().toString());
                    state.add("project", projectJson);
                }

                JsonArray screens = new JsonArray();

                for (Screen screen : layout.getOpenScreens()) {
                    JsonObject screenJson = new JsonObject();
                    screenJson.addProperty("type", screen.getClass().getSimpleName());
                    screenJson.addProperty("title", screen.getTitle().getString());
                    screens.add(screenJson);
                }

                state.add("screens", screens);

                AbstractWidget modal = ModalManager.getInstance().getModal();
                state.addProperty("modal", modal == null ? null : modal.getClass().getSimpleName());

                Screen gameScreen = minecraft.gui.screen();
                state.addProperty("gameScreen", gameScreen == null ? null : gameScreen.getClass().getSimpleName());
                state.addProperty("paused", minecraft.isPaused());
                state.addProperty("windowFocused", minecraft.isWindowActive());
                state.addProperty("pauseOnLostFocus", minecraft.options.pauseOnLostFocus);

                if (minecraft.player != null && minecraft.level != null) {
                    JsonObject player = new JsonObject();
                    player.addProperty("uuid", minecraft.player.getUUID().toString());
                    player.addProperty("dimension", minecraft.level.dimension().identifier().toString());
                    player.addProperty("x", minecraft.player.getX());
                    player.addProperty("y", minecraft.player.getY());
                    player.addProperty("z", minecraft.player.getZ());
                    player.addProperty("yaw", minecraft.player.getYRot());
                    player.addProperty("pitch", minecraft.player.getXRot());
                    player.addProperty("singleplayer", minecraft.getSingleplayerServer() != null);
                    state.add("player", player);
                }

                return McpToolResult.text(PRETTY_GSON.toJson(state));
            })
        ));

        register(McpTool.of(
            "list_project_files",
            "Lists the files of the open project, as paths relative to the project root with '/' separators.",
            """
            {
              "type": "object",
              "properties": {
                "directory": {"type": "string", "description": "A folder relative to the project root to list. Default: the whole project."},
                "maxResults": {"type": "integer", "minimum": 1, "maximum": 5000, "description": "Default: 500."}
              }
            }
            """,
            arguments -> {
                Path root = requireProject().getProjectRoot().toAbsolutePath().normalize();
                Path directory = resolveInProject(root, arguments.has("directory") ? arguments.get("directory").getAsString() : "");
                int maxResults = arguments.has("maxResults") ? Math.clamp(arguments.get("maxResults").getAsInt(), 1, 5000) : 500;

                if (!Files.isDirectory(directory)) return McpToolResult.error("Not a folder: " + root.relativize(directory));

                List<String> files;

                try (Stream<Path> walk = Files.walk(directory)) {
                    files = walk.filter(Files::isRegularFile)
                        .map(path -> root.relativize(path).toString().replace('\\', '/'))
                        .sorted()
                        .limit(maxResults + 1L)
                        .toList();
                }

                String listing = String.join("\n", files.subList(0, Math.min(files.size(), maxResults)));

                return McpToolResult.text(files.size() > maxResults ? listing + "\n... (more files, raise maxResults)" : listing);
            }
        ));

        register(McpTool.of(
            "open_editor",
            "Opens the editor screen of a project file, the same as double-clicking it in the content browser. "
                + "The editor layout must be open.",
            """
            {
              "type": "object",
              "properties": {
                "path": {"type": "string", "description": "A file path relative to the project root, e.g. 'assets/<namespace>/animmodels/animations/idle.json'."}
              },
              "required": ["path"]
            }
            """,
            arguments -> {
                Path root = requireProject().getProjectRoot().toAbsolutePath().normalize();
                Path file = resolveInProject(root, arguments.get("path").getAsString());

                return onClient(() -> {
                    AkytheraEditorLayoutManager layout = AkytheraEditorLayoutManager.getInstance();
                    MainEditorScreen mainScreen = layout.getMainScreen();

                    if (mainScreen == null) return McpToolResult.error("The editor layout is not open");
                    if (!Files.isRegularFile(file)) return McpToolResult.error("No such file: " + root.relativize(file));

                    ExplorerPanel.TreeNode folder = mainScreen.explorerPanel.findNodeByPath(file.getParent());

                    if (folder == null || folder.processor == null) return McpToolResult.error("The file is not inside an asset folder of the project");

                    ContentWidget<?> content = folder.processor.createContentWidget(Minecraft.getInstance().font, file);

                    if (content == null || !content.isOpenable()) return McpToolResult.error("The file has no editor: " + root.relativize(file));

                    Screen before = layout.getRecentEditorScreen();
                    content.onDoubleClicked();
                    Screen after = layout.getRecentEditorScreen();

                    if (after == before) return McpToolResult.error("No editor screen opened for " + root.relativize(file));

                    return McpToolResult.text("Opened " + after.getClass().getSimpleName() + " (" + after.getTitle().getString() + ")");
                });
            }
        ));

        register(McpTool.of(
            "close_editor_screen",
            "Closes the top editor screen, returning to the screen below. The main editor screen is never closed.",
            """
            {"type": "object", "properties": {}}
            """,
            _ -> onClient(() -> {
                AkytheraEditorLayoutManager layout = AkytheraEditorLayoutManager.getInstance();
                Screen top = layout.getRecentEditorScreen();

                if (top == null || top == layout.getMainScreen()) return McpToolResult.error("No editor screen to close");

                layout.closeLastScreen();
                Screen now = layout.getRecentEditorScreen();

                if (now == top) return McpToolResult.error(top.getClass().getSimpleName() + " stayed open, likely asking to save changes");

                return McpToolResult.text("Closed " + top.getClass().getSimpleName());
            })
        ));

        register(McpTool.of(
            "list_unsaved_editors",
            "Lists the open editor screens that have unsaved changes, from bottom to top. Save or close them before "
                + "changing their files on disk, or the next save overwrites the change.",
            """
            {"type": "object", "properties": {}}
            """,
            _ -> onClient(() -> {
                List<String> unsaved = new ArrayList<>();

                for (Screen screen : AkytheraEditorLayoutManager.getInstance().getOpenScreens()) {
                    boolean dirty = findWidgets(screen, TopMenuBar.class).stream().anyMatch(TopMenuBar::isDirty);

                    if (dirty) unsaved.add(screen.getClass().getSimpleName() + " (" + screen.getTitle().getString() + ")");
                }

                return McpToolResult.text(unsaved.isEmpty() ? "No open editor has unsaved changes" : String.join("\n", unsaved));
            })
        ));

        String actionNames = Stream.concat(Arrays.stream(TopMenuOption.values()).map(option -> option.name().toLowerCase(Locale.ROOT)), Stream.of("undo", "redo"))
            .map(name -> "\"" + name + "\"")
            .collect(Collectors.joining(", "));

        register(McpTool.of(
            "run_editor_action",
            "Runs a top menu action on the top editor screen, as picking it from the menu bar would: 'save', 'compile' and "
                + "so on. 'undo' and 'redo' step the editor history. An action that opens a modal leaves it open; see "
                + "get_editor_state. A picker modal ('change_parent', 'select_preview_model', 'select_preview_animation', "
                + "'select_preview_item', 'debug') is driven with get_modal and pick_in_modal. The other modals have "
                + "tools of their own, which need no modal open: export_project, set_editor_preferences, "
                + "edit_localization, edit_project_tags, set_project_settings, set_entity_preview_models, "
                + "retarget_animations and transform_models. 'model_skinning' opens a screen that get_model_skinning "
                + "and edit_model_skinning edit.",
            """
            {
              "type": "object",
              "properties": {
                "action": {"type": "string", "enum": [%s]}
              },
              "required": ["action"]
            }
            """.formatted(actionNames),
            arguments -> {
                String action = arguments.get("action").getAsString().toLowerCase(Locale.ROOT);

                return onClient(() -> {
                    Screen top = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();

                    if (top == null) return McpToolResult.error("The editor layout is not open");

                    switch (action) {
                        case "undo" -> HistoryManager.getInstance().undo();
                        case "redo" -> HistoryManager.getInstance().redo();
                        default -> {
                            TopMenuOption option = TopMenuOption.valueOf(action.toUpperCase(Locale.ROOT));
                            boolean ran = findWidgets(top, TopMenuBar.class).stream().findFirst().map(bar -> bar.runAction(option)).orElse(false);

                            if (!ran) return McpToolResult.error(top.getClass().getSimpleName() + " does not offer '" + action + "' now");
                        }
                    }

                    AbstractWidget modal = ModalManager.getInstance().getModal();

                    return McpToolResult.text("Ran '" + action + "' on " + top.getClass().getSimpleName()
                        + (modal == null ? "" : ". A modal is open: " + modal.getClass().getSimpleName()));
                });
            }
        ));

        register(McpTool.of(
            "get_modal",
            "Returns the open modal as JSON, or null: its 'type', its 'title', and for a modal with a search box its "
                + "'search' text and 'results', the rows the search leaves in list order, which pick_in_modal confirms "
                + "by index.",
            """
            {"type": "object", "properties": {}}
            """,
            _ -> onClient(() -> {
                AbstractWidget modal = ModalManager.getInstance().getModal();

                if (modal == null) return McpToolResult.text("null");

                JsonObject json = new JsonObject();
                json.addProperty("type", modal.getClass().getSimpleName());
                json.addProperty("title", modal.getMessage().getString());

                if (modal instanceof Modal picker && picker.searchBox() instanceof AkytheraEditBox<String> searchBox) {
                    json.addProperty("search", searchBox.getText());
                    json.add("results", PRETTY_GSON.toJsonTree(picker.results()));
                }

                return McpToolResult.text(PRETTY_GSON.toJson(json));
            })
        ));

        register(McpTool.of(
            "pick_in_modal",
            "Confirms a row of the open picker modal, as a click would: types 'search' into its search box first when "
                + "given, then confirms the row at 'index' of the results get_modal lists, or the only row left when "
                + "'index' is absent.",
            """
            {
              "type": "object",
              "properties": {
                "search": {"type": "string"},
                "index": {"type": "integer", "minimum": 0}
              }
            }
            """,
            arguments -> onClient(() -> {
                AbstractWidget modal = ModalManager.getInstance().getModal();

                if (!(modal instanceof Modal picker) || !(picker.searchBox() instanceof AkytheraEditBox<String> searchBox)) {
                    return McpToolResult.error("No picker modal is open");
                }

                if (arguments.has("search")) searchBox.setValueByString(arguments.get("search").getAsString());

                boolean confirmed = arguments.has("index") ? picker.confirmResult(arguments.get("index").getAsInt()) : picker.confirmSoleResult();

                if (!confirmed) return McpToolResult.error("Nothing was confirmed. The results are: " + picker.results());

                AbstractWidget now = ModalManager.getInstance().getModal();

                return McpToolResult.text("Confirmed" + (now == null ? "" : ". A modal is open: " + now.getClass().getSimpleName()));
            })
        ));

        register(McpTool.of(
            "read_console",
            "Reads the editor console: the game's log lines, oldest first. Each line starts with '#<id>'; pass the last id "
                + "as 'afterId' next time to read only newer lines. Script compile errors, codec failures and exceptions land here.",
            """
            {
              "type": "object",
              "properties": {
                "minLevel": {"type": "string", "enum": ["debug", "info", "warn", "error"], "description": "Default: info."},
                "afterId": {"type": "integer", "description": "Only lines with a greater id. Default: every line."},
                "contains": {"type": "string", "description": "Only lines containing this text, ignoring case."},
                "maxLines": {"type": "integer", "minimum": 1, "maximum": 2000, "description": "The newest lines kept when more match. Default: 200."}
              }
            }
            """,
            arguments -> {
                Level minLevel = Level.toLevel(arguments.has("minLevel") ? arguments.get("minLevel").getAsString() : "info", Level.INFO);
                long afterId = arguments.has("afterId") ? arguments.get("afterId").getAsLong() : -1L;
                String contains = arguments.has("contains") ? arguments.get("contains").getAsString().toLowerCase(Locale.ROOT) : null;
                int maxLines = arguments.has("maxLines") ? Math.clamp(arguments.get("maxLines").getAsInt(), 1, 2000) : 200;

                List<EditorLogBuffer.Line> lines = EditorLogBuffer.getInstance().snapshot().stream()
                    .filter(line -> line.id() > afterId && line.level().isMoreSpecificThan(minLevel))
                    .filter(line -> contains == null || line.text().toLowerCase(Locale.ROOT).contains(contains))
                    .toList();

                if (lines.isEmpty()) return McpToolResult.text("(no lines)");

                return McpToolResult.text(lines.subList(Math.max(0, lines.size() - maxLines), lines.size()).stream()
                    .map(line -> "#" + line.id() + " " + line.text())
                    .collect(Collectors.joining("\n")));
            }
        ));

        register(McpTool.of(
            "reload_project",
            "Compiles every project entry, exports the project as temporary packs and reloads the world with them, "
                + "the same as the viewport's reload button. Waits until the world has reloaded. Each entry is exported "
                + "as the editor holds it: a component's Details rows write into the entry, so they reach the world "
                + "unsaved, but a node graph canvas reaches its entry only when its editor saves.",
            """
            {
              "type": "object",
              "properties": {
                "force": {"type": "boolean", "description": "Reload even when some entries are broken, leaving them out. Default: false."}
              }
            }
            """,
            arguments -> {
                boolean force = arguments.has("force") && arguments.get("force").getAsBoolean();
                long lastLineId = EditorLogBuffer.getInstance().snapshot().stream().mapToLong(EditorLogBuffer.Line::id).max().orElse(-1L);
                CompletableFuture<McpToolResult> reloaded = onClient(() -> {
                    AkytheraProject project = requireProject();
                    project.compileAll();
                    List<ResourceKey<?>> broken = project.findBrokenEntries();

                    if (!broken.isEmpty() && !force) {
                        return CompletableFuture.completedFuture(McpToolResult.error(
                            "These entries are broken and would be left out; fix them or pass force=true:\n"
                                + broken.stream().map(project::getRelativeEntryPath).sorted().collect(Collectors.joining("\n"))
                        ));
                    }

                    return ProjectReloader.reload(project).thenApply(_ -> McpToolResult.text(
                        "Reloaded the world. Read the log lines of the reload with read_console afterId=" + lastLineId
                    ));
                });

                return await(reloaded, RELOAD_TIMEOUT_SECONDS);
            }
        ));

        register(McpTool.of(
            "capture_screenshot",
            "Captures the last presented frame as a PNG and saves it under '<game folder>/screenshots/akytheralabs_mcp'. "
                + "'window' is what the user sees, the editor layout included. 'world' is the 3D world viewport alone.",
            """
            {
              "type": "object",
              "properties": {
                "target": {"type": "string", "enum": ["window", "world"], "description": "Default: window."},
                "downscale": {"type": "integer", "minimum": 1, "maximum": 8, "description": "Divides the image size. Default: 2."}
              }
            }
            """,
            arguments -> {
                boolean world = arguments.has("target") && "world".equals(arguments.get("target").getAsString());
                int downscale = arguments.has("downscale") ? Math.clamp(arguments.get("downscale").getAsInt(), 1, 8) : 2;
                CompletableFuture<McpToolResult> captured = new CompletableFuture<>();

                onClient(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    RenderTarget target = world ? minecraft.gameRenderer.mainRenderTarget() : AkytheraEditorLayoutManager.getInstance().getPresentedRenderTarget();
                    Path folder = minecraft.gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR).resolve("akytheralabs_mcp");

                    Screenshot.takeScreenshot(target, image -> Util.ioPool().execute(() -> {
                        try (image; NativeImage scaled = new NativeImage(Math.max(1, image.getWidth() / downscale), Math.max(1, image.getHeight() / downscale), false)) {
                            image.resizeSubRectTo(0, 0, image.getWidth(), image.getHeight(), scaled);

                            Path file = folder.resolve("mcp_" + System.currentTimeMillis() + ".png");
                            Files.createDirectories(folder);
                            scaled.writeToFile(file);
                            captured.complete(McpToolResult.image(Files.readAllBytes(file), "Saved " + scaled.getWidth() + "x" + scaled.getHeight() + " to " + file.toAbsolutePath()));
                        } catch (Exception e) {
                            captured.completeExceptionally(e);
                        }
                    }));

                    return null;
                });

                return await(captured);
            }
        ));

        register(McpTool.of(
            "get_viewer_camera",
            "Lists the 3D viewers on the top editor screen, then in the open modal, each with its 'index', its 'owner', its "
                + "'rect' in GUI pixels, whether it shows an entity model, and its orbit 'camera': 'distance' to the focus in "
                + "blocks, 'pitch' and 'yaw' in degrees (pitch > 0 looks down; yaw 180 looks at the front of a model facing "
                + "-Z, 0 at its back, 90 and -90 at its sides), the 'focus' point in model space and the 'viewMode'.",
            """
            {"type": "object", "properties": {}}
            """,
            _ -> onClient(() -> {
                JsonArray viewers = new JsonArray();
                List<Model3DViewer> found = findViewers();

                for (int index = 0; index < found.size(); index++) {
                    Model3DViewer viewer = found.get(index);
                    CameraComponent camera = viewer.getComponent(CameraComponent.class);
                    JsonObject viewerJson = new JsonObject();
                    viewerJson.addProperty("index", index);
                    viewerJson.addProperty("owner", ownerOf(viewer));
                    viewerJson.add("rect", PRETTY_GSON.toJsonTree(new int[] {viewer.getX(), viewer.getY(), viewer.getWidth(), viewer.getHeight()}));
                    viewerJson.addProperty("showsEntityModel", viewer.getComponent(EntityModelComponent.class) != null);

                    if (camera != null) {
                        Vector3f focus = camera.getTargetFocus();
                        JsonObject cameraJson = new JsonObject();
                        cameraJson.addProperty("distance", camera.getTargetDistance());
                        cameraJson.addProperty("pitch", camera.getTargetXRot());
                        cameraJson.addProperty("yaw", camera.getTargetYRot());
                        cameraJson.add("focus", PRETTY_GSON.toJsonTree(new float[] {focus.x, focus.y, focus.z}));
                        cameraJson.addProperty("viewMode", camera.getViewMode().name().toLowerCase(Locale.ROOT));
                        viewerJson.add("camera", cameraJson);
                    }

                    viewers.add(viewerJson);
                }

                return McpToolResult.text(PRETTY_GSON.toJson(viewers));
            })
        ));

        register(McpTool.of(
            "set_viewer_camera",
            "Places the orbit camera of a 3D viewer get_viewer_camera lists, at once and without easing, so a screenshot "
                + "taken next shows it. A value left out keeps the camera's own. To frame the preview model as the menu "
                + "does, use run_editor_action 'frame_camera' instead.",
            """
            {
              "type": "object",
              "properties": {
                "viewer": {"type": "integer", "minimum": 0, "description": "The index get_viewer_camera lists. Default: 0."},
                "distance": {"type": "number", "minimum": 0.5, "maximum": 100, "description": "Blocks from the camera to the focus."},
                "pitch": {"type": "number", "minimum": -90, "maximum": 90},
                "yaw": {"type": "number"},
                "focus": {"type": "array", "items": {"type": "number"}, "minItems": 3, "maxItems": 3},
                "viewMode": {"type": "string", "enum": ["frustum", "orthographic"]}
              }
            }
            """,
            arguments -> onClient(() -> {
                List<Model3DViewer> viewers = findViewers();
                int index = arguments.has("viewer") ? arguments.get("viewer").getAsInt() : 0;

                if (index < 0 || index >= viewers.size()) return McpToolResult.error("No viewer " + index + "; " + viewers.size() + " are open");

                CameraComponent camera = viewers.get(index).getComponent(CameraComponent.class);

                if (camera == null) return McpToolResult.error("Viewer " + index + " has no camera");

                double distance = arguments.has("distance") ? Math.clamp(arguments.get("distance").getAsDouble(), 0.5, 100.0) : camera.getTargetDistance();
                float pitch = arguments.has("pitch") ? Math.clamp(arguments.get("pitch").getAsFloat(), -90.0F, 90.0F) : camera.getTargetXRot();
                float yaw = arguments.has("yaw") ? arguments.get("yaw").getAsFloat() : camera.getTargetYRot();
                Vector3f focus = camera.getTargetFocus();

                if (arguments.has("focus")) {
                    JsonArray array = arguments.getAsJsonArray("focus");
                    focus.set(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat());
                }

                if (arguments.has("viewMode")) camera.setViewMode(CameraComponent.ViewMode.valueOf(arguments.get("viewMode").getAsString().toUpperCase(Locale.ROOT)));

                camera.setCameraTransform(-distance, pitch, yaw, focus);

                return McpToolResult.text("Placed the camera of viewer " + index);
            })
        ));

        McpWorldTools.registerAll();
        McpScriptGraphTools.registerAll();
        McpBehaviorTools.registerAll();
        McpComponentTools.registerAll();
        McpTrackTools.registerAll();
        McpEntryTools.registerAll();
        McpParticleTools.registerAll();
        McpConversionTools.registerAll();
        McpProjectTools.registerAll();
        McpPreferenceTools.registerAll();
        McpModelSkinningTools.registerAll();
    }

    // ***************************************************************
    // Helpers
    // ***************************************************************

    /// Runs @param `task` on the client thread and waits for its result.
    static <T> T onClient(final Supplier<T> task) throws Exception {
        return await(Minecraft.getInstance().submit(task));
    }

    /// Runs @param `task` on the integrated server thread with the local player and waits for its result.
    static McpToolResult onServer(final Function<ServerPlayer, McpToolResult> task) throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();

        if (server == null || minecraft.player == null) return McpToolResult.error("This tool needs a singleplayer world");

        UUID playerId = minecraft.player.getUUID();

        return await(server.submit(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);

            return player == null ? McpToolResult.error("The local player is not in the world") : task.apply(player);
        }));
    }

    static <T> T await(final CompletableFuture<T> future) throws Exception {
        return await(future, TIMEOUT_SECONDS);
    }

    static <T> T await(final CompletableFuture<T> future, final long timeoutSeconds) throws Exception {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    static AkytheraProject requireProject() {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();

        if (project == null) throw new IllegalStateException("No project is loaded");

        return project;
    }

    /// Resolves @param `relative` against the project @param `root`, refusing paths that leave it.
    static Path resolveInProject(final Path root, final String relative) {
        Path resolved = root.resolve(relative.replace('\\', '/')).normalize();

        if (!resolved.startsWith(root)) throw new IllegalArgumentException("The path leaves the project folder: " + relative);

        return resolved;
    }

    /// @param `path` as an absolute path, or relative to the project @param `root`. Unlike [#resolveInProject], it may
    /// lie outside the project.
    static Path resolveAgainstProject(final Path root, final String path) {
        return root.resolve(path.replace('\\', '/')).toAbsolutePath().normalize();
    }

    /// The existing file [#resolveAgainstProject] gives for @param `path`.
    static Path requireFile(final Path root, final String path) {
        Path file = resolveAgainstProject(root, path);

        if (!Files.isRegularFile(file)) throw new IllegalArgumentException("No such file: " + path);

        return file;
    }

    /// @param `file` relative to the project @param `root`, joined with '/'.
    static String relative(final Path root, final Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    static AssetAccessor<? extends Armature> requireArmature(final AkytheraProject project, final String id) {
        return project.listAllArmatureAccessors()
            .filter(accessor -> accessor.identifier().toString().equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No armature " + id + " in the project"));
    }

    static List<String> armatureIds(final AkytheraProject project) {
        return project.listAllArmatureAccessors().map(accessor -> accessor.identifier().toString()).sorted().toList();
    }

    static List<String> skeletalMeshIds(final AkytheraProject project) {
        return project.listAllMeshAccessors()
            .filter(accessor -> accessor.checkType(AssetAccessor.Type.SKELETAL_MESH))
            .map(accessor -> accessor.identifier().toString())
            .sorted()
            .toList();
    }

    /// Every [Model3DViewer] on the top editor screen, then in the open modal.
    static List<Model3DViewer> findViewers() {
        List<Model3DViewer> viewers = new ArrayList<>();
        Screen top = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();

        if (top != null) viewers.addAll(findWidgets(top, Model3DViewer.class));
        if (ModalManager.getInstance().getModal() instanceof ContainerEventHandler modal) viewers.addAll(findWidgets(modal, Model3DViewer.class));

        return viewers;
    }

    /// The type of the modal @param `viewer` sits in, or else of the top editor screen.
    private static String ownerOf(final Model3DViewer viewer) {
        AbstractWidget modal = ModalManager.getInstance().getModal();
        Screen top = AkytheraEditorLayoutManager.getInstance().getRecentEditorScreen();

        return modal instanceof ContainerEventHandler container && findWidgets(container, Model3DViewer.class).contains(viewer)
            ? modal.getClass().getSimpleName()
            : top == null ? "" : top.getClass().getSimpleName();
    }

    /// Writes the project settings as File > Save of the main editor screen does. False when the editor layout is not
    /// open.
    static boolean saveProjectSettings(final AkytheraProject project) {
        MainEditorScreen mainScreen = AkytheraEditorLayoutManager.getInstance().getMainScreen();

        if (mainScreen == null) return false;

        project.saveProjectMetadata(mainScreen.explorerPanel.collectFolderStates(), mainScreen.explorerPanel.getFocusedPath());

        return true;
    }

    static Identifier requireId(final String id) {
        Identifier parsed = Identifier.tryParse(id);

        if (parsed == null) throw new IllegalArgumentException("Not a valid id: " + id);

        return parsed;
    }

    /// Every widget of @param `type` under @param `root`, including the areas of an [AdjustablePanelManager]
    /// and the inactive tabs of a [TabPanel], which their `children()` leave out.
    static <T> List<T> findWidgets(final ContainerEventHandler root, final Class<T> type) {
        List<T> found = new ArrayList<>();
        Set<GuiEventListener> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<GuiEventListener> pending = new ArrayDeque<>(root.children());

        while (!pending.isEmpty()) {
            GuiEventListener listener = pending.poll();

            if (!visited.add(listener)) continue;
            if (type.isInstance(listener)) found.add(type.cast(listener));

            switch (listener) {
                case AdjustablePanelManager panelManager -> pending.addAll(panelManager.getAreas());
                case TabPanel tabPanel -> {
                    for (int i = 0; i < tabPanel.getTabCount(); i++) pending.add(tabPanel.getTabContent(i));
                }
                case ContainerEventHandler container -> pending.addAll(container.children());
                default -> {}
            }
        }

        return found;
    }
}
