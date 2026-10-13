<#--
  Constructor expression for an "akythera:text_area" ComponentBlueprint (TextAreaBlueprint).
  DTO (TextAreaHolder#getDTO): { layout, order, text, editable, enabled }
  Every value but `enabled` is a Java expression already. Emits a single inline Java expression (no surrounding
  whitespace).
--><#t>
new TextAreaBlueprint(${layout}, ${order}, ${text}, ${editable}, ${enabled?c})<#t>