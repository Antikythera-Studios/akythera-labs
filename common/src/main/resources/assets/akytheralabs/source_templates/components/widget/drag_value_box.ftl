<#--
  Constructor expression for an "akythera:drag_value_box" ComponentBlueprint (DragValueBoxBlueprint).
  DTO (DragValueBoxHolder#getDTO): { layout, order, min, max, message, theme, enabled }
  Every value but `enabled` is a Java expression already. Emits a single inline Java expression (no surrounding
  whitespace).
--><#t>
new DragValueBoxBlueprint(${layout}, ${order}, ${min}, ${max}, ${message}, ${theme}, ${enabled?c})<#t>