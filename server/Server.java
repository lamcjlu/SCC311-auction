// Server.java
import javax.crypto.SecretKey;
import javax.security.sasl.AuthenticationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import javax.crypto.KeyGenerator;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.nio.file.Paths;
import java.nio.file.Path;

public class Server implements Auction {
    private final Map<Integer, AuctionItem> auctionItems;
    private final Map<Integer, String> userInfo; // Maps user ID to email
    private final Map<Integer, PublicKey> userPublicKeys;
    private final Map<Integer, String> userChallenges;
    private final Map<Integer, TokenInfo> userTokens;
    private final Map<Integer, AuctionSaleItem> auctionSaleItems;

    // Additional data structures for auction logic
    private final Map<Integer, Integer> itemToHighestBidder; // Maps item ID to highest bidder ID
    private final Map<Integer, Integer> itemToHighestBid; // Maps item ID to highest bid value
    private final Map<Integer, Integer> auctionSaleItemToCreator; // Maps auction sale item ID to creator ID

    // Other fields
    private int itemIDCounter = 3;
    private int userIDCounter = 1;
    private SecretKey aesKey;
    private PrivateKey serverPrivateKey;
    private SecureRandom secureRandom;

    public Server() throws Exception {
        // Initialize maps
        this.auctionItems = new HashMap<>();
        this.userInfo = new HashMap<>();
        this.userPublicKeys = new HashMap<>();
        this.userChallenges = new HashMap<>();
        this.userTokens = new HashMap<>();
        this.auctionSaleItems = new HashMap<>();

        this.itemToHighestBidder = new HashMap<>();
        this.itemToHighestBid = new HashMap<>();
        this.auctionSaleItemToCreator = new HashMap<>();

        // Initialize other components
        initAuctionItems();
        loadAESKey();
        loadServerPrivateKey();
        secureRandom = new SecureRandom();
    }

    public static void main(String[] args) {
        try {
            Server s = new Server();
            String name = "Auction";
            Auction stub = (Auction) UnicastRemoteObject.exportObject(s, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(name, stub);
            System.out.println("Server ready");
        } catch (Exception e) {
            System.err.println("Exception:");
            e.printStackTrace();
        }
    }

    private void loadAESKey() throws Exception {
        Path path = Paths.get("../keys/testKey.aes");
        if (!Files.exists(path)) {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256); // Use a 256-bit AES key
            SecretKey newAesKey = keyGen.generateKey();

            try (FileOutputStream fos = new FileOutputStream(path.toFile());
                 ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeObject(newAesKey);
            }
        }

        try (FileInputStream fis = new FileInputStream(path.toFile());
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            aesKey = (SecretKey) ois.readObject();
        }
    }

    private void loadServerPrivateKey() throws Exception {
        Path privateKeyPath = Paths.get("../keys/serverKey.priv");

        if (!Files.exists(privateKeyPath)) {
            // Generate a new RSA key pair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair pair = keyGen.generateKeyPair();

            PrivateKey newPrivateKey = pair.getPrivate();
            PublicKey newPublicKey = pair.getPublic();

            // Store the private key using Base64 encoding
            storePrivateKey(newPrivateKey, privateKeyPath.toString());

            // Store the public key (assuming storePublicKey method uses Base64 encoding)
            storePublicKey(newPublicKey, "../keys/serverKey.pub");
        }

        // Load the private key from the file
        String keyBase64 = new String(Files.readAllBytes(privateKeyPath));
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        serverPrivateKey = kf.generatePrivate(spec);
    }


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

