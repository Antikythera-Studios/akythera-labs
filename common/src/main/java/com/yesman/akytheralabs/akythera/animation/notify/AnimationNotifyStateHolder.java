package com.yesman.akytheralabs.akythera.animation.notify;

import com.yesman.akythera.api.animation.notify.AnimationNotifyState;
import com.yesman.akythera.core.util.LogicalSide;
import com.yesman.akytheralabs.SourceTemplateEngine;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.DataHolder;
import org.jspecify.annotations.Nullable;

/// Editor-side mirror for an immutable [AnimationNotifyState]. Counterpart of
/// [AnimationNotifyHolder] for windowed notifies.
///
/// As a [DTOProvider] each subclass maps to a `notify_state/<type>.ftl` fragment
/// (returned by [#sourceTemplate]); [#toSourceCode] renders it against [#getDTO]
/// to produce the notify-state's Java constructor expression for code export.
public abstract class AnimationNotifyStateHolder<T extends AnimationNotifyState<?>> implements DataHolder<T>, DTOProvider {
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

    /// Classpath-relative fragment template that renders this notify-state's constructor
    /// expression, e.g. `notify_state/log.ftl`.
    public abstract String sourceTemplate();

    /// The Java constructor expression for this notify-state, produced by rendering
    /// [#sourceTemplate] against [#getDTO].
    public final String toSourceCode() {
        return SourceTemplateEngine.render(sourceTemplate(), getDTO());
    }
}