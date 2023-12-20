// Server.java
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.*;
import java.util.HashMap;
import java.util.Map;

public class Replica implements Auction {
    private final Map<Integer, AuctionItem> auctionItems;
    private final Map<Integer, String> userInfo; // Maps user ID to email
    private final Map<Integer, AuctionSaleItem> auctionSaleItems;
    private final Map<Integer, Integer> itemToHighestBidder; // Maps item ID to highest bidder ID
    private final Map<Integer, Integer> itemToHighestBid; // Maps item ID to highest bid value
    private final Map<Integer, Integer> auctionSaleItemToCreator; // Maps auction sale item ID to creator ID

    private int itemIDCounter = 3;
    private int userIDCounter = 1;

    // Replica specific fields
    private int replicaId;
    private int primaryID;
    private boolean isPrimary = false;
    private final Map<Integer, String> replicaTable; // Maps replica ID to their address

    public Replica(int replicaId) throws RemoteException {
        this.replicaId = replicaId;
        this.isPrimary = false; // Initially set as non-primary
        this.replicaTable = new HashMap<>();

        this.itemToHighestBidder = new HashMap<>();
        this.itemToHighestBid = new HashMap<>();
        this.auctionSaleItemToCreator = new HashMap<>();

        // Initialize other fields
        auctionItems = new HashMap<>();
        userInfo = new HashMap<>();
        auctionSaleItems = new HashMap<>();
        // Additional initialization as needed
    }

    // Sync method to synchronize state with other replicas
    public void sync(int primaryReplicaId, Payload RemotePayload) throws RemoteException {
        if (this.replicaId == primaryReplicaId) {
            this.isPrimary = true;
        } else {
            // If this is a backup replica, synchronize its state with the primary
            if (payloadIsLargerThanCurrentState(RemotePayload)) {
                updateStateWithPayload(RemotePayload);
            } else {
                // Return the current state to the caller for them to update
                sendCurrentStateToCaller();
            }
        }
    }

    public Payload getpayload(){
        Payload payload = new Payload();
        payload.auctionItems = auctionItems;
        payload.userInfo = userInfo;
        payload.auctionSaleItems = auctionSaleItems;
        payload.itemToHighestBidder = itemToHighestBidder;
        payload.itemToHighestBid = itemToHighestBid;
        payload.auctionSaleItemToCreator = auctionSaleItemToCreator;
        payload.replicaTable = replicaTable;
        payload.itemIDCounter = itemIDCounter;
        payload.userIDCounter = userIDCounter;
        return payload;
    }
    @Override
    public int getPrimaryReplicaID() throws RemoteException {
        return 0;
    }

    @Override
    public ChallengeInfo challenge(int userID, String clientChallenge) throws RemoteException {
        // ChallangeInfo is now a helper method so I dont waste space :>

        if (userID == this.replicaId && clientChallenge.equals("Primary")){
            // Assign myself as the primary replica
            this.isPrimary = true;
            // Initialize Sync
            sync(this.replicaId, getpayload());
        }
        if (userID == -2 && clientChallenge.equals("Init")){
            // Build the auction items as I am the genesis primary replica
            this.initAuctionItems();
        }
        if(!this.isPrimary && clientChallenge.equals("NewPrimary")){
            // Update new primary replica ID
            this.primaryID = userID;
        }
        if(this.isPrimary && userID != replicaId){
            // If I were the primary replica but the new primary replica is not me
            // I am no longer the primary replica
            this.isPrimary = false;
        }
        return null;
    }

    @Override
    public TokenInfo authenticate(int userID, byte[] clientSignature) throws RemoteException {
        return null;
    }

    @Override
    public AuctionItem getSpec(int userID, int itemID, String token) throws RemoteException {
            return auctionItems.get(itemID);
    }

    @Override
    public Integer register(String email, PublicKey pubKey) throws RemoteException {
        // Prevent duplicate registrations
        if (userInfo.containsValue(email)) {
            throw new RemoteException("Email already registered");
        }
        int userID = generateUniqueUserID();
        userInfo.put(userID, email);
        return userID;
    }

    private void initAuctionItems() {
        auctionItems.put(1, new AuctionItem(1, "Vintage Watch", "A rare vintage watch from 1950s.", 1000));
        auctionItems.put(2, new AuctionItem(2, "Classic Book", "A first edition of a classic novel.", 500));
        auctionItems.put(3, new AuctionItem(3, "Sports Memorabilia", "A signed baseball from a famous player.", 750));
    }

    private synchronized int generateUniqueUserID() {
        return userIDCounter++; // Increment and return the counter
    }

    public Integer newAuction(int userID, AuctionSaleItem item, String token) throws RemoteException {
        int itemID = generateUniqueItemID();
        auctionSaleItems.put(itemID, item); // Store the new auction sale item
        auctionSaleItemToCreator.put(itemID, userID); // Associate the item with the creator's userID
        return itemID;
    }

    private int generateUniqueItemID() {
        return itemIDCounter++; // Increment and return the counter
    }

    public AuctionItem[] listItems(int userID, String token) throws RemoteException {
        return auctionItems.values().toArray(new AuctionItem[0]);
    }

    public AuctionResult closeAuction(int userID, int itemID, String token) throws RemoteException {
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

    public boolean bid(int userID, int itemID, int price, String token) {
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

    private boolean checkAccessControl(int userID, int itemID, AccessType type) {
        return true;
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
    private enum AccessType {
        BID, CLOSE_AUCTION, MODIFY_BID
    }
    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.err.println("You must pass the replica ID as a command-line argument.");
                return;
            }
            int replicaId = Integer.parseInt(args[0]);
            String name = "Auction_" + replicaId;

            Replica s = new Replica(replicaId);
            Auction stub = (Auction) UnicastRemoteObject.exportObject(s, 0);
            Registry registry = LocateRegistry.getRegistry("localhost");
            registry.rebind(name, stub);
            System.out.println("Server ready as " + name);
        } catch (Exception e) {
            System.err.println("Exception:");
            e.printStackTrace();
        }
    }
}
