<#--
  Constructor expression for an "akythera:play_sound" AnimationNotify.
  DTO (PlaySoundNotifyHolder#getDTO): { sound } — the sound id string.
  Rebuilt as a direct holder; PlaySoundNotify only reads soundEvent.value().
  Emits a single inline Java expression (no surrounding whitespace).
--><#t>
new PlaySoundNotify(Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.parse("${sound}"))))<#t>