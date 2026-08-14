<#--
  Constructor expression for an "akythera:attribute_modifiers" ComponentBlueprint (PatchAttributeModifiers).
  DTO (PatchAttributeModifiersHolder#getDTO):
    { modifiers: [ { attribute, id, amount, operation } ] }
  The attribute resolves from the builtin registry (matching AttributeModifierEntry.CODEC); amount
  arrives pre-rendered as a Java double literal, operation as the enum constant name. Spliced as a
  List.of(...) element at indent 5; continuation lines bake absolute indents.
--><#t>
<#if modifiers?size == 0>new PatchAttributeModifiers(List.of())<#t>
<#else>new PatchAttributeModifiers(List.of(
<#list modifiers as m>
                        new AttributeModifierEntry(
                            BuiltInRegistries.ATTRIBUTE.getOrThrow(ResourceKey.create(Registries.ATTRIBUTE, Identifier.parse("${m.attribute}"))),
                            new AttributeModifier(Identifier.parse("${m.id}"), ${m.amount}, AttributeModifier.Operation.${m.operation})
                        )<#sep>,</#sep>
</#list>
                    ))<#t>
</#if>
