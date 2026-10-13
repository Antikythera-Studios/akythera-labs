<#--
  Constructor expression for an "akythera:color_picker" ComponentBlueprint (ColorPickerBlueprint).
  DTO (ColorPickerHolder#getDTO): { layout, order, color, enabled }
  Every value but `enabled` is a Java expression already. Emits a single inline Java expression (no surrounding
  whitespace).
--><#t>
new ColorPickerBlueprint(${layout}, ${order}, ${color}, ${enabled?c})<#t>