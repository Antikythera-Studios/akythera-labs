<#--
  Writes the methods of a class generated from a blueprint, a blank line ahead of each.

  @param rows the method rows, each { annotations, declaration, body }, as BlueprintSourceWriter#methods
              builds them
--><#macro methodList rows>
<#list rows as method>

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
