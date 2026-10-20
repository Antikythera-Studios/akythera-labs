package com.yesman.akytheralabs.akythera.widget;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.data.modularize.AkytheraScriptFunctions;
import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.data.modularize.node.SequenceNode;
import com.yesman.akythera.api.data.serializable.LocalizedMessage;
import com.yesman.akythera.client.api.gui.AkytheraHUD;
import com.yesman.akythera.client.api.gui.AkytheraScreen;
import com.yesman.akythera.core.blueprint.widget.DataDrivenWidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.SourceCodeWidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetBlueprint;
import com.yesman.akythera.core.blueprint.widget.WidgetUsage;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akytheralabs.akythera.animation.ScriptGraphCompiler;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintMembers;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintParentRef;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintScriptData;
import com.yesman.akytheralabs.akythera.blueprint.BlueprintSignature;
import com.yesman.akytheralabs.akythera.blueprint.ComponentPaths;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentAttachableHolder;
import com.yesman.akytheralabs.akythera.entitypatch.component.ComponentDataHolder;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.EditorReference;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import com.yesman.akytheralabs.gui.panel.script.BlueprintEditorSession;
import com.yesman.akytheralabs.gui.widget.script.Compilation;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.stream.Stream;

/// Modifiable data holder for a [WidgetBlueprint] (registry [AkytheraRegistries.DataPack#WIDGET_KEY]).
///
/// The blueprint is the root [ComponentAttachableHolder] of its widget tree. Its [WidgetUsage] is not stored:
/// it is what [#parent]'s constructor builds, see [#usageOf].
///
/// [#parent] is either a container class, lowered into a [DataDrivenWidgetBlueprint], or a source code script,
/// lowered into a [SourceCodeWidgetBlueprint] that carries nothing but the title.
public final class WidgetBlueprintHolder extends AbstractDataHolder<WidgetBlueprint> implements ComponentAttachableHolder {
    private static final int DEFAULT_CANVAS_WIDTH = 640;
    private static final int DEFAULT_CANVAS_HEIGHT = 360;

