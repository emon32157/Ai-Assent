package app.shilex.android.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.shilex.android.Branding
import app.shilex.android.R
import app.shilex.android.control.ControlService

/**
 * Asks the user to switch on phone control (Accessibility), with the prominent disclosure
 * Google requires and the "restricted settings" step sideloaded apps need on Android 13+.
 */
@Composable
fun ControlCard(modifier: Modifier = Modifier, showWhenOn: Boolean = false) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(controlEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = controlEnabled(context)
        onPauseOrDispose { }
    }
    if (enabled) {
        if (showWhenOn) {
            InfoRow(stringResource(R.string.control_card_title), stringResource(R.string.control_card_on_desc)) {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text(stringResource(R.string.action_open)) }
            }
        }
        return
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Text(stringResource(R.string.control_card_off_title), color = Accent, fontSize = 15.sp)
        Text(
            stringResource(R.string.control_card_off_desc, Branding.ASSISTANT_NAME),
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Row {
            Button(onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text(stringResource(R.string.action_turn_on)) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Sideloaded apps: Android greys the switch out until "Allow restricted settings".
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text(stringResource(R.string.control_card_switch_greyed), color = Muted) }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Text(
                stringResource(R.string.control_card_restricted_hint),
                color = Muted,
                fontSize = 12.sp,
            )
        }
    }
}

private fun controlEnabled(context: Context): Boolean {
    if (ControlService.instance != null) return true
    val wanted = ComponentName(context, ControlService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    return enabled.split(':').any { it.equals(wanted, ignoreCase = true) }
}
