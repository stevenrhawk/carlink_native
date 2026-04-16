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
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carlink.platform.PropertyResult
import com.carlink.platform.VehicleData
import com.carlink.platform.VehicleDataFormatter
import com.carlink.platform.VehicleDataManager
import com.carlink.ui.theme.AutomotiveDimens

@Composable
fun VehicleDataTab() {
    val view = LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val vehicleData by VehicleDataManager.state.collectAsStateWithLifecycle()

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
            when (vehicleData.status) {
                VehicleData.ConnectionStatus.WAITING_FOR_SERVICE -> {
                    VehicleCard(
                        title = "Vehicle Properties",
                        icon = Icons.Default.DirectionsCar,
                    ) {
                        Text(
                            text = "Waiting for vehicle data service to connect. " +
                                "The Templates Host needs to bind to the Carlink service.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }

                VehicleData.ConnectionStatus.ERROR -> {
                    VehicleCard(
                        title = "Vehicle Properties",
                        icon = Icons.Default.Error,
                    ) {
                        Text(
                            text = "Failed to access vehicle hardware: ${vehicleData.errorMessage ?: "unknown error"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.error,
                        )
                    }
                }

                VehicleData.ConnectionStatus.CONNECTED -> {
                    val results = VehicleDataFormatter.format(vehicleData)

                    // Header card with refresh button
                    VehicleCard(
                        title = "Vehicle Properties",
                        icon = Icons.Default.DirectionsCar,
                    ) {
                        Text(
                            text = "Live vehicle data from the Car App Library hardware APIs. " +
                                "Speed, energy, and mileage update continuously.",
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
                                    VehicleDataManager.requestRefresh()
                                },
                                modifier = Modifier.height(AutomotiveDimens.ButtonMinHeight),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Refresh")
                            }

                            val okCount = results.count { it.status == PropertyResult.Status.OK }
                            val total = results.size
                            Text(
                                text = "$okCount / $total readable",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Readable properties
                    val readable = results.filter { it.status == PropertyResult.Status.OK }
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

                    // Pending (still waiting for data)
                    val pending = results.filter { it.status == PropertyResult.Status.PENDING }
                    if (pending.isNotEmpty()) {
                        VehicleCard(
                            title = "Pending (${pending.size})",
                            icon = Icons.Default.HourglassEmpty,
                        ) {
                            pending.forEachIndexed { index, result ->
                                PropertyRow(result)
                                if (index < pending.lastIndex) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }

                    // Unavailable / Unimplemented
                    val unavailable = results.filter {
                        it.status == PropertyResult.Status.UNAVAILABLE ||
                            it.status == PropertyResult.Status.UNIMPLEMENTED
                    }
                    if (unavailable.isNotEmpty()) {
                        VehicleCard(
                            title = "Not Available (${unavailable.size})",
                            icon = Icons.Default.RemoveCircleOutline,
                        ) {
                            Text(
                                text = "These properties are not supported by the vehicle or Templates Host.",
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

                    // Errors
                    val errors = results.filter { it.status == PropertyResult.Status.ERROR }
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
        PropertyResult.Status.UNAVAILABLE,
        PropertyResult.Status.UNIMPLEMENTED -> colorScheme.onSurfaceVariant to Icons.Default.RemoveCircleOutline
        PropertyResult.Status.ERROR -> colorScheme.error to Icons.Default.Error
        PropertyResult.Status.PENDING -> colorScheme.onSurfaceVariant to Icons.Default.HourglassEmpty
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
