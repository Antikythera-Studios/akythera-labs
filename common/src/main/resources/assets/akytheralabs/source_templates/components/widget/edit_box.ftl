<#--
  Constructor expression for an "akythera:edit_box" ComponentBlueprint (EditBoxBlueprint).
  DTO (EditBoxHolder#getDTO): { layout, order, placeholder, theme, enabled }
  Every value but `enabled` is a Java expression already. Emits a single inline Java expression (no surrounding
  whitespace).
--><#t>
new EditBoxBlueprint(${layout}, ${order}, ${placeholder}, ${theme}, ${enabled?c})<#t>