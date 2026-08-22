<#--
  minecraft:player_skin_model (playerSkin) -> PlayerModelType
  Runtime: a skin that has not resolved yet (null) reads as WIDE.
--><#t>
(${params[0]} == null ? PlayerModelType.WIDE : ${params[0]}.model())<#t>