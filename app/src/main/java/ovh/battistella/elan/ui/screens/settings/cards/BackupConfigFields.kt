package ovh.battistella.elan.ui.screens.settings.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ovh.battistella.elan.R
import ovh.battistella.elan.data.settings.DEFAULT_BACKUP_OBJECT_KEY
import ovh.battistella.elan.data.settings.DEFAULT_S3_REGION
import ovh.battistella.elan.ui.components.QrScanButton
import ovh.battistella.elan.ui.components.SettingField
import ovh.battistella.elan.ui.components.pressableScale
import ovh.battistella.elan.ui.icons.MdiIcons
import ovh.battistella.elan.ui.screens.settings.BackupFormConfig
import ovh.battistella.elan.ui.screens.settings.BackupPatch
import ovh.battistella.elan.ui.theme.ElanTheme
import ovh.battistella.elan.ui.theme.ElanType

/**
 * Champs de connexion S3, partagés par la carte Réglages et la feuille de
 * restauration du premier lancement (mêmes libellés, mêmes placeholders, même
 * persistance). Quatre champs suffisent (endpoint, bucket, clés) ; région et
 * nom d'objet ont des défauts qui conviennent à MinIO/SeaweedFS/Garage et
 * restent modifiables sous « Options avancées », dépliées d'office si la
 * valeur stockée s'écarte du défaut.
 */
@Composable
fun BackupConfigFields(
    config: BackupFormConfig,
    onPatch: (BackupPatch) -> Unit,
    onQrScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ElanTheme.colors
    val hasAdvanced = (config.region.isNotEmpty() && config.region != DEFAULT_S3_REGION) ||
        (config.objectKey.isNotEmpty() && config.objectKey != DEFAULT_BACKUP_OBJECT_KEY)
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    val showAdvanced = advancedOpen || hasAdvanced

    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = modifier.fillMaxWidth()) {
        // Éviter de taper une clé secrète au clavier : QR généré sur le serveur.
        QrScanButton(
            title = stringResource(R.string.settings_backup_scan_qr),
            onScanned = onQrScanned,
            modifier = Modifier.fillMaxWidth(),
        )
        SettingField(
            label = stringResource(R.string.settings_backup_endpoint),
            placeholder = stringResource(R.string.settings_backup_endpoint_placeholder),
            value = config.endpoint,
            onValueChange = { onPatch(BackupPatch(endpoint = it)) },
            keyboardType = KeyboardType.Uri,
        )
        SettingField(
            label = stringResource(R.string.settings_backup_bucket),
            placeholder = stringResource(R.string.settings_backup_bucket_placeholder),
            value = config.bucket,
            onValueChange = { onPatch(BackupPatch(bucket = it)) },
        )
        SettingField(
            label = stringResource(R.string.settings_backup_access_key),
            placeholder = stringResource(R.string.settings_backup_access_key_placeholder),
            value = config.accessKeyId,
            onValueChange = { onPatch(BackupPatch(accessKeyId = it)) },
        )
        SettingField(
            label = stringResource(R.string.settings_backup_secret_key),
            placeholder = stringResource(R.string.settings_backup_secret_key_placeholder),
            value = config.secretAccessKey,
            onValueChange = { onPatch(BackupPatch(secretAccessKey = it)) },
            secret = true,
        )

        if (showAdvanced) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingField(
                    label = stringResource(R.string.settings_backup_region),
                    placeholder = DEFAULT_S3_REGION,
                    value = config.region,
                    onValueChange = { onPatch(BackupPatch(region = it)) },
                    modifier = Modifier.weight(1f),
                )
                SettingField(
                    label = stringResource(R.string.settings_backup_object_key),
                    placeholder = DEFAULT_BACKUP_OBJECT_KEY,
                    value = config.objectKey,
                    onValueChange = { onPatch(BackupPatch(objectKey = it)) },
                    modifier = Modifier.weight(1f),
                )
            }
            CardText(stringResource(R.string.settings_backup_advanced_hint, DEFAULT_S3_REGION, DEFAULT_BACKUP_OBJECT_KEY), muted = true, size = 12)
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .semantics { role = Role.Button }
                    .pressableScale(haptic = null, onClick = { advancedOpen = true })
                    .padding(4.dp),
            ) {
                Icon(painter = painterResource(MdiIcons.ChevronRight), contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.settings_backup_advanced),
                    color = colors.link,
                    style = ElanType.label,
                )
            }
        }
    }
}
