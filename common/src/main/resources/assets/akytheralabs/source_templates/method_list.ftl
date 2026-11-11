<#--
  Writes the methods of a class generated from a blueprint, a blank line ahead of each, and the field a method
  reads ahead of it.

  @param rows the method rows, each { annotations, declaration, body } and an optional `field` declaration, as
              BlueprintSourceWriter#methods builds them
--><#macro methodList rows>
<#list rows as method>

<#if method.field??>
    ${method.field}

</#if>
<#list method.annotations as annotation>
    ${annotation}
</#list>
    ${method.declaration} {
<#list method.body as line>
        ${line}
</#list>
    }
</#list>
</#macro>
