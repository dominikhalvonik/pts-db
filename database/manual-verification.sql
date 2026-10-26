-- UUID vložte z GUI elementu data-testid="order-id".
SET @order_id = 'SEM_UUID_OBJEDNAVKY';

SELECT id, order_number, workspace_id, status, currency,
       subtotal_cents, discount_cents, shipping_cents, total_cents,
       coupon_code, delivery_method, created_at, cancelled_at
FROM orders
WHERE id = @order_id;

SELECT sku, product_name, unit_price_cents, quantity, line_total_cents
FROM order_items
WHERE order_id = @order_id
ORDER BY sku;

SELECT p.sku, p.price_cents, p.stock, w.code AS workspace_code
FROM products p
JOIN workspaces w ON w.id = p.workspace_id
JOIN orders o ON o.workspace_id = p.workspace_id
WHERE o.id = @order_id
  AND p.sku IN ('KB-001', 'MS-001')
ORDER BY p.sku;

-- Referenčný výsledok po vytvorení:
-- orders: subtotal=8970, discount=897, shipping=490, total=8563, status=created
-- items: KB-001 -> 3490 * 2 = 6980; MS-001 -> 1990 * 1 = 1990
-- stock: KB-001=98; MS-001=99
--
-- Po zrušení:
-- status=cancelled, cancelled_at IS NOT NULL, oba sklady opäť 100.
