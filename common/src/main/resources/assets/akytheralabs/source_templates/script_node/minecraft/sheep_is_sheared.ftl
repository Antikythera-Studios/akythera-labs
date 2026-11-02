<#-- minecraft:sheep_is_sheared (entityPatch) -> boolean --><#t>
(${params[0]} != null && ${params[0]}.getOriginal() instanceof Sheep && ((Sheep) ${params[0]}.getOriginal()).isSheared())<#t>