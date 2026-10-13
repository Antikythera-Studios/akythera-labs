package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.yesman.akythera.api.component.ComponentAttachable;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.UniqueComponent;
import com.yesman.akythera.api.component.impl.ArmatureComponent;
import com.yesman.akythera.core.blueprint.ComponentBlueprint;
import com.yesman.akytheralabs.SourceTemplateEngine;
import com.yesman.akytheralabs.akythera.animation.notify.AnimationNotifyHolder;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.ComponentHolderRegistry;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.gui.panel.components.ComponentEditorPanel;
import com.yesman.akytheralabs.gui.widget.script.ScriptGraphData;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/// Base of every patch component holder — the editor twin of one [ComponentBlueprint] type, as
/// registered in a [ComponentHolderRegistry] and stored under a host's
/// [ComponentAttachableHolder#getComponents].
///
/// On top of [AbstractDataHolder] it carries what a *component* specifically owes the editor: the
/// runtime [PatchComponent] its blueprint ends up building ([#generateEventually]) and the
/// `File > Export > As Source Code` rendering ([#sourceTemplate] + [DTOProvider#getDTO]).
///
/// Source export works as it does for [AnimationNotifyHolder]: each holder names its
/// `components/<type>.ftl` fragment under [SourceTemplateEngine]'s template root and supplies the
/// token map that fragment splices in. A host's children render through the host's own fragment —
/// `components/armature.ftl` splices in the sockets attached to it.
public abstract class ComponentDataHolder<T extends ComponentBlueprint<?, ?>> extends AbstractDataHolder<T>
    implements DTOProvider
{
    /// Whether the built component starts switched on, see [ComponentBlueprint#enabled]. Persisted for
    /// every registered type by [ComponentHolderRegistry#register].
    private boolean enabled = true;

    /// The class this component eventually instantiates, e.g. [ArmatureComponentHolder] ->
    /// [ArmatureComponent].
    public abstract Class<? extends PatchComponent> generateEventually();

    /// Editor mirror of [PatchComponent#mergeKey]: what an inherited component is paired with a local
    /// one by. Null pairs on the attach name.
    public @Nullable Object mergeKey() {
        return UniqueComponent.class.isAssignableFrom(generateEventually()) ? generateEventually() : null;
    }

    /// The name this component derives from its own state, or null — the default — to leave the name
    /// to the author. A fixed-name component's row is read-only in [ComponentEditorPanel], and is
    /// re-keyed in its host whenever the state behind the name changes.
    public @Nullable String fixedName() {
        return null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /// The node canvases this component owns, empty for one that owns none.
    public Stream<ScriptGraphData> canvases() {
        return Stream.of();
    }

    /// Replaces every canvas this component owns with `mapper`'s answer. A component that owns none
    /// takes nothing.
    public void mapCanvases(final UnaryOperator<ScriptGraphData> mapper) {
    }

    /// Classpath-relative fragment template that renders this component's constructor expression,
    /// e.g. `components/armature.ftl`. Null — the default — means the type has no source support:
    /// [#sourcesOf] emits a TODO marker in its place rather than aborting the export.
    public @Nullable String sourceTemplate() {
        return null;
    }

    /// The tokens [#sourceTemplate] splices in, besides the ones [#sourcesOf] adds. Empty for a
    /// component that renders nothing else.
    @Override
    public Map<String, Object> getDTO() {
        return Map.of();
    }

    /// The components as `{ name, source }` rows in stored order, each `source` being the Java
    /// constructor expression the generated `componentsToAttach()` collects. The name rides along
    /// because the engine keys a [ComponentAttachable]'s components by it, so the templates emit
    /// `Map` entries.
    ///
    /// Every template receives the `enabled` and `hardcoded` tokens, and a [ComponentAttachableHolder]'s
    /// template its children's rows as `components`. `hardcoded` is true for a hardcoded script class,
    /// whose components never pass through a codec, and false for a datagen provider.
    public static List<Map<String, String>> sourcesOf(Map<String, ? extends ComponentDataHolder<?>> components, boolean hardcoded) {
        return components.entrySet().stream()
            .map(e -> {
                String template = e.getValue().sourceTemplate();

                Map<String, Object> dto = new HashMap<>(e.getValue().getDTO());
                dto.put("enabled", e.getValue().isEnabled());
                dto.put("hardcoded", hardcoded);

                if (e.getValue() instanceof ComponentAttachableHolder host) {
                    dto.put("components", sourcesOf(host.getComponents(), hardcoded));
                }

                return Map.of(
                    "name", e.getKey(),
                    "source", template == null
                        ? "null /* TODO: component '" + e.getKey() + "' has no components/*.ftl source template */"
                        : SourceTemplateEngine.render(template, dto)
                );
            })
            .toList();
    }
}