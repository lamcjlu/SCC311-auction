import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FrontEnd implements Auction {
    private int PrimaryID;
    private final int n = 5; // Number of replicas to maintain
    private static HashMap<Integer, String> replicaTable;

    private void fixReplica() {
        // Spawn a new replica and determine its ID (max existing ID + 1)
        PrimaryID = (findMaxKeyValue(replicaTable) + 1);
        // Start the new replica
        try {
            Runtime.getRuntime().exec("java Replica " + PrimaryID);
        } catch (Exception e) {
            System.err.println("Error in starting new replica: " + e.getMessage());
        }

        // Update the replica names list
        String newReplicaName = "Auction_" + PrimaryID;
        replicaTable.put(PrimaryID, newReplicaName);

        // Broadcast the new primary replica ID to all replicas
        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            for (String replicaName : replicaNames) {
                Auction replica = (Auction) registry.lookup(replicaName);
                replica.sync(newReplicaId, /* payload including current state */);
            }
        } catch (Exception e) {
            System.err.println("Error in broadcasting new primary replica: " + e.getMessage());
        }
    }

    private void syncReplicas(String newPrimaryName) throws RemoteException {
        Registry registry = LocateRegistry.getRegistry("localhost");
        Auction newPrimary = (Auction) registry.lookup(newPrimaryName);
        Object payload = newPrimary.retrieveState(); // Assuming this method retrieves the state

        for (String replicaName : replicaNames) {
            if (!replicaName.equals(newPrimaryName)) {
                Auction replica = (Auction) registry.lookup(replicaName);
                replica.sync(primaryReplicaID, payload);
            }
        }
    }

    public FrontEnd() {
        this.PrimaryID = 0;
        DiscoverReplicas();
    }

    private Auction InvokePrimary() throws RemoteException {
        String replicaName = "Auction_" + PrimaryID;
        try {
                Registry registry = LocateRegistry.getRegistry("localhost");
                Auction replica = (Auction) registry.lookup(replicaName);
                replica.challenge(-1, "Alive"); // Health check
                return replica;
            } catch (Exception e) {
                System.err.println("(Invoke) PrimaryReplica " + replicaName + " failed, re-electing.");
                fixReplica(); // Fixing the failed replica
                return InvokePrimary(); // Recursive call to invoke the new primary replica
            }
    }

    public void DiscoverReplicas() {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            String[] boundNames = registry.list();

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
            System.out.println("Discovered Replicas: " + replicaTable);
        } catch (Exception e) {
            System.err.println("Exception in DiscoverReplicas: " + e.toString());
            e.printStackTrace();
        }
    }

    @Override
    public int getPrimaryReplicaID() throws RemoteException {
        try{
            return InvokePrimary().getPrimaryReplicaID();
        } catch (RemoteException e) {
            fixReplica(); // Failover to another replica
            return InvokePrimary().getPrimaryReplicaID();
        }
    }

    @Override
    public Integer register(String email, PublicKey pubKey) throws RemoteException {
        try {
            return InvokePrimary().register(email, pubKey);
        } catch (RemoteException e) {
            fixReplica(); // Failover to another replica
            return InvokePrimary().register(email, pubKey);
        }
    }

    @Override
    public ChallengeInfo challenge(int userID, String clientChallenge) throws RemoteException {
        try {
            return InvokePrimary().challenge(userID, clientChallenge);
        } catch (RemoteException e) {
            fixReplica(); // Failover to another replica
            return InvokePrimary().challenge(userID, clientChallenge);
        }

    }

    @Override
    public TokenInfo authenticate(int userID, byte[] signature) throws RemoteException {
        try {
            return InvokePrimary().authenticate(userID, signature);
        } catch (RemoteException e) {
            fixReplica(); // Failover to another replica
            return InvokePrimary().authenticate(userID, signature);
        }

    }

    @Override
    public AuctionItem getSpec(int userID, int itemID, String token) throws RemoteException {
        try {
            return InvokePrimary().getSpec(userID, itemID, token);
        } catch (RemoteException e) {
            fixReplica(); // Failover to another replica
            return InvokePrimary().getSpec(userID, itemID, token);
        }

    }

    @Override
    public Integer newAuction(int userID, AuctionSaleItem item, String token) throws RemoteException {
        try {
            return InvokePrimary().newAuction(userID, item, token);
        } catch (RemoteException e) {
            fixReplica(); // Failover to another replica
            return InvokePrimary().newAuction(userID, item, token);
        }
    }

    @Override
    public AuctionItem[] listItems(int userID, String token) throws RemoteException {
        try {
            return InvokePrimary().listItems(userID, token);
        } catch (RemoteException e) {
            fixReplica(); // Failover to another replica
            return InvokePrimary().listItems(userID, token);
        }
    }

    @Override
    public AuctionResult closeAuction(int userID, int itemID, String token) throws RemoteException {
        try {
            return InvokePrimary().closeAuction(userID, itemID, token);
        } catch (RemoteException e) {
            fixReplica(); // Failover to another replica
            return InvokePrimary().closeAuction(userID, itemID, token);
        }
    }

    @Override
    public boolean bid(int userID, int itemID, int price, String token) throws RemoteException {
        try {
            return InvokePrimary().bid(userID, itemID, price, token);
        } catch (RemoteException e) {
            fixReplica(); // Failover to another replica
            return InvokePrimary().bid(userID, itemID, price, token);
        }
    }

    public static int findMaxKeyValue(HashMap<Integer, String> map) {
        if (map.isEmpty()) {
            throw new IllegalStateException("HashMap is empty");
        }

        int maxKey = Integer.MIN_VALUE;
        for (Map.Entry<Integer, String> entry : map.entrySet()) {
            if (entry.getKey() > maxKey) {
                maxKey = entry.getKey();
            }
        }
        return maxKey;
    }

    public static void main(String[] args) {
        try {
            FrontEnd frontEnd = new FrontEnd();
            replicaTable = new HashMap<>();
            String name = "FrontEnd";
            Auction stub = (Auction) UnicastRemoteObject.exportObject(frontEnd, 0);
            Registry registry = LocateRegistry.getRegistry();
            registry.rebind(name, stub);
            System.out.println("FrontEnd ready");
        } catch (Exception e) {
            System.err.println("Exception:");
            e.printStackTrace();
        }
    }
}