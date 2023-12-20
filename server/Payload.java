import java.util.HashMap;
import java.util.Map;

public class Payload {
    Map<Integer, AuctionItem> auctionItems;
    Map<Integer, String> userInfo;
    Map<Integer, AuctionSaleItem> auctionSaleItems;
    Map<Integer, Integer> itemToHighestBidder;
    Map<Integer, Integer> itemToHighestBid;
    Map<Integer, Integer> auctionSaleItemToCreator;
    Map<Integer, String> replicaTable;

    int itemIDCounter;
    int userIDCounter;

    public Payload() {
        // Initialize other fields
        auctionItems = new HashMap<>();
        userInfo = new HashMap<>();
        auctionSaleItems = new HashMap<>();
        itemToHighestBidder = new HashMap<>();
        itemToHighestBid = new HashMap<>();
        auctionSaleItemToCreator = new HashMap<>();
        replicaTable = new HashMap<>();
    }

    public int getSize() {
        return auctionItems.size() + userInfo.size() + auctionSaleItems.size() +
                itemToHighestBidder.size() + itemToHighestBid.size() + auctionSaleItemToCreator.size() +
                replicaTable.size() + itemIDCounter + userIDCounter;
    }
}
