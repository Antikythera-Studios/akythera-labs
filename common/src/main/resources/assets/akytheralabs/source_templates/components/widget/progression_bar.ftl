<#--
  Constructor expression for an "akythera:progression_bar" ComponentBlueprint (ProgressionBarBlueprint).
  DTO (ProgressionBarHolder#getDTO): { layout, order, fill_style, fill_direction, highlight_thickness, flash_time, lerp_time, fixed_lerp_time, filled_image, empty_image, lerp_image, enabled }
  Every value but `enabled` is a Java expression already. Emits a single inline Java expression (no surrounding
  whitespace).
--><#t>
new ProgressionBarBlueprint(${layout}, ${order}, ${fill_style}, ${fill_direction}, ${highlight_thickness}, ${flash_time}, ${lerp_time}, ${fixed_lerp_time}, ${filled_image}, ${empty_image}, ${lerp_image}, ${enabled?c})<#t>