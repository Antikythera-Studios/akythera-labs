<#--
  Constructor expression for an "akythera:image" ComponentBlueprint (ImageBlueprint).
  DTO (ImageHolder#getDTO): { layout, order, image, enabled }
  Every value but `enabled` is a Java expression already. Emits a single inline Java expression (no surrounding
  whitespace).
--><#t>
new ImageBlueprint(${layout}, ${order}, ${image}, ${enabled?c})<#t>