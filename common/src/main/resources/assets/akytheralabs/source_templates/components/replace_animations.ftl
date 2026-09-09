<#--
  Constructor expression for an "akythera:replace_animations" ComponentBlueprint (ReplaceAnimations).
  DTO (ReplaceAnimationsHolder#getDTO):
    { replaces: [ { anim_interface, animation, registry } ], reset_animations, mirror_animations }
  The interface key resolves through its data-block manager by id (registered during mod init, so
  available at datagen); the animation stays a plain ResourceKey into the registry `registry` names,
  matching ReplaceEntry.CODEC. Spliced as a List.of(...) element at indent 5; continuation lines bake
  absolute indents.
--><#t>
<#if replaces?size == 0>new ReplaceAnimations(List.of(), ${reset_animations?c}, ${mirror_animations?c})<#t>
<#else>new ReplaceAnimations(List.of(
<#list replaces as r>
                        new ReplaceAnimations.ReplaceEntry(
                            AnimationInterfaceKey.ENUM_MANAGER.getOrThrow(Identifier.parse("${r.anim_interface}")),
                            ResourceKey.create(AkytheraRegistries.DataPack.${r.registry}, Identifier.parse("${r.animation}"))
                        )<#sep>,</#sep>
</#list>
                    ), ${reset_animations?c}, ${mirror_animations?c})<#t>
</#if>
