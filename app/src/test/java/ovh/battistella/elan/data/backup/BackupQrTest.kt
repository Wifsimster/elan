package ovh.battistella.elan.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ovh.battistella.elan.data.backup.BackupField.ACCESS_KEY_ID
import ovh.battistella.elan.data.backup.BackupField.BUCKET
import ovh.battistella.elan.data.backup.BackupField.ENDPOINT
import ovh.battistella.elan.data.backup.BackupField.OBJECT_KEY
import ovh.battistella.elan.data.backup.BackupField.REGION
import ovh.battistella.elan.data.backup.BackupField.SECRET_ACCESS_KEY

/** Port de `__tests__/lib/backup-qr.test.ts`. Robolectric pour `org.json`. */
@RunWith(RobolectricTestRunner::class)
class BackupQrTest {

    @Test
    fun `lit un JSON complet aux clés canoniques`() {
        assertEquals(
            mapOf(
                ENDPOINT to "https://s3.x.tld", BUCKET to "elan", ACCESS_KEY_ID to "AK", SECRET_ACCESS_KEY to "SK",
                REGION to "eu-west-1", OBJECT_KEY to "o.json",
            ),
            parseBackupQr(
                """{"endpoint":"https://s3.x.tld","bucket":"elan","accessKeyId":"AK","secretAccessKey":"SK","region":"eu-west-1","objectKey":"o.json"}""",
            ),
        )
    }

    @Test
    fun `accepte les alias snake_case et AWS, insensibles à la casse`() {
        assertEquals(
            mapOf(ENDPOINT to "https://s3.x.tld", ACCESS_KEY_ID to "AK", SECRET_ACCESS_KEY to "SK"),
            parseBackupQr("""{"URL":"https://s3.x.tld","access_key":"AK","AWS_SECRET_ACCESS_KEY":"SK"}"""),
        )
    }

    @Test
    fun `ignore les champs vides, non-texte ou inconnus, et nettoie les espaces`() {
        assertEquals(
            mapOf(ACCESS_KEY_ID to "AK"),
            parseBackupQr("""{"accessKeyId":" AK ","secretAccessKey":"","bucket":3,"foo":"bar"}"""),
        )
    }

    @Test
    fun `lit la forme s3 ACCESS SECRET hôte bucket objet (identifiants encodés)`() {
        assertEquals(
            mapOf(
                ENDPOINT to "https://s3.x.tld", BUCKET to "elan", ACCESS_KEY_ID to "AK", SECRET_ACCESS_KEY to "s/k+x",
                OBJECT_KEY to "backup.json",
            ),
            parseBackupQr("s3://AK:s%2Fk%2Bx@s3.x.tld/elan/backup.json"),
        )
        assertEquals(mapOf(ENDPOINT to "https://s3.x.tld", BUCKET to "elan"), parseBackupQr("s3://s3.x.tld/elan"))
        assertEquals(
            mapOf(ENDPOINT to "https://h:9000", BUCKET to "b", ACCESS_KEY_ID to "AK", OBJECT_KEY to "d/o.json"),
            parseBackupQr("s3://AK@h:9000/b/d/o.json"),
        )
        // Un pourcentage mal formé est gardé tel quel (repli de decodeURIComponent).
        assertEquals("s%ZZ", parseBackupQr("s3://AK:s%ZZ@h/b")!![SECRET_ACCESS_KEY])
    }

    @Test
    fun `renvoie null pour un QR étranger`() {
        assertNull(parseBackupQr("https://example.com"))
        assertNull(parseBackupQr("""{"foo":"bar"}"""))
        assertNull(parseBackupQr("[1,2]"))
        assertNull(parseBackupQr("   "))
        assertNull(parseBackupQr("{not json"))
    }

    @Test
    fun `décrit les champs remplis en français, dans l'ordre du QR`() {
        assertEquals(
            "access key, secret key, endpoint",
            describeQrPatch(linkedMapOf(ACCESS_KEY_ID to "a", SECRET_ACCESS_KEY to "b", ENDPOINT to "c")),
        )
        assertEquals(
            "access key, secret key, endpoint",
            describeQrPatch(parseBackupQr("""{"accessKeyId":"a","secretAccessKey":"b","endpoint":"c"}""")!!),
        )
        assertEquals("région, nom de l'objet, bucket", describeQrPatch(linkedMapOf(REGION to "r", OBJECT_KEY to "o", BUCKET to "b")))
    }
}
