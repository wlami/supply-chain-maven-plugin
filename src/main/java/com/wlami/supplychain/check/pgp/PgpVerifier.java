package com.wlami.supplychain.check.pgp;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public final class PgpVerifier {

    public boolean verify(byte[] data, byte[] armoredSignature, byte[] armoredPublicKey)
            throws IOException, PGPException {
        PGPSignature signature = readSignature(armoredSignature);
        if (signature == null) return false;
        PGPPublicKey key = findKey(armoredPublicKey, signature.getKeyID());
        if (key == null) return false;

        signature.init(new BcPGPContentVerifierBuilderProvider(), key);
        signature.update(data);
        return signature.verify();
    }

    private PGPSignature readSignature(byte[] armored) throws IOException {
        try (InputStream in = PGPUtil.getDecoderStream(new ByteArrayInputStream(armored))) {
            JcaPGPObjectFactory factory = new JcaPGPObjectFactory(in);
            Object o = factory.nextObject();
            if (o instanceof PGPCompressedData) {
                factory = new JcaPGPObjectFactory(((PGPCompressedData) o).getDataStream());
                o = factory.nextObject();
            }
            if (o instanceof PGPSignatureList) {
                PGPSignatureList list = (PGPSignatureList) o;
                if (list.isEmpty()) return null;
                return list.get(0);
            }
            return null;
        } catch (PGPException e) {
            throw new IOException(e);
        }
    }

    private PGPPublicKey findKey(byte[] armoredKey, long keyId) throws IOException, PGPException {
        try (InputStream in = new ArmoredInputStream(new ByteArrayInputStream(armoredKey))) {
            PGPPublicKeyRingCollection rings =
                new PGPPublicKeyRingCollection(in, new BcKeyFingerprintCalculator());
            for (Iterator<PGPPublicKeyRing> rIt = rings.getKeyRings(); rIt.hasNext(); ) {
                PGPPublicKeyRing ring = rIt.next();
                for (Iterator<PGPPublicKey> kIt = ring.getPublicKeys(); kIt.hasNext(); ) {
                    PGPPublicKey k = kIt.next();
                    if (k.getKeyID() == keyId) return k;
                }
            }
            return null;
        }
    }
}
