<#--
  Constructor expression for an "akythera:combo_box" ComponentBlueprint (ComboBoxBlueprint).
  DTO (ComboBoxHolder#getDTO): { layout, order, title, items, max_rows, searchable, clearable, theme, enabled }
  Every value but `enabled` is a Java expression already. Emits a single inline Java expression (no surrounding
  whitespace).
--><#t>
new ComboBoxBlueprint(${layout}, ${order}, ${title}, ${items}, ${max_rows}, ${searchable}, ${clearable}, ${theme}, ${enabled?c})<#t>