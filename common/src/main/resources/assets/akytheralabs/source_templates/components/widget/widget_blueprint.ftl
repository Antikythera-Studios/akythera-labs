<#--
  Constructor expression for an "akythera:widget_blueprint" ComponentBlueprint (WidgetBlueprintComponentBlueprint).
  DTO (WidgetBlueprintRefHolder#getDTO): { layout, order, widget, enabled }
  Every value but `enabled` is a Java expression already. Emits a single inline Java expression (no surrounding
  whitespace).
--><#t>
new WidgetBlueprintComponentBlueprint(${layout}, ${order}, ${widget}, ${enabled?c})<#t>