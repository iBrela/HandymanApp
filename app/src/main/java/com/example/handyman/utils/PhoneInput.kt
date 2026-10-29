package com.example.handyman.utils

/** Country code the phone screens start with (Bangladesh). */
const val DEFAULT_COUNTRY_CODE = "880"

private const val BANGLADESH_CODE = "880"
private const val BANGLADESH_NATIONAL_DIGITS = 10 // 01XXXXXXXXX without the trunk 0
private const val MAX_E164_DIGITS = 15
private const val MAX_COUNTRY_CODE_DIGITS = 3
private const val MIN_NATIONAL_DIGITS = 4

private val COUNTRY_CODE_REGEX = Regex("[1-9]\\d{0,2}")

/** A country offered in the country-code dropdown: ISO 3166 region (e.g. "BD") and dialling code. */
data class CountryDialCode(val regionCode: String, val dialCode: String)

/** Countries offered in the phone screens' dropdown. Bangladesh comes first as the default. */
val SUPPORTED_COUNTRIES = listOf(
    CountryDialCode("BD", BANGLADESH_CODE),
    CountryDialCode("IN", "91"),
    CountryDialCode("AU", "61"),
    CountryDialCode("GB", "44"),
    CountryDialCode("US", "1"),
    CountryDialCode("VN", "84"),
)

/** The supported country with this dialling code, or null if it isn't in the dropdown. */
fun countryForDialCode(dialCode: String): CountryDialCode? =
    SUPPORTED_COUNTRIES.firstOrNull { it.dialCode == dialCode }

/**
 * Display-only: shows ASCII digits as Bangla digits (০-৯) when [language] is Bangla, e.g. "+880" -> "+৮৮০".
 * Other text is unchanged. Values sent to Firebase must stay ASCII, so use this only for labels.
 */
fun localizedDigits(text: String, language: String): String =
    if (language != LocaleHelper.LANG_BANGLA) text
    else text.map { if (it in '0'..'9') '০' + (it - '0') else it }.joinToString("")

/** Flag emoji for a two-letter region code, e.g. "BD" -> 🇧🇩 (built from regional indicator symbols). */
fun flagEmoji(regionCode: String): String =
    regionCode.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")

/** Why a country code / number pair is not a valid phone number. */
enum class PhoneError { COUNTRY_CODE, BANGLADESH, GENERIC }

/**
 * Keeps only digits. Any Unicode decimal digit (e.g. Bangla ০-৯) is converted to its
 * ASCII equivalent; spaces, dashes, letters and invisible characters are dropped.
 */
private fun asciiDigits(input: String): String {
    val out = StringBuilder(input.length)
    for (c in input) {
        if (Character.isDigit(c)) out.append('0' + Character.digit(c, 10))
    }
    return out.toString()
}

/** Country code box: 1-3 ASCII digits, no '+' (the field shows it) and no leading zero. */
fun normalizeCountryCode(input: String): String =
    asciiDigits(input).trimStart('0').take(MAX_COUNTRY_CODE_DIGITS)

/**
 * How many digits the number field accepts. A leading trunk '0' (e.g. Bangladesh 01712345678)
 * is allowed on top of the number itself. Bangladesh numbers are capped at 10 digits
 * (11 with the 0); other countries are capped so country code + number fit E.164's 15 digits.
 */
private fun maxNationalLength(countryCode: String, digits: String): Int {
    val trunkZero = if (digits.startsWith("0")) 1 else 0
    val limit = if (countryCode == BANGLADESH_CODE) {
        BANGLADESH_NATIONAL_DIGITS
    } else {
        MAX_E164_DIGITS - countryCode.length
    }
    return limit + trunkZero
}

/**
 * Cleans the number field: ASCII digits only (Bangla digits converted), capped to the
 * maximum length for [countryCode]. If a full international number is pasted
 * ("+8801712345678") the country code is dropped so only the local part is kept.
 */
fun normalizeNationalNumber(input: String, countryCode: String): String {
    var digits = asciiDigits(input)
    val startsWithPlus = input.firstOrNull { it == '+' || it == '＋' || Character.isDigit(it) }
        .let { it == '+' || it == '＋' }
    if (countryCode.isNotEmpty() && digits.startsWith(countryCode) &&
        (startsWithPlus || digits.length > maxNationalLength(countryCode, digits))
    ) {
        digits = digits.removePrefix(countryCode)
    }
    return digits.take(maxNationalLength(countryCode, digits))
}

/**
 * Validates a country code + number pair, or returns why it is invalid.
 * A single leading trunk '0' on the number is accepted and ignored.
 * - Bangladesh (880): exactly 10 digits starting with 1, i.e. 01XXXXXXXXX or 1XXXXXXXXX.
 * - Other countries: at least 4 digits, not starting with 0, at most 15 digits in total.
 */
fun validatePhone(countryCode: String, nationalNumber: String): PhoneError? {
    if (!COUNTRY_CODE_REGEX.matches(countryCode)) return PhoneError.COUNTRY_CODE
    val n = nationalNumber.removePrefix("0")
    return if (countryCode == BANGLADESH_CODE) {
        if (n.length == BANGLADESH_NATIONAL_DIGITS && n[0] == '1') null else PhoneError.BANGLADESH
    } else {
        if (n.length >= MIN_NATIONAL_DIGITS && n[0] != '0' &&
            countryCode.length + n.length <= MAX_E164_DIGITS
        ) null else PhoneError.GENERIC
    }
}

/** E.164 number sent to Firebase and stored, e.g. +8801712345678 (trunk 0 removed). */
fun buildE164(countryCode: String, nationalNumber: String): String =
    "+$countryCode${nationalNumber.removePrefix("0")}"

/**
 * Cleans an OTP code field to ASCII digits only. Bangla digits (০-৯) are converted
 * to 0-9 (Firebase only accepts ASCII digits), and spaces/invisible characters are dropped.
 */
fun normalizeOtpInput(input: String): String = asciiDigits(input)
