package com.eirahoutmoss.nextools.idcard

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CardServiceException
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jmrtd.BACKey
import org.jmrtd.PassportService
import org.jmrtd.lds.CardAccessFile
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.iso19794.FaceImageInfo
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.security.MessageDigest
import java.security.Security

/**
 * ICAO 9303 eMRTD okuyucu (JMRTD). Erişim: önce PACE, olmazsa BAC. Okunan: DG1 (MRZ bilgileri),
 * DG2 (yüz fotoğrafı), SOD (özetler + imza). Ülke kök sertifikası (CSCA) zinciri denetlenmez.
 */
object JmrtdReader {
    private val bc = BouncyCastleProvider()

    private fun ensureProvider() {
        // Android'in kırpılmış "BC" sağlayıcısı PACE için gereken algoritmaları içermez.
        val cur = Security.getProvider("BC")
        if (cur == null || cur.javaClass.name != BouncyCastleProvider::class.java.name) {
            Security.removeProvider("BC")
            Security.insertProviderAt(bc, 1)
        }
    }

    fun read(tag: Tag, documentNumber: String, birthYyMMdd: String, expiryYyMMdd: String, onStep: (IdStep) -> Unit): IdCardData {
        ensureProvider()
        val iso = IsoDep.get(tag) ?: throw IdCardException(IdError.NOT_EMRTD)
        iso.timeout = 10_000
        try {
            onStep(IdStep.CONNECTING)
            val card = CardService.getInstance(iso)
            card.open()
            val service = PassportService(card, PassportService.NORMAL_MAX_TRANCEIVE_LENGTH, PassportService.DEFAULT_MAX_BLOCKSIZE, false, false)
            service.open()
            val key = BACKey(documentNumber.uppercase().trim(), birthYyMMdd, expiryYyMMdd)

            onStep(IdStep.ACCESS)
            var method = "BAC"
            var pace = false
            try {
                val access = CardAccessFile(service.getInputStream(PassportService.EF_CARD_ACCESS))
                for (info in access.securityInfos) {
                    if (info is PACEInfo) {
                        service.doPACE(key, info.objectIdentifier, PACEInfo.toParameterSpec(info.parameterId), null)
                        pace = true; method = "PACE"
                        break
                    }
                }
            } catch (_: Exception) { /* kartta PACE yok ya da başarısız: BAC denenecek */ }
            service.sendSelectApplet(pace)
            if (!pace) {
                try { service.getInputStream(PassportService.EF_COM).read() } catch (_: Exception) {
                    try { service.doBAC(key) } catch (e: CardServiceException) { throw IdCardException(IdError.ACCESS_DENIED, e) }
                }
            }

            onStep(IdStep.READING_DATA)
            val dg1Bytes = service.getInputStream(PassportService.EF_DG1).readBytes()
            val mrz = DG1File(ByteArrayInputStream(dg1Bytes)).mrzInfo

            onStep(IdStep.READING_PHOTO)
            var dg2Bytes: ByteArray? = null
            var photo: Bitmap? = null; var mime: String? = null; var photoLen = 0
            runCatching {
                val raw = service.getInputStream(PassportService.EF_DG2).readBytes()
                dg2Bytes = raw
                val dg2 = DG2File(ByteArrayInputStream(raw))
                val face: FaceImageInfo? = dg2.faceInfos.flatMap { it.faceImageInfos }.firstOrNull()
                if (face != null) {
                    photoLen = face.imageLength
                    val buf = ByteArray(photoLen)
                    DataInputStream(face.imageInputStream).readFully(buf, 0, photoLen)
                    mime = face.mimeType
                    photo = decodeImage(buf, face.mimeType)
                }
            }

            onStep(IdStep.VERIFYING)
            val passive = runCatching {
                val sodBytes = service.getInputStream(PassportService.EF_SOD).readBytes()
                verify(sodBytes, mapOfNotNull(1 to dg1Bytes, 2 to dg2Bytes))
            }.getOrElse { PassiveAuth(emptyMap(), null, null, null) }

            return IdCardData(
                accessMethod = method,
                documentCode = mrz.documentCode.orEmpty(),
                documentNumber = mrz.documentNumber.orEmpty(),
                issuingState = mrz.issuingState.orEmpty(),
                surname = mrz.primaryIdentifier.orEmpty().replace('<', ' ').trim(),
                givenNames = mrz.secondaryIdentifier.orEmpty().replace('<', ' ').trim(),
                nationality = mrz.nationality.orEmpty(),
                birthDate = mrz.dateOfBirth.orEmpty(),
                expiryDate = mrz.dateOfExpiry.orEmpty(),
                gender = mrz.gender?.toString().orEmpty(),
                personalNumber = runCatching { mrz.personalNumber }.getOrNull()?.replace("<", "")?.takeIf { it.isNotBlank() }
                    ?: runCatching { mrz.optionalData1 }.getOrNull()?.replace("<", "")?.takeIf { it.isNotBlank() },
                photo = photo, photoMime = mime, photoBytes = photoLen,
                passive = passive,
            )
        } catch (e: IdCardException) {
            throw e
        } catch (e: TagLostException) {
            throw IdCardException(IdError.TAG_LOST, e)
        } catch (e: CardServiceException) {
            throw IdCardException(if (e.sw == 0x6982 || e.sw == 0x6300) IdError.ACCESS_DENIED else IdError.UNKNOWN, e)
        } catch (e: Exception) {
            throw IdCardException(if (e.cause is TagLostException) IdError.TAG_LOST else IdError.UNKNOWN, e)
        } finally {
            runCatching { iso.close() }
        }
    }

