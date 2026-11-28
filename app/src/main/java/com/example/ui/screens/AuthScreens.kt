package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rabbta.chatapp.v1.R
import com.example.ui.AuthUiState

@Composable
fun LoginScreen(
    authState: AuthUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onForgotPassword: (String) -> Unit,
    onClearError: () -> Unit,
    onClearInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var showRegisterDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .height(176.dp)
                .clip(RoundedCornerShape(24.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_login_hero),
                contentDescription = "Millionaire Community Hero",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC062E28))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Forum,
                        contentDescription = null,
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = "Millionaire",
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )
                }
                Text(
                    text = "Simple Friends Chat, Coin Groups & Authority",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Main Login Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Sign In to Continue",
                    style = MaterialTheme.typography.headlineSmall
                )

                AnimatedVisibility(visible = authState.infoMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MarkEmailRead,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Email check karein",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = authState.infoMessage.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            IconButton(
                                onClick = onClearInfo,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = authState.error != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = "Login error",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = authState.error.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = identifier,
                    onValueChange = {
                        identifier = it
                        onClearError()
                    },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("identifier_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onClearError()
                    },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password"
                            )
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showForgotDialog = true },
                        modifier = Modifier.testTag("forgot_password_button")
                    ) {
                        Text("Forgot Password?")
                    }

                    TextButton(
                        onClick = { showRegisterDialog = true },
                        modifier = Modifier.testTag("open_register_button")
                    ) {
                        Text("Create Account")
                    }
                }

                Button(
                    onClick = { onLogin(identifier, password) },
                    enabled = !authState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("login_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (authState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Login, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Login", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }

    }

    if (showForgotDialog) {
        var resetInput by remember { mutableStateOf(identifier) }
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            icon = { Icon(Icons.Filled.LockReset, contentDescription = null) },
            title = { Text("Forgot Password?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Apni account wali email likhein. Password reset karne ka link email par aayega.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = resetInput,
                        onValueChange = { resetInput = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forgot_identifier_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onForgotPassword(resetInput)
                        showForgotDialog = false
                    },
                    modifier = Modifier.testTag("send_temp_password_button")
                ) {
                    Text("Send Reset Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRegisterDialog) {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var regPass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRegisterDialog = false },
            icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
            title = { Text("Create New Account") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = regPass,
                        onValueChange = { regPass = it },
                        label = { Text("Password (8+ chars, 1 Cap, 1 Num)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRegister(name, email, regPass)
                        showRegisterDialog = false
                    },
                    modifier = Modifier.testTag("confirm_register_button")
                ) {
                    Text("Sign Up")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterDialog = false }) { Text("Cancel") }
            }
        )
    }
}
