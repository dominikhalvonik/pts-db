-- Spustite raz ako správca MariaDB.
-- Zmeňte heslo, názov databázy a host podľa svojho prostredia.
-- Test používa TCP adresu 127.0.0.1, preto je účet viazaný na 127.0.0.1.
-- Port 3306 neotvárajte verejne do internetu.

CREATE USER 'testlab_reader'@'%'
    IDENTIFIED BY '123456789';

GRANT SELECT ON testlab_shop.workspaces
    TO 'testlab_reader'@'%';
GRANT SELECT ON testlab_shop.products
    TO 'testlab_reader'@'%';
GRANT SELECT ON testlab_shop.orders
    TO 'testlab_reader'@'%';
GRANT SELECT ON testlab_shop.order_items
    TO 'testlab_reader'@'%';

SHOW GRANTS FOR 'testlab_reader'@'%';

-- Zámerne sa neudeľuje SELECT na users, api_tokens ani login_limits.
-- Zámerne sa neudeľuje INSERT, UPDATE, DELETE, CREATE, DROP ani ALTER.
