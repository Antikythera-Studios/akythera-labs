<#--
  Constructor expression for an "akythera:weapon_stance_variation_adapter" ComponentBlueprint
  (WeaponStanceVariationAdapter).
  DTO (WeaponStanceVariationAdapterHolder#getDTO):
    { stance, condition, components: [ { name, source } ], enabled }
  Spliced as a component-map value at indent 5; continuation lines bake absolute indents.
--><#t>
<#import "../component_map.ftl" as tpl><#t>
new WeaponStanceVariationAdapter(
                        WeaponStance.ENUM_MANAGER.getOrThrow(Identifier.parse("${stance}")),
${condition},
                        <@tpl.componentMap components "                            "/>,
                        ${enabled?c}
                    )<#t>
