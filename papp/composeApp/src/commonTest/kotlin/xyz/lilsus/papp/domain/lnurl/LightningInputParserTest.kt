package xyz.lilsus.papp.domain.lnurl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LightningInputParserTest {

    private val parser = LightningInputParser()

    @Test
    fun parsesPlainLightningAddress() {
        val result = parser.parse("pay@lilsus.xyz")

        val success = assertIs<LightningInputParser.ParseResult.Success>(result)
        val target = assertIs<LightningInputParser.Target.LightningAddressTarget>(success.target)
        assertEquals("pay", target.address.username)
        assertEquals("lilsus.xyz", target.address.domain)
        assertEquals(null, target.address.tag)
    }

    @Test
    fun parsesLightningAddressWithPrefixAndTag() {
        val result = parser.parse("lightning:LiLsUs+tips@BliNk.sv")

        val success = assertIs<LightningInputParser.ParseResult.Success>(result)
        val target = assertIs<LightningInputParser.Target.LightningAddressTarget>(success.target)
        assertEquals("lilsus", target.address.username)
        assertEquals("blink.sv", target.address.domain)
        assertEquals("tips", target.address.tag)
    }

    @Test
    fun parsesUrlWrappedLightningAddress() {
        val result = parser.parse("http://jum@blink.sv/")

        val success = assertIs<LightningInputParser.ParseResult.Success>(result)
        val target = assertIs<LightningInputParser.Target.LightningAddressTarget>(success.target)
        assertEquals("jum", target.address.username)
        assertEquals("blink.sv", target.address.domain)
    }

    @Test
    fun doesNotTreatHttpUrlWithPathAsLightningAddress() {
        val result = parser.parse("https://some.random.domain.com/some/resource")

        val success = assertIs<LightningInputParser.ParseResult.Success>(result)
        val target = success.target
        assertIs<LightningInputParser.Target.Lnurl>(target)
    }

    @Test
    fun doesNotExtractLightningAddressFromUrlUserInfo() {
        val result = parser.parse("https://pay@lilsus.xyz/.well-known/lnurlp/pay")

        val success = assertIs<LightningInputParser.ParseResult.Success>(result)
        val target = success.target
        assertIs<LightningInputParser.Target.Lnurl>(target)
    }

    @Test
    fun rejectsAddressWithPathSegment() {
        val result = parser.parse("pay@lilsus.xyz/path")

        val failure = assertIs<LightningInputParser.ParseResult.Failure>(result)
        assertTrue(failure.reason is LightningInputParser.FailureReason.Unrecognized)
    }

    @Test
    fun rejectsAddressWithoutDomainSuffix() {
        val result = parser.parse("pay@localhost")

        val failure = assertIs<LightningInputParser.ParseResult.Failure>(result)
        assertTrue(failure.reason is LightningInputParser.FailureReason.Unrecognized)
    }
}
