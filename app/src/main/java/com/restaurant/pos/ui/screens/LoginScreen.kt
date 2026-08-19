package com.restaurant.pos.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.restaurant.pos.R
import com.restaurant.pos.ui.theme.*
import com.restaurant.pos.ui.viewmodel.RestaurantViewModel
import kotlinx.coroutines.launch

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx != null) {
        if (ctx is Activity) return ctx
        if (ctx is ContextWrapper) {
            ctx = ctx.baseContext
        } else {
            break
        }
    }
    return null
}


@Composable
fun LoginScreen(
    viewModel: RestaurantViewModel,
    activity: Activity? = null,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val currentActivity = activity ?: (LocalContext.current as? Activity) ?: LocalContext.current.findActivity()
    androidx.activity.compose.BackHandler {
        onBack()
    }

    var emailOrPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }

    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val savedSetting by viewModel.receiptSetting.collectAsState()
    val restaurantName = savedSetting?.shopName?.takeIf { it.isNotBlank() } ?: "RESTAURANT"

    // Theme Colors for the Login Screen
    val premiumDarkBg = Brush.verticalGradient(
        colors = listOf(Color(0xFF2C1914), Color(0xFF100B09))
    )
    val cardBackground = Color(0xFFFFF9F0) // Cream color from reference
    val cardSurfaceColor = Color.White
    val textDark = Color(0xFF1E1E1E)
    val textGray = Color(0xFF757575)
    val primaryOrange = Color(0xFFFF6100) // Gradient/Orange style
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(premiumDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("login_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("login_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            // Header Area (Restaurant Logo & Name)
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFF050505), shape = RoundedCornerShape(20.dp))
                    .border(1.5.dp, Color(0xFFFFD447).copy(alpha = 0.7f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_restaurant_logo),
                    contentDescription = "Restaurant POS Logo",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(68.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = restaurantName.uppercase(),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                HorizontalDivider(modifier = Modifier.width(30.dp), color = primaryOrange, thickness = 2.dp)
                Text(
                    text = " POS ",
                    color = primaryOrange,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                HorizontalDivider(modifier = Modifier.width(30.dp), color = primaryOrange, thickness = 2.dp)
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.login_sub_title),
                color = Color(0xCCFFFFFF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Login Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
                color = cardBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isSignUpMode) stringResource(R.string.login_join_us) else stringResource(R.string.login_welcome_back),
                        color = textDark,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isSignUpMode) stringResource(R.string.login_subtitle_signup) else stringResource(R.string.login_subtitle_welcome),
                        color = textGray,
                        fontSize = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    

                    if (isSignUpMode) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            placeholder = { Text(stringResource(R.string.lbl_full_name), color = textGray) },
                            leadingIcon = {
                                Icon(Icons.Outlined.PersonOutline, contentDescription = "Name", tint = primaryOrange)
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = cardSurfaceColor,
                                unfocusedContainerColor = cardSurfaceColor,
                                focusedBorderColor = primaryOrange,
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedTextColor = textDark,
                                unfocusedTextColor = textDark
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("login_name_input")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    
                    // Email Input
                    OutlinedTextField(
                        value = emailOrPhone,
                        onValueChange = { 
                            emailOrPhone = it
                            if (errorMessage.isNotEmpty()) errorMessage = ""
                        },
                        placeholder = { Text(stringResource(R.string.lbl_email_address), color = textGray) },
                        leadingIcon = {
                            Icon(Icons.Outlined.PersonOutline, contentDescription = "User", tint = primaryOrange)
                        },
                        trailingIcon = {
                            Icon(Icons.Outlined.Email, contentDescription = "Email", tint = textGray)
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = cardSurfaceColor,
                            unfocusedContainerColor = cardSurfaceColor,
                            focusedBorderColor = primaryOrange,
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedTextColor = textDark,
                            unfocusedTextColor = textDark
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_email_input")
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text(stringResource(R.string.lbl_password), color = textGray) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(Icons.Outlined.Lock, contentDescription = "Lock", tint = primaryOrange)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password",
                                    tint = textGray
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = cardSurfaceColor,
                            unfocusedContainerColor = cardSurfaceColor,
                            focusedBorderColor = primaryOrange,
                            unfocusedBorderColor = Color(0xFFE0E0E0),
                            focusedTextColor = textDark,
                            unfocusedTextColor = textDark
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_password_input")
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Remember Me & Forgot Password
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { rememberMe = !rememberMe }.padding(vertical = 4.dp)) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                colors = CheckboxDefaults.colors(checkedColor = primaryOrange, uncheckedColor = textGray),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.lbl_remember_me), color = textDark, fontSize = 13.sp)
                        }
                        Text(
                            text = stringResource(R.string.lbl_forgot_password),
                            color = primaryOrange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { }
                        )
                    }
                    
                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage,
                            color = Color.Red,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // LOGIN SECURELY Button
                    Button(
                        onClick = {
                            val trimmedEmail = emailOrPhone.trim()
                            if (!trimmedEmail.contains("@") || !trimmedEmail.contains(".")) {
                                errorMessage = "Please enter a valid email address."
                                return@Button
                            }
                            if (password.length < 6) {
                                errorMessage = "Password must be at least 6 characters."
                                return@Button
                            }
                            isLoading = true
                            errorMessage = ""
                            coroutineScope.launch {
                                val result = if (isSignUpMode) {
                                    viewModel.authRepo.register(name, trimmedEmail, password)
                                } else {
                                    viewModel.authRepo.login(trimmedEmail, password)
                                }
                                isLoading = false
                                result.onSuccess {
                                    onLoginSuccess()
                                }.onFailure {
                                    errorMessage = it.message ?: "Authentication failed. Check credentials."
                                }
                            }
                        },
                        enabled = !isLoading && emailOrPhone.isNotBlank() && password.isNotBlank() && (!isSignUpMode || name.isNotBlank()),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryOrange,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("login_submit_btn")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = "Secure", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isSignUpMode) stringResource(R.string.btn_signup) else stringResource(R.string.btn_login),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Or continue with
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE0E0E0))
                        Text(
                            text = "  OR CONTINUE WITH  ",
                            color = textGray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE0E0E0))
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Continue with Google
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                errorMessage = ""
                                try {
                                    val targetActivity = currentActivity ?: activity ?: (context as? Activity) ?: context.findActivity()
                                    if (targetActivity == null) {
                                        errorMessage = "Cannot launch Google Login: Activity context is not available."
                                        isLoading = false
                                        return@launch
                                    }
                                    val credentialManager = CredentialManager.create(targetActivity)
                                    val resId = targetActivity.resources.getIdentifier("default_web_client_id", "string", targetActivity.packageName)
                                    val webClientId = if (resId != 0) {
                                        targetActivity.getString(resId).ifBlank { "418091820215-restaurant-pos-99d57.apps.googleusercontent.com" }
                                    } else {
                                        "418091820215-restaurant-pos-99d57.apps.googleusercontent.com"
                                    }

                                    val googleIdOption = GetGoogleIdOption.Builder()
                                        .setFilterByAuthorizedAccounts(false)
                                        .setServerClientId(webClientId)
                                        .setAutoSelectEnabled(false)
                                        .build()

                                    val request = GetCredentialRequest.Builder()
                                        .addCredentialOption(googleIdOption)
                                        .build()

                                    val result = credentialManager.getCredential(targetActivity, request)
                                    if (result.credential is CustomCredential && result.credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                                        val res = viewModel.authRepo.loginWithGoogle(googleIdTokenCredential.idToken)
                                        if (res.isSuccess) {
                                            onLoginSuccess()
                                        } else {
                                            errorMessage = res.exceptionOrNull()?.message ?: "Google Login failed"
                                        }
                                    } else {
                                        errorMessage = "Unexpected credential type"
                                    }
                                } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
                                    // User dismissed or canceled the sign-in prompt
                                } catch (e: SecurityException) {
                                    errorMessage = "Google Play Services is updating or unavailable. Please sign in with Email & Password."
                                } catch (e: Exception) {
                                    val msg = e.message ?: ""
                                    val isCancel = msg.contains("cancel", ignoreCase = true) ||
                                            msg.contains("USER_CANCELED", ignoreCase = true) ||
                                            e.javaClass.simpleName.contains("Cancel", ignoreCase = true)
                                    if (!isCancel) {
                                        errorMessage = if (msg.contains("gms", ignoreCase = true) || msg.contains("service", ignoreCase = true)) {
                                            "Google Sign-In unavailable on this device. Please use Email & Password."
                                        } else {
                                            msg.ifBlank { "Google Login failed or canceled" }
                                        }
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = cardSurfaceColor,
                            contentColor = textDark
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE0E0E0))),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("login_google_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Google", tint = Color.Red, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.btn_sign_in_with_google), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Security Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFC8E6C9), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF4CAF50), shape = RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = "Security", tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Your Security is Our Priority", color = Color(0xFF2E7D32), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("We use advanced encryption to protect your data and keep your account safe.", color = Color(0xFF388E3C), fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.Lock, contentDescription = "Lock", tint = Color(0x664CAF50), modifier = Modifier.size(32.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Sign Up/Login Toggle
                    Row {
                        Text(
                            text = if (isSignUpMode) stringResource(R.string.lbl_already_have_account) + " " else stringResource(R.string.lbl_dont_have_account) + " ", 
                            color = textDark, 
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isSignUpMode) stringResource(R.string.btn_login) else stringResource(R.string.btn_signup),
                            color = primaryOrange,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                isSignUpMode = !isSignUpMode
                                errorMessage = ""
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Footer Icons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FooterFeatureItem(Icons.Outlined.Security, "Secure\nAuthentication", primaryOrange)
                        FooterFeatureItem(Icons.Outlined.Lock, "Encrypted\nConnection", primaryOrange)
                        FooterFeatureItem(Icons.Outlined.Devices, "Secure\nSession", primaryOrange)
                        FooterFeatureItem(Icons.Outlined.PersonOutline, "Privacy\nProtected", primaryOrange)
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun FooterFeatureItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            color = Color(0xFF424242),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
