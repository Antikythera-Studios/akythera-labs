package com.yesman.akytheralabs.dataholder.animation.notify;

import com.yesman.akythera.api.animation.notify.AnimationNotify;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.SourceTemplateEngine;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.DataHolder;

/// Editor-side mirror for an immutable [AnimationNotify]. Subclasses expose
/// mutable fields annotated with
/// [com.yesman.akytheralabs.editor.EditorEditable] so the notify-details
/// panel can render the right widgets, then [#makeObject] reconstructs the
/// engine instance for export.
///
/// As a [DTOProvider] each subclass also maps to a `notify/<type>.ftl` fragment
/// (returned by [#sourceTemplate]); [#toSourceCode] renders that fragment against
/// [#getDTO] to produce the notify's Java constructor expression for code export.
public abstract class AnimationNotifyHolder<T extends AnimationNotify<?>> implements DataHolder<T>, DTOProvider {
    /// Direct read of the logical side without allocating a fresh engine
    /// instance via [#makeObject]. Used by track-row colouring which runs
    /// every render frame.
    public abstract LogicalSide logicalSide();

    /// Returns the original class's simple name
    public abstract String notifyName();

    /// Classpath-relative fragment template that renders this notify's constructor
    /// expression, e.g. `notify/log.ftl`.
    public abstract String sourceTemplate();

    /// The Java constructor expression for this notify (e.g. `new LogNotify(LogicalSide.BOTH)`),
    /// produced by rendering [#sourceTemplate] against [#getDTO].
    public final String toSourceCode() {
        return SourceTemplateEngine.render(sourceTemplate(), getDTO());
    }
}