    private fun <K, V> mapOfNotNull(vararg pairs: Pair<K, V?>): Map<K, V> = pairs.mapNotNull { (k, v) -> v?.let { k to it } }.toMap()

    /** Özetler (DG1, DG2) ve SOD içindeki CMS imzası. */
    private fun verify(sodBytes: ByteArray, groups: Map<Int, ByteArray>): PassiveAuth {
        val sod = SODFile(ByteArrayInputStream(sodBytes))
        val md = MessageDigest.getInstance(sod.digestAlgorithm)
        val expected = sod.dataGroupHashes
        val results = groups.mapValues { (dg, bytes) -> expected[dg]?.let { md.reset(); md.digest(bytes).contentEquals(it) } ?: false }
        // SOD = 0x77 etiketli kap; içi CMS ContentInfo
        val content = unwrapTlv(sodBytes)
        var sigOk: Boolean? = null; var signer: String? = null; var until: Long? = null
        runCatching {
            val cms = CMSSignedData(content)
            val si: SignerInformation = cms.signerInfos.signers.first()
            @Suppress("UNCHECKED_CAST")
            val holder = cms.certificates.getMatches(si.sid as org.bouncycastle.util.Selector<X509CertificateHolder>).firstOrNull()
            if (holder != null) {
                signer = holder.subject.toString()
                until = holder.notAfter.time
                sigOk = si.verify(JcaSimpleSignerInfoVerifierBuilder().setProvider(bc).build(holder))
            }
        }
        return PassiveAuth(results, sigOk, signer, until)
    }

    private fun unwrapTlv(b: ByteArray): ByteArray {
        if (b.size < 2 || (b[0].toInt() and 0xFF) != 0x77) return b
        val l0 = b[1].toInt() and 0xFF
        var start = 2; var len = l0
        if (l0 >= 0x80) {
            val n = l0 and 0x7F; len = 0
            for (i in 0 until n) len = (len shl 8) or (b[2 + i].toInt() and 0xFF)
            start = 2 + n
        }
        return b.copyOfRange(start, minOf(b.size, start + len))
    }

    /** JPEG doğrudan; JPEG 2000 için JP2 kitaplığı (sınıf adı sürüme göre değişebildiği için yansıma ile). */
    private fun decodeImage(bytes: ByteArray, mime: String?): Bitmap? {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it }
        if (mime != null && !mime.contains("jp2") && !mime.contains("jpeg2000")) return null
        for (cls in listOf("com.gemalto.jp2.JP2Decoder", "dev.keiji.jp2.JP2Decoder")) {
            val bmp = runCatching {
                val c = Class.forName(cls)
                val dec = c.getConstructor(ByteArray::class.java).newInstance(bytes)
                c.getMethod("decode").invoke(dec) as? Bitmap
            }.getOrNull()
            if (bmp != null) return bmp
        }
        return null
    }
}
