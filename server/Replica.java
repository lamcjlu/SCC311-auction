// Replica.java
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Replica implements Auction {
    private Map<Integer, AuctionItem> auctionItems;
    private Map<Integer, String> userInfo; // Maps user ID to email
    private Map<Integer, AuctionSaleItem> auctionSaleItems;
    private Map<Integer, Integer> itemToHighestBidder; // Maps item ID to highest bidder ID
    private Map<Integer, Integer> itemToHighestBid; // Maps item ID to highest bid value
    private Map<Integer, Integer> auctionSaleItemToCreator; // Maps auction sale item ID to creator ID
    private int itemIDCounter = 3;
    private int userIDCounter = 1;

    // Replica specific fields
    private int replicaID;
    private int primaryID;
    private boolean isPrimary = false;
    private Map<Integer, String> replicaTable; // Keeps track of replicas

    public Replica(int replicaID) throws RemoteException {
        this.replicaID = replicaID;
        this.isPrimary = false; // Initially set as non-primary
        this.replicaTable = new HashMap<>();

        this.itemToHighestBidder = new HashMap<>();
        this.itemToHighestBid = new HashMap<>();
        this.auctionSaleItemToCreator = new HashMap<>();

        // Initialize other fields
        auctionItems = new HashMap<>();
        userInfo = new HashMap<>();
        auctionSaleItems = new HashMap<>();
        System.out.println(debugHeader()+"-Replica Initialized.");
    }

    // Sync method to synchronize state with other replicas
    public void sync(int primaryReplicaId, Payload RemotePayload, int callerID) throws RemoteException {
        System.out.println(debugHeader()+"-Sync Syncing with PriRepID: " + primaryReplicaId);
        System.out.println(debugHeader()+"-Sync CallerID: " + callerID);
        DiscoverReplicas();

        // If this is the primary replica, send payload to all other replicas
        if (this.replicaID == primaryReplicaId && this.isPrimary) {
            System.out.println(debugHeader()+"-Sync Syncing state with " + checkAliveReplicas() + " other replicas");
            for (Map.Entry<Integer, String> entry : replicaTable.entrySet()) {
                int targetID = entry.getKey();
                System.out.println(debugHeader()+"-Sync Syncing with replicaID: " + targetID);
                // Skip if the replica is the primary itself
                if (targetID == this.replicaID) {
                    System.out.println(debugHeader()+"-Sync Skipping self");
                    continue;
                }

                String replicaName = entry.getValue();
                try {
                    Registry registry = LocateRegistry.getRegistry("localhost");
                    System.out.println(debugHeader()+"-Sync Looking up and syncing replica: " + replicaName);
                    Replica targetReplica = (Replica) registry.lookup(replicaName);
                    System.out.println(debugHeader()+"-Sync targetReplica: " + targetReplica);
                    if (targetReplica == null) {
                        System.out.println(debugHeader()+"-Sync targetReplica is null - Exiting...");
                        System.exit(1);
                    }
                    targetReplica.sync(this.primaryID, this.getpayload(), this.replicaID); // send payload to replica
                    System.out.println(debugHeader()+"-Sync Synced with replica: " + replicaName);
                    System.out.println(debugHeader()+"-Sync primaryID: " + this.primaryID + " | replicaID: " + targetID + " | payload: " + this.getpayload());
                } catch (RemoteException e) {
                    System.err.println(debugHeader()+"-Sync Error syncing with replica " + replicaName + ": " + e.getMessage());
                } catch (NotBoundException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            // If this is a backup replica, check if primary is ahead of self and update
            System.out.println(debugHeader()+"-Sync Syncing with replicaID: " + primaryReplicaId + " | RemotePayload: " + RemotePayload);
            updateStateWithPayload(RemotePayload);
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

    public void updateStateWithPayload(Payload remotePayload) {
        System.out.println(debugHeader()+"-updateStateWithPayload UpdatePayload - RemotePL: " + remotePayload + " | SelfPL: " + this.getpayload());
        if (remotePayload.getSize() > this.getpayload().getSize()) {
            this.auctionItems = remotePayload.auctionItems;
            this.userInfo = remotePayload.userInfo;
            this.auctionSaleItems = remotePayload.auctionSaleItems;
            this.itemToHighestBidder = remotePayload.itemToHighestBidder;
            this.itemToHighestBid = remotePayload.itemToHighestBid;
            this.auctionSaleItemToCreator = remotePayload.auctionSaleItemToCreator;
            this.replicaTable = remotePayload.replicaTable;
            this.itemIDCounter = remotePayload.itemIDCounter;
            this.userIDCounter = remotePayload.userIDCounter;
            System.out.println(debugHeader()+"-updateStateWithPayload State updated with larger remote payload.");
        }
    }
    public void DiscoverReplicas() {
        try {
            System.out.println(debugHeader()+"-DR Discovering replicas...");
            Registry registry = LocateRegistry.getRegistry("localhost");
            String[] boundNames = registry.list();
            if (boundNames.length == 0) {
                System.out.println(debugHeader()+"-DR FATAL ERROR RMI IS EMPTY. Exiting...");
                System.exit(1);
            }
            // Pattern to match "Auction_#" names
            Pattern pattern = Pattern.compile("Auction_(\\d+)");
            for (String name : boundNames) {
                Matcher matcher = pattern.matcher(name);
                if (matcher.find()) {
                    // Extract ID and add to replicaTable
                    int id = Integer.parseInt(matcher.group(1));
                    replicaTable.put(id, name);
                }
            }
            // Optionally, print out the discovered replicas
            System.out.println(debugHeader()+"-DR Discovered Replicas: " + replicaTable);
        } catch (Exception e) {
            System.err.println(debugHeader()+"-DR Exception in DiscoverReplicas: " + e);
            e.printStackTrace();
        }
    }
    private int checkAliveReplicas() {
        int aliveCount = 0;
        System.out.println(debugHeader()+"-ChkAlive Checking alive replicas...");
        // Using an Iterator to avoid ConcurrentModificationException while removing elements
        Iterator<Map.Entry<Integer, String>> iterator = replicaTable.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, String> entry = iterator.next();
            try {
                String replicaName = entry.getValue();
                Registry registry = LocateRegistry.getRegistry("localhost");
                Auction replica = (Auction) registry.lookup(replicaName);
                replica.authenticate(-1, null); // Assuming this method exists in the Auction interface
                aliveCount++;
            } catch (Exception e) {
                iterator.remove(); // Remove the replica from the table if it raises an exception
                System.err.println(debugHeader()+"-ChkAlive Removing failed replica: " + entry.getKey());

            }
        }

        return aliveCount;
    }
    @Override
    public int getPrimaryReplicaID() throws RemoteException {
        if(isPrimary){
            return this.replicaID;
        } else {
            return this.primaryID;
        }
    }

    @Override
    public ChallengeInfo challenge(int userID, String clientChallenge) throws RemoteException {
        // ChallangeInfo is now a helper method :>
        System.out.println(debugHeader()+"-CL Received challenge with userID: " + userID + " and clientChallenge: " + clientChallenge);
        if (userID == this.replicaID && clientChallenge.equals("Primary")){
            // Assign myself as the primary replica
            this.isPrimary = true;
            // Initialize Sync
            System.out.println(debugHeader()+"-CL Initializing Sync with payload: " + getpayload());
            sync(this.replicaID, getpayload(), this.replicaID);
        }
        if (userID == -2 && clientChallenge.equals("Init")){
            // Build the auction items as I am the genesis primary replica
            this.initAuctionItems();
            System.out.println(debugHeader()+"-CL Initialized AuctionItems: " + auctionItems);
        }
        if(!this.isPrimary && clientChallenge.equals("NewPrimary")){
            // Update new primary replica ID
            this.primaryID = userID;
            System.out.println(debugHeader()+"-CL I am new primary replica ID: " + userID);
        }
        if(this.isPrimary && userID != replicaID){
            // If I were the primary replica but the new primary replica is not me
            // I am no longer the primary replica
            this.isPrimary = false;
            System.out.println(debugHeader()+"-CL I am no longer the primary replica | isPrimary: " + this.isPrimary);
            this.suicide();
        }
        return null;
    }

    @Override
    public TokenInfo authenticate(int userID, byte[] clientSignature) throws RemoteException {
        return null;
    }

    @Override
    public AuctionItem getSpec(int userID, int itemID, String token) throws RemoteException {
        if (isPrimary){
            return auctionItems.get(itemID);
        } else {
            // If this is a backup replica, synchronize its state with the primary
            sync(this.primaryID, getpayload(), this.replicaID);
        }
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
    private String debugHeader(){
        return "(Auction_" + this.replicaID + "|isPrimary: " + this.isPrimary + ")";
    }
    public void suicide(){
        System.out.println(debugHeader()+ " is suiciding with primaryID = " + primaryID);
        System.exit(0);
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
            Replica stb = (Replica) UnicastRemoteObject.exportObject(s, 0);
            Registry registry = LocateRegistry.getRegistry("localhost");
            registry.rebind(name, stub);
            registry.rebind("R"+name, stb);
            System.out.println("Server ready as " + name);
        } catch (Exception e) {
            System.err.println("Exception:");
            e.printStackTrace();
        }
    }
}
