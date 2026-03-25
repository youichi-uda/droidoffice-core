package com.droidoffice.core.license

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.UUID

/**
 * Generates JWT license keys. Used by the license issuing server (Gumroad webhook handler).
 * NOT included in the client library — this is a server-side tool.
 */
object LicenseKeyGenerator {

    /**
     * Generate a new RSA-2048 key pair for license signing.
     */
    fun generateKeyPair(): KeyPair {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        return gen.generateKeyPair()
    }

    /**
     * Generate a signed JWT license key.
     */
    fun generateLicenseKey(
        privateKey: RSAPrivateKey,
        product: String,
        plan: LicensePlan,
        licensee: String,
        expiresAt: LocalDate,
    ): String {
        val expDate = Date.from(expiresAt.atStartOfDay(ZoneId.systemDefault()).toInstant())

        val claims = JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .subject(licensee)
            .issuer("droidoffice")
            .claim("product", product)
            .claim("plan", plan.name.lowercase())
            .expirationTime(expDate)
            .issueTime(Date())
            .build()

        val header = JWSHeader.Builder(JWSAlgorithm.RS256).build()
        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(RSASSASigner(privateKey))

        return signedJwt.serialize()
    }

    /**
     * Encode a public key to Base64 for embedding in the library.
     */
    fun encodePublicKey(publicKey: RSAPublicKey): String {
        return java.util.Base64.getEncoder().encodeToString(publicKey.encoded)
    }

    /**
     * Decode a Base64-encoded public key.
     */
    fun decodePublicKey(base64: String): RSAPublicKey {
        val bytes = java.util.Base64.getDecoder().decode(base64)
        val keySpec = java.security.spec.X509EncodedKeySpec(bytes)
        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec) as RSAPublicKey
    }
}
