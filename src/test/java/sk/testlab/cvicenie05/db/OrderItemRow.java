package sk.testlab.cvicenie05.db;

public record OrderItemRow(
        String orderId,
        long productId,
        String sku,
        String productName,
        int unitPriceCents,
        int quantity,
        int lineTotalCents) {
}
