package io.github.thisisartman.collectibles.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.thisisartman.collectibles.R
import io.github.thisisartman.collectibles.data.AutoBackup
import io.github.thisisartman.collectibles.data.ServerError
import io.github.thisisartman.collectibles.data.ServerLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where weekly backups go: a folder, one file (Google Drive), or a server. */
@Composable
fun WhereDialog(onPick: (AutoBackup.Where) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.where_title)) },
        text = {
            Column {
                listOf(
                    Triple(AutoBackup.Where.FOLDER, R.string.where_folder, R.string.where_folder_sub),
                    Triple(AutoBackup.Where.FILE, R.string.where_file, R.string.where_file_sub),
                    Triple(AutoBackup.Where.SERVER, R.string.where_server, R.string.where_server_sub),
                ).forEach { (where, title, sub) ->
                    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onPick(where) }.padding(vertical = 8.dp)) {
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Signing in to a WebDAV server. Connect checks the folder (and creates it if it's missing); a home server's own
 * certificate is shown by its fingerprint and trusted only when the user says so.
 */
@Composable
fun ServerDialog(saved: ServerLogin?, onSaved: (ServerLogin) -> Unit, onRemove: (() -> Unit)?, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(saved?.url.orEmpty()) }
    var user by remember { mutableStateOf(saved?.user.orEmpty()) }
    var password by remember { mutableStateOf(saved?.password.orEmpty()) }
    var trusted by remember { mutableStateOf(saved?.trusted) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var askTrust by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val errHttps = stringResource(R.string.server_err_https)
    val errAddress = stringResource(R.string.server_err_address)
    val errLogin = stringResource(R.string.server_err_login)
    val errFolder = stringResource(R.string.server_err_folder)
    val errOffline = stringResource(R.string.server_err_offline)
    val errOther = stringResource(R.string.server_err_other)

    fun connect(trust: String?) {
        busy = true; error = null
        scope.launch {
            val login = ServerLogin(url.trim(), user.trim(), password, trust)
            val result = withContext(Dispatchers.IO) { runCatching { login.client().ensureFolder() } }
            busy = false
            result.onSuccess { onSaved(login) }.onFailure { e ->
                when (e) {
                    is ServerError.Untrusted -> askTrust = e.sha256
                    is ServerError.NotHttps -> error = errHttps
                    is ServerError.BadAddress -> error = errAddress
                    is ServerError.WrongLogin -> error = errLogin
                    is ServerError.NotFound -> error = errFolder
                    is ServerError.Http -> error = errOther.format(e.code)
                    else -> error = errOffline
                }
            }
        }
    }

    askTrust?.let { sha ->
        AlertDialog(
            onDismissRequest = { askTrust = null },
            title = { Text(stringResource(R.string.trust_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.trust_body))
                    Text(sha, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            },
            confirmButton = { TextButton(onClick = { askTrust = null; trusted = sha; connect(sha) }) { Text(stringResource(R.string.trust_ok)) } },
            dismissButton = { TextButton(onClick = { askTrust = null }) { Text(stringResource(R.string.cancel)) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.server_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    url, { url = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.server_address)) },
                    placeholder = { Text(stringResource(R.string.server_address_hint), style = MaterialTheme.typography.bodySmall) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.server_user)) })
                OutlinedTextField(
                    password, { password = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.server_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Text(stringResource(R.string.server_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) CircularProgressIndicator(Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && url.isNotBlank() && user.isNotBlank() && password.isNotEmpty(), onClick = { connect(trusted) }) {
                Text(stringResource(R.string.server_connect))
            }
        },
        dismissButton = {
            Column {
                if (onRemove != null) TextButton(enabled = !busy, onClick = onRemove) { Text(stringResource(R.string.server_remove)) }
                TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
