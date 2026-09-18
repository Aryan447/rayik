package app.rayik.music.domain.raay

/** v1 Raay rule: morning pick must always carry its reason. */
object RaayRules {
  data class Suggestion(val title: String, val reason: String)

  fun isValid(s: Suggestion): Boolean =
    s.title.isNotBlank() && s.reason.isNotBlank()

  fun headline(s: Suggestion): String =
    if (isValid(s)) "${s.title} — because ${s.reason}" else s.title.ifBlank { "Something for you" }
}
