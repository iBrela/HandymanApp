package com.example.handyman.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.handyman.R
import com.example.handyman.utils.SUPPORTED_COUNTRIES
import com.example.handyman.utils.countryForDialCode
import com.example.handyman.utils.flagEmoji
import com.example.handyman.utils.localizedDigits
import com.example.handyman.utils.normalizeNationalNumber
import java.util.Locale
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException

/**
 * Phone entry with a country-code dropdown (default +880) next to the number field.
 * The dropdown offers [SUPPORTED_COUNTRIES], with names in the app's current language.
 * [onChange] always receives the cleaned pair, and the number is re-capped when the
 * country code changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneNumberField(
    countryCode: String,
    nationalNumber: String,
    onChange: (countryCode: String, nationalNumber: String) -> Unit,
    countryCodeError: Boolean,
    numberError: Boolean,
    modifier: Modifier = Modifier
) {
    val countryCodeDescription = stringResource(R.string.country_code_label)
    // Country names follow the app language (LocaleHelper sets it on the configuration).
    val displayLocale = LocalConfiguration.current.locales[0]
    val language = displayLocale.language
    val selectedFlag = countryForDialCode(countryCode)?.let { flagEmoji(it.regionCode) }.orEmpty()
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.width(136.dp)
        ) {
            OutlinedTextField(
                value = localizedDigits("$selectedFlag +$countryCode".trim(), language),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                isError = countryCodeError,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor()
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = countryCodeDescription }
            )
            // Plain DropdownMenu (not ExposedDropdownMenu) so the list can be wider than the
            // narrow code box and country names don't wrap.
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                SUPPORTED_COUNTRIES.forEach { country ->
                    val name = Locale("", country.regionCode).getDisplayCountry(displayLocale)
                    DropdownMenuItem(
                        text = { Text("${flagEmoji(country.regionCode)}  $name") },
                        trailingIcon = { Text(localizedDigits("+${country.dialCode}", language)) },
                        onClick = {
                            expanded = false
                            onChange(
                                country.dialCode,
                                normalizeNationalNumber(nationalNumber, country.dialCode)
                            )
                        }
                    )
                }
            }
        }
        OutlinedTextField(
            value = nationalNumber,
            onValueChange = { onChange(countryCode, normalizeNationalNumber(it, countryCode)) },
            placeholder = { Text(stringResource(R.string.phone_number_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            isError = numberError,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
        )
    }
}

/**
 * User-facing text for a failed phone verification. The raw Firebase message
 * (e.g. "An internal error has occurred") is not shown to users; callers log it instead.
 */
fun verificationErrorMessage(context: Context, e: FirebaseException): String = when (e) {
    is FirebaseAuthInvalidCredentialsException -> context.getString(R.string.error_phone_not_valid)
    is FirebaseTooManyRequestsException -> context.getString(R.string.error_too_many_attempts)
    else -> context.getString(R.string.error_phone_send_failed)
}
