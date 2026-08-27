package uk.co.rodrunners.raffles.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.co.rodrunners.raffles.ui.components.GoldButton
import uk.co.rodrunners.raffles.ui.components.QuietButton
import uk.co.rodrunners.raffles.ui.components.Wordmark
import uk.co.rodrunners.raffles.ui.theme.Dimens
import uk.co.rodrunners.raffles.ui.theme.RrrColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = RrrColors.Ink,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back", tint = RrrColors.Bone)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = RrrColors.Ink,
                    titleContentColor = RrrColors.Bone,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .background(RrrColors.Ink)
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.gutter),
        ) { content() }
    }
}

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    AuthScaffold("Log in", onBack) {
        Spacer(Modifier.height(12.dp))
        Wordmark(Modifier.fillMaxWidth(), compact = true)
        Spacer(Modifier.height(32.dp))

        RrrTextField(
            value = form.email,
            onValueChange = viewModel::onEmail,
            label = "Email address",
            keyboardType = KeyboardType.Email,
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(
            value = form.password,
            onValueChange = viewModel::onPassword,
            label = "Password",
            imeAction = ImeAction.Done,
        )

        form.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it.message, style = MaterialTheme.typography.bodySmall, color = RrrColors.Danger)
        }
        form.info?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = RrrColors.Success)
        }

        Spacer(Modifier.height(24.dp))
        GoldButton(
            text = "Log in",
            onClick = { viewModel.logIn(onLoggedIn) },
            enabled = form.canLogIn,
            loading = form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            QuietButton("Forgot password", onForgotPassword)
            QuietButton("Create an account", onRegister)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onRegistered: () -> Unit,
    onOpenRules: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    AuthScaffold("Create your account", onBack) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Entries are limited per person, so we need an account before you can take part.",
            style = MaterialTheme.typography.bodyMedium,
            color = RrrColors.Mist,
        )
        Spacer(Modifier.height(24.dp))

        RrrTextField(form.displayName, viewModel::onDisplayName, "Your name")
        Spacer(Modifier.height(12.dp))
        RrrTextField(form.email, viewModel::onEmail, "Email address", keyboardType = KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        PasswordField(form.password, viewModel::onPassword, "Password")
        Text(
            "At least 8 characters, with a letter and a number.",
            style = MaterialTheme.typography.labelSmall,
            color = if (form.password.isEmpty() || form.passwordValid) RrrColors.Slate else RrrColors.Warning,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )
        Spacer(Modifier.height(12.dp))
        PasswordField(form.confirmPassword, viewModel::onConfirmPassword, "Confirm password", imeAction = ImeAction.Done)
        Spacer(Modifier.height(12.dp))
        RrrTextField(form.referralCode, viewModel::onReferralCode, "Referral code (optional)")

        Spacer(Modifier.height(20.dp))
        ConsentRow(
            checked = form.ageConfirmed,
            onCheckedChange = viewModel::onAgeConfirmed,
            text = "I confirm I am 18 or over and resident in the UK.",
        )
        ConsentRow(
            checked = form.rulesAccepted,
            onCheckedChange = viewModel::onRulesAccepted,
            text = "I have read the competition rules, terms and privacy policy.",
            actionLabel = "Read",
            onAction = onOpenRules,
        )
        ConsentRow(
            checked = form.marketingOptIn,
            onCheckedChange = viewModel::onMarketing,
            text = "Email me about new raffles and offers. Optional, and you can turn it off any time.",
        )

        form.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it.message, style = MaterialTheme.typography.bodySmall, color = RrrColors.Danger)
        }

        Spacer(Modifier.height(24.dp))
        GoldButton(
            text = "Create account",
            onClick = { viewModel.register(onRegistered) },
            enabled = form.canRegister,
            loading = form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    AuthScaffold("Reset password", onBack) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter the address on your account and we'll send a reset link.",
            style = MaterialTheme.typography.bodyMedium,
            color = RrrColors.Mist,
        )
        Spacer(Modifier.height(24.dp))
        RrrTextField(form.email, viewModel::onEmail, "Email address", keyboardType = KeyboardType.Email)

        form.info?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = RrrColors.Success)
        }
        form.error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it.message, style = MaterialTheme.typography.bodySmall, color = RrrColors.Danger)
        }

        Spacer(Modifier.height(24.dp))
        GoldButton(
            text = "Send reset link",
            onClick = viewModel::sendPasswordReset,
            enabled = form.emailValid && !form.submitting,
            loading = form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun VerifyEmailScreen(
    email: String,
    onVerified: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(RrrColors.Ink)
            .padding(Dimens.gutter),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Wordmark(compact = true)
        Spacer(Modifier.height(28.dp))
        Text("Confirm your email", style = MaterialTheme.typography.headlineSmall, color = RrrColors.Bone)
        Spacer(Modifier.height(10.dp))
        Text(
            "We sent a link to $email. Open it, then come back and tap continue. " +
                "Entries can't be purchased until the address is confirmed.",
            style = MaterialTheme.typography.bodyMedium,
            color = RrrColors.Mist,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        form.info?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = RrrColors.Success)
        }
        Spacer(Modifier.height(28.dp))
        GoldButton(
            text = "I've confirmed it",
            onClick = { viewModel.refreshVerification(onVerified) },
            loading = form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        QuietButton("Send it again", viewModel::resendVerification)
        QuietButton("Use a different account", { viewModel.signOut(); onSignOut() })
    }
}

@Composable
fun RrrTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    singleLine: Boolean = true,
    minLines: Int = 1,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        supportingText = supportingText?.let { { Text(it) } },
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RrrColors.Gold,
            unfocusedBorderColor = RrrColors.Hairline,
            focusedLabelColor = RrrColors.Gold,
            unfocusedLabelColor = RrrColors.Mist,
            focusedTextColor = RrrColors.Bone,
            unfocusedTextColor = RrrColors.Bone,
            cursorColor = RrrColors.Gold,
            focusedContainerColor = RrrColors.Surface,
            unfocusedContainerColor = RrrColors.Surface,
        ),
    )
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }, modifier = Modifier.size(Dimens.minTouchTarget)) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                    tint = RrrColors.Mist,
                )
            }
        },
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RrrColors.Gold,
            unfocusedBorderColor = RrrColors.Hairline,
            focusedLabelColor = RrrColors.Gold,
            unfocusedLabelColor = RrrColors.Mist,
            focusedTextColor = RrrColors.Bone,
            unfocusedTextColor = RrrColors.Bone,
            cursorColor = RrrColors.Gold,
            focusedContainerColor = RrrColors.Surface,
            unfocusedContainerColor = RrrColors.Surface,
        ),
    )
}

@Composable
fun ConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = RrrColors.Gold,
                uncheckedColor = RrrColors.Slate,
                checkmarkColor = RrrColors.Ink,
            ),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = RrrColors.Mist,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        if (actionLabel != null && onAction != null) QuietButton(actionLabel, onAction)
    }
}
