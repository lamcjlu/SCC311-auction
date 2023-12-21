import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FrontEnd implements Auction {
    private int primaryID;
    private final int n = 4; // Number of replicas to maintain
    private static HashMap<Integer, String> replicaTable;

    private void fixReplica() {
        if (primaryID == -1) {
            //First initialization, spawn n replicas and elect the last one as primary
            System.out.println("(FE) First initialization, spawning " + n + " replicas. Electing Auction_" + n + " as primary.");
            this.primaryID = n;

            for (int i = 0; i <= n; i++) {
                try {
                    System.out.println("FE: java Replica " + i + " &");
                    Runtime.getRuntime().exec("java Replica " + i + " &");
                } catch (Exception e) {
                    System.err.println("(FE) Error in starting new replica: " + e.getMessage());
                }
            }

            try {
                DiscoverReplicas();
                InvokePrimary().challenge(-2, "Init"); // Launch check
                System.out.println("(Fix) PR_Launch: Auction_" + primaryID + " is alive.");
            } catch (Exception e) {
                System.out.println("(Fix) PR_Launch: Auction_" + primaryID + " failed." + e.getMessage());
                fixReplica();
            }

        } else {
            // Spawn a new Primary replica and determine its ID (max existing ID + 1)
            System.out.println("PrimaryID: " + primaryID);
            this.primaryID = (findMaxKeyValue(replicaTable) + 1);
            try {
                Runtime.getRuntime().exec("java Replica " + primaryID);
            } catch (Exception e) {
                System.err.println("(FE) Error in starting new primary replica: " + e.getMessage());
            }

            //check replicaTable & size
            DiscoverReplicas();
            //count alive replicas from replicaTable and spawn new
            int alive = checkAliveReplicas();
            for(int i = 0; i < n - alive; i++){
                try {
                    Runtime.getRuntime().exec("java Replica " + (findMaxKeyValue(replicaTable) + i));
                } catch (Exception e) {
                    System.err.println("(FE) Error in starting new replica: " + e.getMessage());
                }
            }
        }

        // Update the replica names list
        DiscoverReplicas();

        // Broadcast the new primary replica ID to all replicas
        try {
            Registry registry = LocateRegistry.getRegistry("localhost");
            for (Map.Entry<Integer, String> entry : replicaTable.entrySet()) {
                String replicaName = entry.getValue();
                Auction replica = (Auction) registry.lookup(replicaName);
                replica.challenge(primaryID, "NewPrimary");
            }
        } catch (Exception e) {
            System.err.println("Error in broadcasting new primary replica: " + e.getMessage());
        }
    }

    public FrontEnd() {
        this.primaryID = -1;
        replicaTable = new HashMap<>();
        // fixReplica();
    }

    private Auction InvokePrimary() throws RemoteException {
        String replicaName = "Auction_" + primaryID;
        System.out.println("(FE) Invoking primary replica: " + replicaName);
        try {
                Registry registry = LocateRegistry.getRegistry("localhost");
                Auction replica = (Auction) registry.lookup(replicaName);
                replica.challenge(primaryID, "Primary"); // Health check
                return replica;
            } catch (Exception e) {
                System.err.println("(FE) Invoke: PrimaryReplica " + replicaName + " failed, re-electing.");
                fixReplica(); // Fixing the failed replica
                return InvokePrimary(); // Recursive call to invoke the new primary replica
            }
    }

    public void DiscoverReplicas() {
        try {
            System.out.println("(FE) Discovering Replicas...");
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
            System.out.println("(FE) Discovered Replicas: " + replicaTable);
        } catch (Exception e) {
            System.err.println("(FE) Exception in DiscoverReplicas: " + e.toString());
            e.printStackTrace();
        }
    }

    private int checkAliveReplicas() {
        int aliveCount = 0;

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
//                try {
//                    String replicaName = entry.getValue();
//                    Registry registry = LocateRegistry.getRegistry("localhost");
//                    Replica failingReplica = (Replica) registry.lookup(replicaName);
//                    failingReplica.suicide(); // Assuming suicide() is a method in Replica
//                } catch (Exception innerException) {
//                    System.err.println("(FE) ChkAlive - Error in invoking suicide on replica " + entry.getKey() + ": " + innerException.getMessage());
//                }
                iterator.remove(); // Remove the replica from the table if it raises an exception

                System.err.println("(FE) ChkAlive - Removing failed replica: " + entry.getKey());
            }
        }

        return aliveCount;
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