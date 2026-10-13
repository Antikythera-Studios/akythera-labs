<#--
  Constructor expression for an "akythera:button" ComponentBlueprint (ButtonBlueprint).
  DTO (ButtonHolder#getDTO): { layout, order, message, theme, normal_image, focused_image, disabled_image, hover_sound, press_sound, enabled }
  Every value but `enabled` is a Java expression already. Emits a single inline Java expression (no surrounding
  whitespace).
--><#t>
new ButtonBlueprint(${layout}, ${order}, ${message}, ${theme}, ${normal_image}, ${focused_image}, ${disabled_image}, ${hover_sound}, ${press_sound}, ${enabled?c})<#t>