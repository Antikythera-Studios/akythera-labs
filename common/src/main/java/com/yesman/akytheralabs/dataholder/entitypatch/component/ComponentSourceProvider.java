package com.yesman.akytheralabs.dataholder.entitypatch.component;

import com.yesman.akytheralabs.SourceTemplateEngine;
import com.yesman.akytheralabs.dataholder.DTOProvider;

/// A component editor holder that can render its runtime
/// [com.yesman.akythera.core.blueprint.ComponentBlueprint] as a Java constructor expression for
/// `File > Export > As Source Code`.
///
/// Mirrors [com.yesman.akytheralabs.dataholder.animation.notify.AnimationNotifyHolder]: each holder
/// names its `components/<type>.ftl` fragment (under
/// [SourceTemplateEngine]'s template root) and supplies, via [DTOProvider#getDTO], the token map that
/// fragment splices in. Unlike node-graph flows (compiled by [com.yesman.akytheralabs.ScriptNodeSourceCompiler]),
/// the entity patch's attached components are a plain list, so each renders straight from its holder.
public interface ComponentSourceProvider extends DTOProvider {
    /// Classpath-relative fragment template that renders this component's constructor expression,
    /// e.g. `components/armature.ftl`.
    String sourceTemplate();

    /// The Java constructor expression for this component's runtime blueprint (the element the
    /// generated `componentsToAttach()` collects), produced by rendering [#sourceTemplate] against
    /// [#getDTO].
    default String toSourceCode() {
        return SourceTemplateEngine.render(sourceTemplate(), getDTO());
    }
}