    public static final Codec<WidgetBlueprintHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            AkytheraRegistries.Frozen.SCRIPT_FUNCTION.byNameCodec().optionalFieldOf("parent").forGetter(h -> Optional.of(h.parent.ctor())),
            AkytheraRegistries.Frozen.SCRIPT_TYPE.byNameCodec().optionalFieldOf("source_code_script")
                .forGetter(h -> h.parent instanceof BlueprintParentRef.SourceCodeScript sourceCode ? Optional.of(sourceCode.script()) : Optional.empty()),
            LocalizedMessage.CODEC.optionalFieldOf("title", LocalizedMessage.EMPTY).forGetter(h -> h.title),
            ComponentAttachableHolder.componentsField(() -> WidgetComponentHolders.REGISTRY).forGetter(ComponentAttachableHolder::componentsOf),
            BlueprintScriptData.MAP_CODEC.forGetter(h -> h.script),
            Codec.INT.optionalFieldOf("canvas_width", DEFAULT_CANVAS_WIDTH).forGetter(h -> h.canvasWidth),
            Codec.INT.optionalFieldOf("canvas_height", DEFAULT_CANVAS_HEIGHT).forGetter(h -> h.canvasHeight)
        ).apply(instance, (ctor, sourceCodeScript, title, components, script, canvasWidth, canvasHeight) -> {
            WidgetBlueprintHolder holder = new WidgetBlueprintHolder();
            ScriptFunction parentCtor = ctor.orElseGet(() -> defaultCtor(WidgetUsage.SCREEN));
            holder.parent = sourceCodeScript
                .<BlueprintParentRef>map(type -> new BlueprintParentRef.SourceCodeScript(parentCtor, type))
                .orElseGet(() -> new BlueprintParentRef.ParentClass(parentCtor));
            holder.title = title;
            ComponentAttachableHolder.applyComponents(holder, components);
            holder.script = script;
            holder.canvasWidth = canvasWidth;
            holder.canvasHeight = canvasHeight;
            return holder;
        })
    );

    /// What the engine builds the container with. Its constructor's return type is the container's class, the
    /// script owner of every canvas, and decides the [WidgetUsage].
    private BlueprintParentRef parent = new BlueprintParentRef.ParentClass(defaultCtor(WidgetUsage.SCREEN));

    /// The screen's title, which [AkytheraScriptFunctions#SCREEN_TITLE] reads.
    @EditorEditable(label = LangKeys.GUI_WIDGET_SCREEN_ROW_TITLE, order = 0, visibleWhen = "isScreen")
    private LocalizedMessage title = LocalizedMessage.EMPTY;

    /// The widgets by the name the engine attaches them under, in the order they are laid out.
    private final SequencedMap<String, ComponentDataHolder<?>> components = ComponentAttachableHolder.newComponentMap();

    private BlueprintScriptData script = new BlueprintScriptData();

    /// The canvas frame's size, in GUI pixels: the window for a screen or a HUD, the placed rect for a widget.
    /// Editor-only.
    private int canvasWidth = DEFAULT_CANVAS_WIDTH;
    private int canvasHeight = DEFAULT_CANVAS_HEIGHT;

    /// The built-in constructor of `usage`: `akythera:screen_ctor`, `akythera:widget_ctor` or `akythera:hud_ctor`.
    ///
    /// ⚠ Requires the script-function registry to be filled (`Akythera.onModInitialized`).
    public static ScriptFunction defaultCtor(final WidgetUsage usage) {
        return switch (usage) {
            case SCREEN -> AkytheraScriptFunctions.SCREEN_CONSTRUCTOR.value();
            case WIDGET -> AkytheraScriptFunctions.WIDGET_CONSTRUCTOR.value();
            case HUD -> AkytheraScriptFunctions.HUD_CONSTRUCTOR.value();
        };
    }

    /// The usage of the container `ctor` builds.
    public static WidgetUsage usageOf(final ScriptFunction ctor) {
        Class<?> container = ctor.functionSignature().returnType();

        if (AkytheraHUD.class.isAssignableFrom(container)) return WidgetUsage.HUD;
        if (AkytheraScreen.class.isAssignableFrom(container)) return WidgetUsage.SCREEN;

        return WidgetUsage.WIDGET;
    }

    public BlueprintParentRef getParent() {
        return parent;
    }

    public void setParent(final BlueprintParentRef parent) {
        this.parent = parent;
    }

    public WidgetUsage usage() {
        return usageOf(parent.ctor());
    }

    private boolean isScreen() {
        return usage() == WidgetUsage.SCREEN;
    }

    /// The container's class: what `self` is on every canvas.
    public Class<?> ownerType() {
        return parent.ctor().functionSignature().returnType();
    }

    /// The members [#ownerType] lays the editor out with.
    public BlueprintLayout layout() {
        return Objects.requireNonNull(ScriptModularizeHelper.blueprintLayoutOf(ownerType()));
    }

    public int getCanvasWidth() {
        return canvasWidth;
    }

    public int getCanvasHeight() {
        return canvasHeight;
    }

    public void setCanvasSize(final int width, final int height) {
        canvasWidth = width;
        canvasHeight = height;
    }

    public BlueprintScriptData getScript() {
        return script;
    }

    /// The paths of the widgets in the tree that take input, which a HUD refuses.
    public List<String> inputWidgetPaths() {
        return ComponentPaths.known(this).keySet().stream()
            .filter(path -> ComponentPaths.at(this, path) instanceof WidgetComponentHolder<?> widget
                && !(widget instanceof ComponentAttachableHolder)
                && !(widget instanceof WidgetBlueprintRefHolder)
                && widget.makeObject().acceptsInput())
            .toList();
    }

    @Override
    public List<Class<?>> attachableTypes() {
        return List.of(WidgetComponentHolder.class);
    }

    @Override
    public SequencedMap<String, ComponentDataHolder<?>> getComponents() {
        return components;
    }

    @Override
    public void readFrom(final WidgetBlueprint object) {
        title = object.title();

        switch (object) {
            // The runtime blueprint carries no on-canvas layout, so only the values round-trip.
            case DataDrivenWidgetBlueprint dataDriven -> {
                parent = new BlueprintParentRef.ParentClass(dataDriven.ctor());
                ComponentAttachableHolder.readComponents(this, WidgetComponentHolders.REGISTRY, dataDriven.components());
                script = new BlueprintScriptData(
                    ScriptGraphData.EMPTY, Map.of(), List.of(), dataDriven.body().properties(),
                    dataDriven.body().variables().stream().map(BlueprintScriptData.Variable::of).toList());
            }
            case SourceCodeWidgetBlueprint sourceCode -> parent = new BlueprintParentRef.SourceCodeScript(sourceCode.ctor(), sourceCode.script());
        }
    }

    @Override
    public WidgetBlueprint makeObject() {
        return switch (parent) {
            case BlueprintParentRef.ParentClass parentClass -> {
                Map<String, FlowNode> events = new LinkedHashMap<>();
                Map<String, FlowNode> bindings = new LinkedHashMap<>();
                BlueprintMembers members = script.members(layout());

                // A graph whose entry leads nowhere is left out
                for (WidgetGraphSite site : widgetGraphSites()) {
                    FlowNode flow = ScriptGraphCompiler.compile(
                        site.graph(), ownerType(), BlueprintScriptData.entryOf(site.signature().name()), members.with(site.widgetSignatures()), site.signature().name());

                    if (flow instanceof SequenceNode sequence && sequence.executionBlocks().isEmpty()) continue;

                    (site.signature().returnType() == null ? events : bindings).put(site.key(), flow);
                }

                yield new DataDrivenWidgetBlueprint(
                    usage(), parentClass.ctor(), title, WidgetComponentHolder.lowerWidgets(components),
                    script.compile(layout(), ownerType()), events, bindings);
            }
            case BlueprintParentRef.SourceCodeScript sourceCode -> new SourceCodeWidgetBlueprint(usage(), sourceCode.ctor(), title, sourceCode.script());
        };
    }

    /// Also validates the graph of every widget event and binding, under its [WidgetGraphSite#key]. A source
    /// code script lowers nothing from the canvases, so it checks none.
    @Override
    public void compile(final Compilation compilation) {
        if (parent instanceof BlueprintParentRef.SourceCodeScript) return;

        BlueprintEditorSession.compileCanvases(compilation, script, layout(), ownerType());

        BlueprintMembers members = script.members(layout());

        for (WidgetGraphSite site : widgetGraphSites()) {
            compilation.canvas(site.key(),
                BlueprintEditorSession.canvasOf(ownerType(), members.with(site.widgetSignatures()), site.signature().name(), site.graph()),
                Component.literal(site.key()));
        }

        List<String> unknownPaths = ComponentPaths.unknown(this, script);

        if (!unknownPaths.isEmpty()) compilation.fail(Component.literal("No widget at " + String.join(", ", unknownPaths)));

        compilation.unresolved(script.failedNodes());
        compilation.build(this::makeObject);
    }

    /// Every graph the widgets of the tree hold for one of their events or bindings, at every depth, in tree order.
    public List<WidgetGraphSite> widgetGraphSites() {
        List<WidgetGraphSite> sites = new ArrayList<>();

        collectSites(components, "", sites);

        return sites;
    }

    /// Adds the [WidgetGraphSite]s of the widgets under `components` to `sites`.
    ///
    /// @param prefix the path of `components`' host followed by [ComponentAttachable#PATH_SEPARATOR], or empty at
    ///               the root
    private static void collectSites(final SequencedMap<String, ComponentDataHolder<?>> components, final String prefix, final List<WidgetGraphSite> sites) {
        components.forEach((name, component) -> {
            if (!(component instanceof WidgetComponentHolder<?> widget)) return;

            String path = prefix + name;
            Map<String, BlueprintSignature> signatures = widget.graphSignatures();

            widget.getGraphs().map().forEach((site, graph) -> {
                BlueprintSignature signature = signatures.get(site);

                if (signature != null) sites.add(new WidgetGraphSite(path + "." + site, graph, signature, signatures));
            });

            if (widget instanceof ComponentAttachableHolder host) collectSites(host.getComponents(), path + ComponentAttachable.PATH_SEPARATOR, sites);
        });
    }

    /// Every blueprint the widgets place, plus every asset the canvases' nodes bind.
    @Override
    public Stream<EditorReference> referencedReferences() {
        return Stream.concat(ComponentAttachableHolder.flatten(this).flatMap(DataHolder::referencedReferences), script.referencedReferences());
    }

    /// The first failure the decode guard salvaged: a defaults-fallback or unknown-type widget, or an unresolved
    /// node kept on a canvas.
    @Override
    public @Nullable Exception getNestedFailureException() {
        Exception component = AbstractDataHolder.firstNestedFailure(components.values());

        return component != null ? component : script.firstFailure();
    }

    /// A widget's graph for one of its events or bindings.
    ///
    /// @param key              where the blueprint keeps it: `<path>.<name>`
    /// @param signature        what it implements
    /// @param widgetSignatures the signatures of every event and binding of its widget, what its canvas knows
    public record WidgetGraphSite(String key, ScriptGraphData graph, BlueprintSignature signature, Map<String, BlueprintSignature> widgetSignatures) {
    }
}
