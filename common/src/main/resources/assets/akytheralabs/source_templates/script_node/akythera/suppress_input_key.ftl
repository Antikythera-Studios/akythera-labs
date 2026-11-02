<#--
  akythera:suppress_input_key (action, suppress) -> void; client implementation
  Renders an if statement; the flow compiler appends the closing semicolon.
--><#t>
if (${params[0]} != null && Minecraft.getInstance().isSameThread()) InputManager.getInstance().suppressInputKey(${params[0]}, Boolean.TRUE.equals(${params[1]}))<#t>