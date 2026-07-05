package com.yesman.akytheralabs.editor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a mutable holder field that should be rendered into an editor
/// [com.yesman.akythera.client.gui.widget.WidgetTable] by [EditorWidgetFactory].
///
/// Field types the factory understands today: `float` / `int` (numeric box),
/// `boolean` (checkbox), `String` (edit box), `enum` (ComboBox), and
/// [com.yesman.akythera.api.asset.AssetAccessor] (a search PopupBox routed by the
/// accessor's runtime type). Other types fall back to a read-only display.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EditorEditable {
    /// Optional override for the displayed label. Falls back to the field name.
    String label() default "";

    /// For numeric types only; ignored otherwise.
    float min() default -Float.MAX_VALUE;

    float max() default Float.MAX_VALUE;

    /// Display order — ascending. Equal values are tie-broken by declaration
    /// order within a class.
    int order() default 0;
}