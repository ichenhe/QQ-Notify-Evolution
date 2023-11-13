package cc.chenhe.qqnotifyevo.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cc.chenhe.qqnotifyevo.R
import cc.chenhe.qqnotifyevo.ui.theme.AppTheme

@Composable
@Preview(showBackground = true)
private fun PreferencePreview() {
    AppTheme {
        PreferenceGroup(groupTitle = "常规") {
            PreferenceItem(
                title = "工作模式",
                icon = Icons.Default.Star,
                description = "传统",
                modifier = Modifier.fillMaxWidth()
            )
            PreferenceDivider()
            PreferenceItem(
                title = "工作模式",
                icon = Icons.Default.Star,
                description = "传统",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun PreferenceGroup(groupTitle: String?, content: @Composable ColumnScope.() -> Unit) {
    Column {
        if (groupTitle != null) {
            Text(
                text = groupTitle,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Card(content = content, modifier = Modifier.animateContentSize())
    }
}

@Composable
internal fun PreferenceGroupInterval() {
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
internal fun PreferenceDivider() {
    Divider(modifier = Modifier.padding(horizontal = 48.dp))
}

@Composable
internal fun PreferenceItem(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
    descriptionModifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    button: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = descriptionModifier
                )
            }

        }
        if (button != null) {
            Spacer(modifier = Modifier.width(8.dp))
            button()
        }
    }
}

@Composable
internal fun SingleSelectionDialog(
    icon: @Composable (() -> Unit)? = null,
    title: String,
    data: List<String>,
    currentSelectedIndex: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        title = { Text(text = title) },
        icon = icon,
        text = {
            LazyColumn {
                itemsIndexed(data) { i, opt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelected(i) }) {
                        RadioButton(
                            selected = i == currentSelectedIndex,
                            onClick = null,
                            Modifier.minimumInteractiveComponentSize()
                        )
                        Text(text = opt)
                    }
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm() }) {
                Text(text = stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}