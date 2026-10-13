<#--
  Constructor expression for an "akythera:horizontal_container" ComponentBlueprint (LinearContainerBlueprint).
  DTO (LinearContainerHolder#getDTO): { vertical, layout, order, padding, components, enabled }
  Every value but `components` and `enabled` is a Java expression already. The attached widgets ride along as
  the blueprint's child map, each rendered by its own components/widget/*.ftl. Emits a single inline Java
  expression (no surrounding whitespace).
--><#t>
<#import "../../component_map.ftl" as tpl><#t>
new LinearContainerBlueprint(${vertical}, ${layout}, ${order}, ${padding}, <@tpl.componentMap components "    "/>, ${enabled?c})<#t>