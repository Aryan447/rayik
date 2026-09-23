package app.rayik.music.domain.raay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RaayRulesTest {
  @Test fun `suggestion needs title and reason`() {
    assertTrue(RaayRules.isValid(RaayRules.Suggestion("Mehfil Mix", "you looped Arijit 12x")))
    assertFalse(RaayRules.isValid(RaayRules.Suggestion("", "you looped Arijit 12x")))
    assertFalse(RaayRules.isValid(RaayRules.Suggestion("Mehfil Mix", "")))
  }
}
