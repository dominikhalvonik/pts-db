package sk.testlab.cvicenie05.db;

import java.time.LocalDateTime;

public record OrderRow(
        String id,
        String orderNumber,
        long workspaceId,
        long userId,
        String status,
        String currency,
        int subtotalCents,
        int discountCents,
        int shippingCents,
        int totalCents,
        String couponCode,
        String deliveryMethod,
        String customerName,
        String customerEmail,
        String addressLine,
        String city,
        String postalCode,
        String note,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt) {
}
