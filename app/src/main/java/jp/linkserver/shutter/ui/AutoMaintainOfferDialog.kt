package jp.linkserver.shutter.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import jp.linkserver.shutter.R

@Composable
internal fun AutoMaintainOfferDialog(onChoice: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoice(false) },
        icon = { Icon(painterResource(R.drawable.ic_restore), null) },
        title = { Text(stringResource(R.string.auto_maintain_offer_title)) },
        text = { Text(stringResource(R.string.auto_maintain_offer_description)) },
        dismissButton = {
            TextButton(onClick = { onChoice(false) }) {
                Text(stringResource(R.string.auto_maintain_offer_later))
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoice(true) }) {
                Text(stringResource(R.string.auto_maintain_offer_enable))
            }
        }
    )
}
