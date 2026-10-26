package sk.testlab.cvicenie05.db;

public record ProductRow(
        long id,
        long workspaceId,
        String sku,
        String name,
        int priceCents,
        int stock,
        boolean active) {
}
