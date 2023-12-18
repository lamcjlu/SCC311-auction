import java.io.FileOutputStream;
import java.security.PublicKey;
import java.util.Base64;

// Method to write a public key to a file.
// Example use: storePublicKey(aPublicKey, ‘../keys/serverKey.pub’)
public class storePublicKey {
    public void storePublicKey(PublicKey publicKey, String filePath) throws Exception {
        // Convert the public key to a byte array
        byte[] publicKeyBytes = publicKey.getEncoded();
        // Encode the public key bytes as Base64
        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyBytes);
        // Write the Base64 encoded public key to a file
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(publicKeyBase64.getBytes());
        }
    }
}