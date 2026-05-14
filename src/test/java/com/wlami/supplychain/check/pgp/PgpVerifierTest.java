package com.wlami.supplychain.check.pgp;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.sig.Features;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.PGPKeyPair;
import org.bouncycastle.openpgp.PGPKeyRingGenerator;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator;
import org.bouncycastle.openpgp.operator.PBESecretKeyEncryptor;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Date;
import static org.assertj.core.api.Assertions.assertThat;

class PgpVerifierTest {

    private static byte[] payload;
    private static byte[] publicKeyArmored;
    private static byte[] signatureArmored;

    @BeforeAll
    static void generateFixtures() throws Exception {
        // Generate RSA keypair using BC's lightweight API.
        RSAKeyPairGenerator gen = new RSAKeyPairGenerator();
        gen.init(new RSAKeyGenerationParameters(
            BigInteger.valueOf(0x10001), new SecureRandom(), 2048, 12));
        AsymmetricCipherKeyPair kp = gen.generateKeyPair();
        Date now = new Date();
        PGPKeyPair pgpKp = new BcPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, kp, now);

        PGPSignatureSubpacketGenerator subpackets = new PGPSignatureSubpacketGenerator();
        subpackets.setKeyFlags(false, KeyFlags.SIGN_DATA);
        subpackets.setFeature(false, Features.FEATURE_MODIFICATION_DETECTION);

        PBESecretKeyEncryptor encryptor = new BcPBESecretKeyEncryptorBuilder(
            SymmetricKeyAlgorithmTags.AES_128,
            new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1))
            .build("".toCharArray());

        PGPKeyRingGenerator ringGen = new PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            pgpKp,
            "test@example.com",
            new BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1),
            subpackets.generate(),
            null,
            new BcPGPContentSignerBuilder(pgpKp.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256),
            encryptor);

        PGPPublicKeyRing publicRing = ringGen.generatePublicKeyRing();
        PGPSecretKeyRing secretRing = ringGen.generateSecretKeyRing();

        // Export public key armored.
        ByteArrayOutputStream pubOut = new ByteArrayOutputStream();
        try (ArmoredOutputStream armored = new ArmoredOutputStream(pubOut)) {
            publicRing.encode(armored);
        }
        publicKeyArmored = pubOut.toByteArray();

        // Payload to sign.
        payload = "test-payload-1\n".getBytes();

        // Detached signature.
        PGPSecretKey signingKey = secretRing.getSecretKey();
        PGPSignatureGenerator sigGen = new PGPSignatureGenerator(
            new BcPGPContentSignerBuilder(signingKey.getPublicKey().getAlgorithm(), HashAlgorithmTags.SHA256));
        sigGen.init(PGPSignature.BINARY_DOCUMENT,
            signingKey.extractPrivateKey(
                new org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder(
                    new BcPGPDigestCalculatorProvider())
                    .build("".toCharArray())));
        sigGen.update(payload);
        PGPSignature sig = sigGen.generate();

        ByteArrayOutputStream sigOut = new ByteArrayOutputStream();
        try (ArmoredOutputStream armored = new ArmoredOutputStream(sigOut)) {
            sig.encode(armored);
        }
        signatureArmored = sigOut.toByteArray();
    }

    @Test
    void verifiesValidSignature() throws Exception {
        boolean ok = new PgpVerifier().verify(payload, signatureArmored, publicKeyArmored);
        assertThat(ok).isTrue();
    }

    @Test
    void rejectsTamperedData() throws Exception {
        boolean ok = new PgpVerifier().verify("tampered\n".getBytes(), signatureArmored, publicKeyArmored);
        assertThat(ok).isFalse();
    }
}
