<#--
  Constructor expression for an "akythera:check_box" ComponentBlueprint (CheckBoxBlueprint).
  DTO (CheckBoxHolder#getDTO): { layout, order, message, theme, enabled }
  Every value but `enabled` is a Java expression already. Emits a single inline Java expression (no surrounding
  whitespace).
--><#t>
new CheckBoxBlueprint(${layout}, ${order}, ${message}, ${theme}, ${enabled?c})<#t>