package com.example.handyman.handyman_pages

import android.util.Log
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.handyman.HandymanSignupViewModel
import com.example.handyman.R
import com.example.handyman.components.DividerLine
import com.example.handyman.components.StepCircle
import com.example.handyman.components.PhoneNumberField
import com.example.handyman.components.verificationErrorMessage
import com.example.handyman.utils.DEFAULT_COUNTRY_CODE
import com.example.handyman.utils.PhoneError
import com.example.handyman.utils.buildE164
import com.example.handyman.utils.validatePhone

fun findActivity(context: Context): android.app.Activity? {
    var currentContext = context
    while (currentContext is ContextWrapper) {
        if (currentContext is android.app.Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

@Composable
fun HandymanKYCPhoneNumber(modifier: Modifier = Modifier, navController: NavController, signupViewModel: HandymanSignupViewModel) {
    val context = LocalContext.current
    var countryCode by remember { mutableStateOf(DEFAULT_COUNTRY_CODE) }
    var nationalNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

    val phoneValidation = validatePhone(countryCode, nationalNumber)
    val isValidPhone = phoneValidation == null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Top Bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.arrow_back),
                contentDescription = stringResource(R.string.cd_back),
                modifier = Modifier
                    .size(24.dp)
                    .clickable { navController.popBackStack() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.account_verification_title), fontSize = 20.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Step indicator (Step 4 of 4)
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            StepCircle(stepNumber = 1, isActive = true)
            DividerLine()
            StepCircle(stepNumber = 2, isActive = true)
            DividerLine()
            StepCircle(stepNumber = 3, isActive = true)
            DividerLine()
            StepCircle(stepNumber = 4, isActive = true)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Header
        Text(stringResource(R.string.verify_phone_title), fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.otp_hint),
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(stringResource(R.string.mobile_label), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        // Country code dropdown (default +880) next to the number field, which strips spaces and
        // invisible characters, convert Bangla digits to ASCII and enforce the length limit.
        PhoneNumberField(
            countryCode = countryCode,
            nationalNumber = nationalNumber,
            onChange = { code, number ->
                countryCode = code
                nationalNumber = number
                errorMessage = null
            },
            countryCodeError = phoneValidation == PhoneError.COUNTRY_CODE,
            numberError = (nationalNumber.isNotEmpty() && phoneValidation != null) || errorMessage != null
        )

        // Server error wins; otherwise explain the expected format instead of
        // only turning the field red.
        val phoneError = errorMessage ?: when {
            phoneValidation == PhoneError.COUNTRY_CODE -> stringResource(R.string.error_country_code_invalid)
            nationalNumber.isEmpty() -> null
            phoneValidation == PhoneError.BANGLADESH -> stringResource(R.string.error_phone_invalid_bd)
            phoneValidation == PhoneError.GENERIC -> stringResource(R.string.error_phone_invalid)
            else -> null
        }
        if (phoneError != null) {
            Text(
                text = phoneError,
                color = Color.Red,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = {
                val currentActivity = findActivity(context)
                if (currentActivity == null) {
                    Toast.makeText(context, context.getString(R.string.error_activity_not_found_message), Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isLoading = true
                errorMessage = null

                // E.164 number, e.g. +8801712345678 (a leading 0 is dropped)
                val formattedNumber = buildE164(countryCode, nationalNumber)

                val callbacks = object : com.google.firebase.auth.PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: com.google.firebase.auth.PhoneAuthCredential) {
                        isLoading = false
                    }

                    override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                        isLoading = false
                        // Friendly text for the user; the raw Firebase error stays in Logcat.
                        errorMessage = verificationErrorMessage(context, e)
                        Log.e("KYC", "Verification failed: ${e.message}", e)
                    }

                    override fun onCodeSent(
                        verificationId: String,
                        token: com.google.firebase.auth.PhoneAuthProvider.ForceResendingToken
                    ) {
                        isLoading = false
                        // Account is not created yet — the phone number is only
                        // committed once OTP verification succeeds.
                        signupViewModel.phoneNumber = formattedNumber
                        navController.navigate("handymanKycCodeOTP/$verificationId/$formattedNumber")
                    }
                }

                val options = com.google.firebase.auth.PhoneAuthOptions.newBuilder(auth)
                    .setPhoneNumber(formattedNumber)
                    .setTimeout(60L, java.util.concurrent.TimeUnit.SECONDS)
                    .setActivity(currentActivity)
                    .setCallbacks(callbacks)
                    .build()
                com.google.firebase.auth.PhoneAuthProvider.verifyPhoneNumber(options)
            },
            enabled = isValidPhone && !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isValidPhone) Color(0xFF2F3367) else Color(0xFFCCCCCC)
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(stringResource(R.string.get_otp_btn), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