    private void storePrivateKey(PrivateKey privateKey, String filePath) throws Exception {
        // Convert the private key to a byte array
        byte[] privateKeyBytes = privateKey.getEncoded();
        // Encode the private key bytes as Base64
        String privateKeyBase64 = Base64.getEncoder().encodeToString(privateKeyBytes);
        // Write the Base64 encoded private key to a file
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(privateKeyBase64.getBytes());
        }
    }

    @Override
    public ChallengeInfo challenge(int userID, String clientChallenge) throws RemoteException {
        try {
            String serverChallenge = generateRandomChallenge();
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(serverPrivateKey);
            signature.update((clientChallenge + serverChallenge).getBytes()); // Combine challenges for signing
            byte[] signedData = signature.sign();

            userChallenges.put(userID, serverChallenge); // Store server challenge

            ChallengeInfo challengeInfo = new ChallengeInfo();
            challengeInfo.response = signedData;         // Set the response
            challengeInfo.clientChallenge = clientChallenge; // Set the client challenge

            return challengeInfo;
        } catch (Exception e) {
            throw new RemoteException("Error during challenge generation", e);
        }
    }


    @Override
    public TokenInfo authenticate(int userID, byte[] clientSignature) throws RemoteException {
        try {
            PublicKey pubKey = getUserPublicKey(userID);
            String serverChallenge = userChallenges.get(userID);

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(pubKey);
            signature.update(serverChallenge.getBytes());

            if (!signature.verify(clientSignature)) {
                throw new AuthenticationException("Invalid signature");
            }

            String token = generateRandomToken();
            long expiryTime = System.currentTimeMillis() + 60000; // Extend token expiry to 60 seconds

            TokenInfo tokenInfo = new TokenInfo();
            tokenInfo.token = token;
            tokenInfo.expiryTime = expiryTime;

            userTokens.put(userID, tokenInfo); // Store the token

            return tokenInfo;
        } catch (Exception e) {
            throw new RemoteException("Error during authentication", e);
        }
    }


    private void initAuctionItems() {
        auctionItems.put(1, new AuctionItem(1, "Vintage Watch", "A rare vintage watch from 1950s.", 1000));
        auctionItems.put(2, new AuctionItem(2, "Classic Book", "A first edition of a classic novel.", 500));
        auctionItems.put(3, new AuctionItem(3, "Sports Memorabilia", "A signed baseball from a famous player.", 750));
    }

    @Override
    public AuctionItem getSpec(int userID, int itemID, String token) throws RemoteException {
        if (!isValidToken(userID, token)) {
            throw new RemoteException("Invalid or expired token");
        } else {
            return auctionItems.get(itemID); // Retrieve the auction item specification
        }
    }

    @Override
    public Integer register(String email, PublicKey pubKey) throws RemoteException {
        // Prevent duplicate registrations
        if (userInfo.containsValue(email)) {
            throw new RemoteException("Email already registered");
        }
        int userID = generateUniqueUserID();
        userInfo.put(userID, email);
        userPublicKeys.put(userID, pubKey);
        return userID;
    }

    private synchronized int generateUniqueUserID() {
        return userIDCounter++; // Increment and return the counter
    }

    private PublicKey getUserPublicKey(int userID) {
        PublicKey pk = userPublicKeys.get(userID);
        if (pk == null) {
            try {
                throw new RemoteException("Public key not found for user " + userID);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
        return pk; // Retrieves the public key for the given user ID
    }

    private boolean isValidToken(int userID, String token) {
        TokenInfo tokenInfo = userTokens.get(userID);
        if (tokenInfo != null && tokenInfo.token.equals(token)) {
            // Validate one-time use by removing the token after validation
            userTokens.remove(userID);
            return tokenInfo.expiryTime > System.currentTimeMillis();
        }
        return false;
    }

    public Integer newAuction(int userID, AuctionSaleItem item, String token) throws RemoteException {
        if (!isValidToken(userID, token)) {
            throw new RemoteException("Invalid or expired token");
        } else {
            int itemID = generateUniqueItemID();
            auctionSaleItems.put(itemID, item); // Store the new auction sale item
            auctionSaleItemToCreator.put(itemID, userID); // Associate the item with the creator's userID
            return itemID;
        }
    }

    private int generateUniqueItemID() {
        return itemIDCounter++; // Increment and return the counter
    }

    public AuctionItem[] listItems(int userID, String token) throws RemoteException {
        if (!isValidToken(userID, token)) {
            throw new RemoteException("Invalid or expired token");
        } else {
            return auctionItems.values().toArray(new AuctionItem[0]);
        }
    }

    public AuctionResult closeAuction(int userID, int itemID, String token) throws RemoteException {
        if (!isValidToken(userID, token)) {
            throw new RemoteException("Invalid or expired token");
        } else {
            AuctionItem item = auctionItems.get(itemID);
            AuctionSaleItem saleItem = auctionSaleItems.get(itemID);
            if (saleItem == null) {
                throw new RemoteException("Item not found or not available for closure");
            }
            if (item != null && checkAccessControl(userID, itemID, AccessType.CLOSE_AUCTION)) {
                AuctionResult result = getAuctionResult(itemID);
                auctionItems.remove(itemID); // Remove the item as the auction is now closed
                return result;
            }
            throw new RemoteException("Unable to close auction. Either item does not exist or access is denied.");
        }
    }

    private String getServerChallenge(int userID) {
        return userChallenges.get(userID); // Retrieves the server challenge for the given user ID
    }

    public boolean bid(int userID, int itemID, int price, String token) {
        if (!isValidToken(userID, token)) {
            try {
                throw new RemoteException("Invalid or expired token");
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        } else {
            AuctionItem item = auctionItems.get(itemID);
            AuctionSaleItem saleItem = auctionSaleItems.get(itemID);
            if (saleItem == null) {
                try {
                    throw new RemoteException("Item not found or not available for bidding");
                } catch (RemoteException e) {
                    throw new RuntimeException(e);
                }
            }
            int currentHighestBid = itemToHighestBid.getOrDefault(itemID, 0);

            if (item != null && price > currentHighestBid && checkAccessControl(userID, itemID, AccessType.BID)) {
                itemToHighestBid.put(itemID, price);
                itemToHighestBidder.put(itemID, userID);
                return true;
            }
            return false;
        }
    }

    private boolean checkAccessControl(int userID, int itemID, AccessType type) {
        switch (type) {
            case BID:
                // Everyone can bid unless the auction is closed
                return auctionItems.containsKey(itemID);
            case CLOSE_AUCTION:
                // Check if the user trying to close the auction is the creator
                Integer creatorID = auctionSaleItemToCreator.get(itemID);
                return creatorID != null && creatorID.equals(userID);
            case MODIFY_BID:
                // Check if the user is the highest bidder and the auction is not closed
                Integer highestBidderID = itemToHighestBidder.get(itemID);
                Integer currentHighestBid = itemToHighestBid.getOrDefault(itemID, 0);
                AuctionItem item = auctionItems.get(itemID);
                return highestBidderID != null && highestBidderID.equals(userID) && item != null;
        }
        return false;
    }

    private AuctionResult getAuctionResult(int itemID) {
        Integer winningBidderID = itemToHighestBidder.get(itemID);
        if (winningBidderID != null) {
            String winningEmail = userInfo.get(winningBidderID);
            Integer winningBid = itemToHighestBid.get(itemID);

            AuctionResult auctionResult = new AuctionResult();
            auctionResult.winningEmail = winningEmail;
            auctionResult.winningPrice = winningBid != null ? winningBid : 0; // Handling possible null for winningBid

            return auctionResult;
        }
        return null; // Return null if there is no winning bidder
    }


    private String generateRandomChallenge() {
        // Generate a random alphanumeric string
        int length = 16; // Length of the challenge string
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }
        return sb.toString();
    }

    private String generateRandomToken() {
        int length = 20; // Length of the token string
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(characters.charAt(secureRandom.nextInt(characters.length())));
        }
        return sb.toString();
    }

    private byte[] sign(String data, PrivateKey privateKey) throws RemoteException {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data.getBytes());
            return signature.sign();
        } catch (Exception e) {
            throw new RemoteException("Signing error", e);
        }
    }

    private boolean verify(String data, byte[] signature, PublicKey publicKey) throws RemoteException {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(data.getBytes());
            return sig.verify(signature);
        } catch (Exception e) {
            throw new RemoteException("Verification error", e);
        }
    }

    // Enum to define types of access control checks
    private enum AccessType {
        BID, CLOSE_AUCTION, MODIFY_BID
    }
}
