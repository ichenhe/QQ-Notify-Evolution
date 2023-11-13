package cc.chenhe.qqnotifyevo.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cc.chenhe.qqnotifyevo.ui.theme.AppTheme


@Composable
@Preview
private fun ErrorCardPreview() {
    AppTheme {
        ErrorCard(title = "Error", description = "message", button = {
            TextButton(
                onClick = { },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Text(text = "Confirm")
            }
        })
    }
}

@Composable
internal fun ErrorCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.ErrorOutline,
    description: String? = null,
    button: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.errorContainer,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (!description.isNullOrEmpty()) {
                        Text(
                            text = description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            if (button != null) {
                button()
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}