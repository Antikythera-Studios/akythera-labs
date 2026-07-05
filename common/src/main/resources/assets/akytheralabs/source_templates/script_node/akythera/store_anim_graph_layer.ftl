<#--
  akythera:store_anim_graph_layer (layerMap, driver) -> void
  Runtime: layerMap.put("anim_graph", new AnimationLayer(driver)) — the raw-Map cast mirrors the
  engine's erased put of a String key into the signature-keyed layer map.
--><#t>
((Map) ${params[0]}).put("anim_graph", new AnimationLayer(${params[1]}))<#t>