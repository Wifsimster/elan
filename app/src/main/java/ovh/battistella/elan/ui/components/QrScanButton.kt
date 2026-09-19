package ovh.battistella.elan.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import ovh.battistella.elan.ui.icons.MdiIcons

/**
 * Bouton « Scanner un QR code » adossé au scanner de codes de Google Play
 * Services : l'interface de capture appartient aux services Play, l'app ne
 * déclare aucune permission CAMERA et ne voit que le texte décodé. Masqué
 * quand les services Play sont absents (appareil sans GMS) — la saisie
 * manuelle reste possible.
 */
@Composable
fun QrScanButton(
    title: String,
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val context = LocalContext.current
    val available = remember(context) {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }
    if (!available) return

    PulseButton(
        title = title,
        icon = MdiIcons.QrcodeScan,
        variant = ButtonVariant.Secondary,
        color = color,
        modifier = modifier,
        onClick = {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            GmsBarcodeScanning.getClient(context, options)
                .startScan()
                .addOnSuccessListener { barcode -> barcode.rawValue?.let(onScanned) }
            // Annulation ou échec : rien à faire, l'utilisateur garde les champs.
        },
    )
}
