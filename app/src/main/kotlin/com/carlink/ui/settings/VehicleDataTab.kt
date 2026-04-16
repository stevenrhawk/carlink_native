package com.carlink.ui.settings

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carlink.platform.VehicleDataReader
import com.carlink.platform.VehicleDataReader.PropertyResult
import com.carlink.ui.theme.AutomotiveDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vehicle Data diagnostics tab for the settings screen.
 *
 * Reads vehicle properties from CarPropertyManager and displays results.
 * Shows which properties are readable, permission-denied, or unavailable.
 * This is a diagnostic tool to determine what vehicle data could be forwarded
 * to CarPlay via the Carlinkit dongle.
 */
@Composable
fun VehicleDataTab() {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    var results by remember { mutableStateOf<List<PropertyResult>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var lastReadTime by remember { mutableStateOf<Long?>(null) }

    val reader = remember { VehicleDataReader(context) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 800.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header card with scan button
            VehicleCard(
                title = "Vehicle Properties",
                icon = Icons.Default.DirectionsCar,
            ) {
                Text(
                    text = "Reads vehicle data from AAOS CarPropertyManager. " +
                        "Shows which properties are accessible to this app on your GM head unit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            scope.launch {
                                isLoading = true
                                results = withContext(Dispatchers.IO) {
                                    reader.readAll()
                                }
                                lastReadTime = System.currentTimeMillis()
                                isLoading = false
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.height(AutomotiveDimens.ButtonMinHeight),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (results == null) "Scan Properties" else "Rescan")
                    }

                    if (results != null) {
                        val okCount = results!!.count { it.status == PropertyResult.Status.OK }
                        val total = results!!.size
                        Text(
                            text = "$okCount / $total readable",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Results
            if (results != null) {
                // Readable properties
                val readable = results!!.filter { it.status == PropertyResult.Status.OK }
                if (readable.isNotEmpty()) {
                    VehicleCard(
                        title = "Readable (${readable.size})",
                        icon = Icons.Default.CheckCircle,
                    ) {
                        readable.forEachIndexed { index, result ->
                            PropertyRow(result)
                            if (index < readable.lastIndex) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // Permission denied
                val denied = results!!.filter { it.status == PropertyResult.Status.PERMISSION_DENIED }
                if (denied.isNotEmpty()) {
                    VehicleCard(
                        title = "Permission Denied (${denied.size})",
                        icon = Icons.Default.Lock,
                    ) {
                        Text(
                            text = "These properties exist but require system-level permissions " +
                                "that GM has not granted to third-party apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        denied.forEachIndexed { index, result ->
                            PropertyRow(result)
                            if (index < denied.lastIndex) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // Unavailable
                val unavailable = results!!.filter {
                    it.status == PropertyResult.Status.UNAVAILABLE
                }
                if (unavailable.isNotEmpty()) {
                    VehicleCard(
                        title = "Not Available (${unavailable.size})",
                        icon = Icons.Default.RemoveCircleOutline,
                    ) {
                        Text(
                            text = "These properties are not supported on this vehicle hardware.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        unavailable.forEachIndexed { index, result ->
                            PropertyRow(result)
                            if (index < unavailable.lastIndex) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                // Errors (including Car not connected)
                val errors = results!!.filter {
                    it.status == PropertyResult.Status.ERROR ||
                        it.status == PropertyResult.Status.CAR_NOT_CONNECTED
                }
                if (errors.isNotEmpty()) {
                    VehicleCard(
                        title = "Errors (${errors.size})",
                        icon = Icons.Default.Error,
                    ) {
                        errors.forEachIndexed { index, result ->
                            PropertyRow(result)
                            if (index < errors.lastIndex) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PropertyRow(result: PropertyResult) {
    val colorScheme = MaterialTheme.colorScheme

    val (statusColor, statusIcon) = when (result.status) {
        PropertyResult.Status.OK -> colorScheme.primary to Icons.Default.CheckCircle
        PropertyResult.Status.PERMISSION_DENIED -> colorScheme.error to Icons.Default.Lock
        PropertyResult.Status.UNAVAILABLE -> colorScheme.onSurfaceVariant to Icons.Default.RemoveCircleOutline
        PropertyResult.Status.ERROR,
        PropertyResult.Status.CAR_NOT_CONNECTED -> colorScheme.error to Icons.Default.Error
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = result.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = result.displayValue,
            style = MaterialTheme.typography.bodyMedium,
            color = if (result.status == PropertyResult.Status.OK) {
                colorScheme.onSurface
            } else {
                colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VehicleCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            content()
        }
    }
}
