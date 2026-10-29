package com.example.handyman

import com.example.handyman.utils.DEFAULT_COUNTRY_CODE
import com.example.handyman.utils.PhoneError
import com.example.handyman.utils.SUPPORTED_COUNTRIES
import com.example.handyman.utils.buildE164
import com.example.handyman.utils.countryForDialCode
import com.example.handyman.utils.flagEmoji
import com.example.handyman.utils.localizedDigits
import com.example.handyman.utils.normalizeCountryCode
import com.example.handyman.utils.normalizeNationalNumber
import com.example.handyman.utils.normalizeOtpInput
import com.example.handyman.utils.validatePhone
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneInputTest {

    // ---- country code dropdown ----

    @Test
    fun dropdownStartsWithBangladeshAsDefault() {
        assertThat(SUPPORTED_COUNTRIES.first().dialCode).isEqualTo(DEFAULT_COUNTRY_CODE)
        assertThat(countryForDialCode(DEFAULT_COUNTRY_CODE)?.regionCode).isEqualTo("BD")
    }

    @Test
    fun dropdownCodesAreUniqueAndValid() {
        val codes = SUPPORTED_COUNTRIES.map { it.dialCode }
        assertThat(codes).containsNoDuplicates()
        codes.forEach { assertThat(normalizeCountryCode(it)).isEqualTo(it) }
    }

    @Test
    fun unknownDialCodeHasNoCountry() {
        assertThat(countryForDialCode("999")).isNull()
    }

    @Test
    fun flagEmojiIsBuiltFromRegionCode() {
        assertThat(flagEmoji("BD")).isEqualTo("🇧🇩")
        assertThat(flagEmoji("au")).isEqualTo("🇦🇺")
    }

    @Test
    fun dialCodeShowsBanglaDigitsOnlyInBangla() {
        assertThat(localizedDigits("+880", "bn")).isEqualTo("+৮৮০")
        assertThat(localizedDigits("🇻🇳 +84", "bn")).isEqualTo("🇻🇳 +৮৪")
        assertThat(localizedDigits("+880", "en")).isEqualTo("+880")
    }

    @Test
    fun banglaDialCodeConvertsBackToAscii() {
        assertThat(normalizeCountryCode(localizedDigits("880", "bn"))).isEqualTo("880")
    }

    // ---- country code box ----

    @Test
    fun countryCodeKeepsDigitsOnly() {
        assertThat(normalizeCountryCode("880")).isEqualTo("880")
        assertThat(normalizeCountryCode("+61")).isEqualTo("61")
        assertThat(normalizeCountryCode(" 6 1 ")).isEqualTo("61")
    }

    @Test
    fun countryCodeConvertsBanglaDigitsAndCapsAtThree() {
        assertThat(normalizeCountryCode("৮৮০")).isEqualTo("880")
        assertThat(normalizeCountryCode("8801")).isEqualTo("880")
    }

    @Test
    fun countryCodeHasNoLeadingZero() {
        assertThat(normalizeCountryCode("0")).isEmpty()
        assertThat(normalizeCountryCode("00880")).isEqualTo("880")
        assertThat(normalizeCountryCode("")).isEmpty()
    }

    // ---- number box: Bangladesh ----

    @Test
    fun bangladeshAcceptsLocalAndInternationalStyle() {
        assertThat(normalizeNationalNumber("01712345678", "880")).isEqualTo("01712345678")
        assertThat(normalizeNationalNumber("1712345678", "880")).isEqualTo("1712345678")
    }

    @Test
    fun bangladeshInputIsCappedAt11DigitsWithZeroOr10Without() {
        assertThat(normalizeNationalNumber("017123456789999", "880")).isEqualTo("01712345678")
        assertThat(normalizeNationalNumber("17123456789999", "880")).isEqualTo("1712345678")
    }

    @Test
    fun banglaDigitsAreConvertedToAscii() {
        assertThat(normalizeNationalNumber("০১৭১২৩৪৫৬৭৮", "880")).isEqualTo("01712345678")
        assertThat(normalizeNationalNumber("০১৭12৩৪৫৬৭৮", "880")).isEqualTo("01712345678")
    }

    @Test
    fun spacesSeparatorsAndInvisibleCharsAreDropped() {
        assertThat(normalizeNationalNumber("017 1234-5678 ", "880")).isEqualTo("01712345678")
        assertThat(normalizeNationalNumber(" 1712345678‎", "880")).isEqualTo("1712345678")
        assertThat(normalizeNationalNumber("abc", "880")).isEmpty()
    }

    @Test
    fun pastedInternationalNumberDropsTheCountryCode() {
        assertThat(normalizeNationalNumber("+8801712345678", "880")).isEqualTo("1712345678")
        assertThat(normalizeNationalNumber("+880 1712 345678", "880")).isEqualTo("1712345678")
        assertThat(normalizeNationalNumber("8801712345678", "880")).isEqualTo("1712345678")
    }

    @Test
    fun typingTheCountryCodeDigitByDigitStillEndsUpCorrect() {
        var text = ""
        "8801712345678".forEach { text = normalizeNationalNumber(text + it, "880") }
        assertThat(text).isEqualTo("1712345678")
    }

    // ---- number box: other countries ----

    @Test
    fun otherCountriesKeepTotalWithinE164() {
        assertThat(normalizeNationalNumber("0444555666", "61")).isEqualTo("0444555666")
        assertThat(normalizeNationalNumber("44455566612345678", "61")).isEqualTo("4445556661234")
        assertThat(normalizeNationalNumber("+61444555666", "61")).isEqualTo("444555666")
    }

    @Test
    fun capShrinksWhenCountryCodeGetsLonger() {
        assertThat(normalizeNationalNumber("44455566612345", "1")).isEqualTo("44455566612345")
        assertThat(normalizeNationalNumber("44455566612345", "353")).isEqualTo("444555666123")
    }

    // ---- validation ----

    @Test
    fun bangladeshNumbersAreValid() {
        assertThat(validatePhone("880", "01712345678")).isNull()
        assertThat(validatePhone("880", "1712345678")).isNull()
    }

    @Test
    fun bangladeshNumbersWithWrongLengthOrStartAreRejected() {
        assertThat(validatePhone("880", "")).isEqualTo(PhoneError.BANGLADESH)
        assertThat(validatePhone("880", "0171234567")).isEqualTo(PhoneError.BANGLADESH)
        assertThat(validatePhone("880", "171234567")).isEqualTo(PhoneError.BANGLADESH)
        assertThat(validatePhone("880", "2712345678")).isEqualTo(PhoneError.BANGLADESH)
        assertThat(validatePhone("880", "001712345678")).isEqualTo(PhoneError.BANGLADESH)
    }

    @Test
    fun otherCountriesAcceptWithOrWithoutLeadingZero() {
        assertThat(validatePhone("61", "0444555666")).isNull()
        assertThat(validatePhone("61", "444555666")).isNull()
        assertThat(validatePhone("1", "2025550123")).isNull()
    }

    @Test
    fun otherCountriesRejectTooShortDoubleZeroOrTooLong() {
        assertThat(validatePhone("61", "123")).isEqualTo(PhoneError.GENERIC)
        assertThat(validatePhone("61", "00444555666")).isEqualTo(PhoneError.GENERIC)
        assertThat(validatePhone("1", "123456789012345")).isEqualTo(PhoneError.GENERIC)
    }

    @Test
    fun invalidCountryCodesAreRejected() {
        assertThat(validatePhone("", "1712345678")).isEqualTo(PhoneError.COUNTRY_CODE)
        assertThat(validatePhone("0", "1712345678")).isEqualTo(PhoneError.COUNTRY_CODE)
        assertThat(validatePhone("8800", "1712345678")).isEqualTo(PhoneError.COUNTRY_CODE)
    }

    @Test
    fun cleanedBanglaInputPassesValidation() {
        val number = normalizeNationalNumber("০১৭১২৩৪৫৬৭৮", "880")
        assertThat(validatePhone("880", number)).isNull()
    }

    // ---- number sent to Firebase ----

    @Test
    fun e164DropsTheLeadingZero() {
        assertThat(buildE164("880", "01712345678")).isEqualTo("+8801712345678")
        assertThat(buildE164("880", "1712345678")).isEqualTo("+8801712345678")
        assertThat(buildE164("61", "0444555666")).isEqualTo("+61444555666")
        assertThat(buildE164("61", "444555666")).isEqualTo("+61444555666")
    }

    // ---- OTP code ----

    @Test
    fun otpAsciiIsUnchanged() {
        assertThat(normalizeOtpInput("123456")).isEqualTo("123456")
    }

    @Test
    fun otpBanglaDigitsAreConvertedToAscii() {
        assertThat(normalizeOtpInput("১২৩৪৫৬")).isEqualTo("123456")
        assertThat(normalizeOtpInput("১২3৪5৬")).isEqualTo("123456")
    }

    @Test
    fun otpSeparatorsAndInvisibleCharsAreDropped() {
        assertThat(normalizeOtpInput("123 456 ")).isEqualTo("123456")
        assertThat(normalizeOtpInput("123-456‎")).isEqualTo("123456")
        assertThat(normalizeOtpInput("+12a3")).isEqualTo("123")
        assertThat(normalizeOtpInput("")).isEmpty()
    }

    @Test
    fun otpOutputPassesScreenValidation() {
        listOf("১২৩৪৫৬", "123 456 ").forEach {
            assertThat(normalizeOtpInput(it).matches(Regex("^\\d{6}$"))).isTrue()
        }
    }
}
