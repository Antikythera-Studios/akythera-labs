<#-- akythera:random_in_bb (entityPatch) -> Vec3 --><#t>
${params[0]}.getOriginal().position().add(<#t>
-${params[0]}.getOriginal().getDimensions(${params[0]}.getOriginal().getPose()).width() * 0.5F + ${params[0]}.getOriginal().getRandom().nextFloat() * ${params[0]}.getOriginal().getDimensions(${params[0]}.getOriginal().getPose()).width() * 1.2F, <#t>
-${params[0]}.getOriginal().getDimensions(${params[0]}.getOriginal().getPose()).height() * 0.5F + ${params[0]}.getOriginal().getRandom().nextFloat() * ${params[0]}.getOriginal().getDimensions(${params[0]}.getOriginal().getPose()).height() * 1.2F + ${params[0]}.getOriginal().getDimensions(${params[0]}.getOriginal().getPose()).height() * 0.2F, <#t>
-${params[0]}.getOriginal().getDimensions(${params[0]}.getOriginal().getPose()).width() * 0.5F + ${params[0]}.getOriginal().getRandom().nextFloat() * ${params[0]}.getOriginal().getDimensions(${params[0]}.getOriginal().getPose()).width() * 1.2F<#t>
)<#t>