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
}
