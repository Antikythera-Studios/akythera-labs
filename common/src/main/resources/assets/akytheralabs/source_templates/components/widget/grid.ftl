<#--
  Constructor expression for an "akythera:grid" ComponentBlueprint (GridBlueprint).
  DTO (GridHolder#getDTO): { layout, order, cell_width, cell_height, padding, components, enabled }
  Every value but `components` and `enabled` is a Java expression already. The attached widgets ride along as
  the blueprint's child map, each rendered by its own components/widget/*.ftl. Emits a single inline Java
  expression (no surrounding whitespace).
--><#t>
<#import "../../component_map.ftl" as tpl><#t>
new GridBlueprint(${layout}, ${order}, ${cell_width}, ${cell_height}, ${padding}, <@tpl.componentMap components "    "/>, ${enabled?c})<#t>