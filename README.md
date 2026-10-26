# TestLab Shop – Cvičenie 05: Web GUI a databáza

Hotové riešenie praktickej časti prezentácie
`05_Web_GUI_a_databaza.pptx`.

Projekt vytvorí referenčnú objednávku cez **Selenium Web GUI** a následne ju
podľa vlastného UUID nezávisle overí cez **read-only JDBC** v MariaDB.

## Čo test overuje

1. Pred operáciou načíta z DB ceny a sklad `KB-001` a `MS-001`.
2. Cez GUI vytvorí objednávku:
   - `KB-001 × 2`
   - `MS-001 × 1`
   - kupón `STUDENT10`
   - kuriér
3. Z GUI uloží UUID, cenu `8563`, viditeľných `85,63 €` a stav `created`.
4. Podľa presného UUID overí:
   - tabuľku `orders`,
   - tabuľku `order_items`,
   - pokles skladu v `products` z `100/100` na `98/99`.
5. Objednávku zruší cez GUI.
6. Overí `cancelled`, vyplnené `cancelled_at` a návrat skladu na `100/100`.
7. Obnoví detail a overí, že samotné čítanie už sklad nemení.

Test nepoužíva `ORDER BY created_at DESC LIMIT 1`, nehľadá globálne poslednú
objednávku a databázu nepoužíva na prípravu alebo opravu výsledku.

## Technológie

- Java 21
- Maven
- Selenium 4.49.0
- JUnit Jupiter 5.12.2
- MariaDB Connector/J 3.5.10
- TestLab Shop 1.0.0

Projekt neobsahuje Groovy ani `groovy-all`.
JUnit je zosúladený cez BOM a explicitný `junit-platform-launcher`.

## 1. Otvorenie v IntelliJ IDEA

1. Rozbaľte ZIP.
2. Otvorte koreňový priečinok alebo jeho `pom.xml` ako **Maven projekt**.
3. Nastavte Project SDK na **JDK 21**.
4. V Maven okne vykonajte **Reload All Maven Projects**.

Pri spustení zelenou šípkou musí byť pracovný priečinok koreň projektu,
teda priečinok obsahujúci `pom.xml` a `config/`.

## 2. Príprava izolovaných dát

V pôvodnom PHP projekte TestLab Shop:

```powershell
php bin/check.php
php bin/fixtures.php create
```

Výstup obsahuje:

- `workspace_code`
- `email`
- `password`

Použite nový `run-*` priestor. Test očakáva čerstvý sklad 100 kusov
`KB-001` a 100 kusov `MS-001`.

PHP aplikáciu spustite v samostatnom termináli:

```powershell
php -S 127.0.0.1:8080 -t public
```

## 3. Read-only databázový účet

Ako správca MariaDB upravte a spustite:

```text
database/create-readonly-user.sql
```

Účet dostane iba `SELECT` nad:

- `workspaces`
- `products`
- `orders`
- `order_items`

Nedostane prístup k `users`, `api_tokens` ani `login_limits` a nedostane
`INSERT`, `UPDATE`, `DELETE`, `CREATE`, `DROP` či `ALTER`.

Na spoločnom serveri nedávajte študentom aplikačný DB účet.
Port MariaDB neotvárajte verejne do internetu. Pri nedôveryhodných klientoch
použite samostatnú databázu alebo samostatné laboratórne prostredie.

## 4. Konfigurácia testu

Skopírujte:

```powershell
Copy-Item config\test.example.properties config\test.properties
```

V ZIP je aj zástupný `config/test.properties`; môžete ho priamo upraviť.

Vyplňte:

```properties
base.url=http://127.0.0.1:8080/

test.workspace=run-SKUTOCNY_KOD
test.email=SKUTOCNY_EMAIL_FIXTURE
test.password=SKUTOCNE_HESLO_FIXTURE

browser=chrome
headless=false
timeout.seconds=30
cleanup.order=true

db.url=jdbc:mariadb://127.0.0.1:3306/testlab_shop?connectTimeout=5000&socketTimeout=10000
db.user=testlab_reader
db.password=SKUTOCNE_HESLO_READ_ONLY_UCTU
db.verify.readonly=true
```

`db.url` nie je HTTP URL. Používajte protokol `jdbc:mariadb://`.

Heslá možno nastaviť aj premennými prostredia:

```powershell
$env:TEST_PASSWORD = 'HESLO_FIXTURE'
$env:DB_PASSWORD = 'HESLO_READ_ONLY_UCTU'
```

Priorita je JVM `-D` > premenná prostredia > `config/test.properties`.

## 5. Spustenie

V IntelliJ otvorte:

```text
src/test/java/sk/testlab/cvicenie05/GuiDatabaseOrderTest.java
```

Spustite triedu alebo metódu:

```text
orderCreatedInGuiMatchesDatabaseAndCancellationRestoresStock
```

Cez Maven okno môžete spustiť cieľ:

```text
test
```

Ak je Maven na systémovom PATH:

```powershell
mvn test
```

alebo:

```powershell
.\run-tests.bat
```

Bez viditeľného okna:

```powershell
mvn test "-Dheadless=true"
```

## 6. Očakávaný výsledok

Na správnej aplikácii a čerstvom fixture:

```text
Tests run: 1, Failures: 0, Errors: 0
```

Konzola vypíše približne:

```text
PASS: <UUID> | GUI + DB = 8563 centov
| created -> cancelled | sklad 100/100 -> 98/99 -> 100/100
```
