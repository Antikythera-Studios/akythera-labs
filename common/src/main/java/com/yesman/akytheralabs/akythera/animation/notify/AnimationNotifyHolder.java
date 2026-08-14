package com.yesman.akytheralabs.akythera.animation.notify;

import com.yesman.akythera.api.animation.notify.AnimationNotify;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.SourceTemplateEngine;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.DataHolder;
import org.jspecify.annotations.Nullable;

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
    private @Nullable Exception loadingException;

    @Override
    public @Nullable Exception getDeserializationFailureException() {
        return loadingException;
    }

    @Override
    public void markFailed(Exception exception) {
        loadingException = exception;
    }

    /// The logical side, read without allocating an engine instance through [#makeObject].
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