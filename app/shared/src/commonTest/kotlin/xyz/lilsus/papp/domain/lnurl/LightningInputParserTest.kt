package xyz.lilsus.papp.domain.lnurl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LightningInputParserTest {

    private val parser = LightningInputParser()

    @Test
    fun parsesLightningAddressForms() {
        val cases = listOf(
            AddressCase(
                input = "pay@lilsus.xyz",
                username = "pay",
                domain = "lilsus.xyz",
                tag = null,
                full = "pay@lilsus.xyz"
            ),
            AddressCase(
                input = "lightning:LiLsUs+Tips@BliNk.sv",
                username = "LiLsUs",
                domain = "blink.sv",
                tag = "Tips",
                full = "LiLsUs+Tips@blink.sv"
            ),
            AddressCase(
                input = "http://JuM@BliNk.sv/",
                username = "JuM",
                domain = "blink.sv",
                tag = null,
                full = "JuM@blink.sv"
            )
        )

        cases.forEach { case ->
            val success = assertIs<LightningInputParser.ParseResult.Success>(
                parser.parse(case.input)
            )
            val target = assertIs<LightningInputParser.Target.LightningAddressTarget>(
                success.target
            )
            assertEquals(case.username, target.address.username)
            assertEquals(case.domain, target.address.domain)
            assertEquals(case.tag, target.address.tag)
            assertEquals(case.full, target.address.full)
        }
    }

    @Test
    fun parsesLnurlLikeHttpUrls() {
        val cases = listOf(
            "https://example.com/.well-known/lnurlp/pay",
            "https://pay@lilsus.xyz/.well-known/lnurlp/pay"
        )

        cases.forEach { input ->
            val success = assertIs<LightningInputParser.ParseResult.Success>(
                parser.parse(input)
            )
            assertIs<LightningInputParser.Target.Lnurl>(success.target)
        }
    }

    @Test
    fun rejectsInputsThatOnlyLookLikeLightningAddresses() {
        val cases = listOf(
            "https://some.random.domain.com/some/resource",
            "pay@lilsus.xyz/path",
            "pay@localhost",
            "golol.de"
        )

        cases.forEach { input ->
            val failure = assertIs<LightningInputParser.ParseResult.Failure>(
                parser.parse(input)
            )
            assertTrue(failure.reason is LightningInputParser.FailureReason.Unrecognized)
        }
    }

    private data class AddressCase(val input: String, val username: String, val domain: String, val tag: String?, val full: String)
}
