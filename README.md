# Indian Algo Trading Platform — Web + Android + iPhone + Spring Boot

Unbranded source project for Indian NSE/BSE cash-equity paper trading, strategy backtesting, broker account linking, market-data retrieval, and guarded Angel One SmartAPI limit-order execution.

## Project contents

- `backend/`: Java 21, Spring Boot 3.5 REST API, persistent H2 database, risk checks, audit trail, strategy scheduler, Angel One SmartAPI adapter, and a dashboard served from `src/main/resources/static`.
- `web-angular/`: Angular standalone web client source; production build is copied into Spring Boot static resources so the UI and API share one origin.
- `android/`: native Kotlin/Jetpack Compose client for Android.
- `ios/TradingPlatform/`: native SwiftUI iPhone client.
- `docs/`: setup and live-trading readiness instructions.

## Honest implementation status

This is a substantial end-to-end **source implementation**, not a certified production trading system. Backend compilation (`mvn -DskipTests compile`) and the Angular production build have completed. Automated tests and native Android/iOS builds have not been run here.

The live-order path is implemented against Angel One SmartAPI's documented login, quote, historical candle, order, modify/cancel, order book, RMS and position endpoints. It is fail-closed by default. Real order submission requires all of the following:
- Live mode explicitly enabled in the Broker tab (starts OFF after every restart)
- an authenticated Angel One session
- current, correct instrument symbol/token/tick/lot mapping
- registered static public IP and correct local-IP/MAC headers
- explicit `LIVE` confirmation on each manual order
- the live kill switch cleared with `ENABLE LIVE`
- order-value, exposure, daily MTM-loss, tick-size, lot-size and local rate-limit checks passed

A broker's successful order-submission response is **not** a fill. Inspect and reconcile the broker order book. An `UNKNOWN` response must not be retried blindly; inspect the broker order book first. Never enable real trading until you've reviewed the source and verified behaviour with your own account.

## 1. Backend, embedded web UI and startup

Requirements: JDK 21 and Maven 3.9+. To start the backend, first package it once. This is the only normal step that compiles source code:

```powershell
cd backend
mvn clean package
.\run-backend.ps1
```

On macOS/Linux, use `./run-backend.sh` instead. The launch scripts start the existing JAR with `java -jar`; they do not invoke Maven or compile on each launch. The scripts keep live trading disabled.

Once the server starts, open **http://127.0.0.1:8080/** in your browser. On first run, create the first account; then use **Sign in**. The app uses OAuth 2.0 Authorization Code with PKCE and short-lived access tokens. The web UI is served by Spring Boot itself; no separate web server is required.

The project includes a static dashboard so the browser UI works immediately after backend packaging. The Angular source is in `web-angular/`. To replace the fallback dashboard with the production Angular bundle, run this once from `web-angular/` on a machine with npm registry access:

```bash
npm install
npm run build
```

Then rebuild the backend once with `mvn clean package`. See `web-angular/README.md` for details.

### Required environment

The backend binds to `127.0.0.1` by default. `GET /api/health` is public; all other `/api/*` routes require OAuth 2.0 access tokens. Password hashes are stored in the local database. Initial account setup closes after the first account is created. Register additional browser callback origins with comma-separated exact URLs in `AUTH_WEB_REDIRECT_URIS` if the dashboard is opened through a custom host. Paper account state, trades, audit events and instrument mappings are saved in `backend/data/`.

### Connect a physical Android phone

For an Android emulator, the app's default URL `http://10.0.2.2:8080` reaches the host machine. For a physical phone on the same trusted Wi-Fi, set `APP_BIND_ADDRESS=0.0.0.0` in the same PowerShell terminal before running `./run-backend.ps1`, then use the computer's private LAN IP in the Android app (for example `http://192.168.1.20:8080`). Allow the port only on the private network in Windows Firewall. Do not port-forward port 8080 to the public internet; use HTTPS/private VPN for remote access.

### Smoke tests

```bash
curl http://127.0.0.1:8080/api/health
curl http://127.0.0.1:8080/auth/status
```

Demo candle data is synthetic, not a live or historical exchange feed.

## 2. Install and connect the iPhone 13 app

Requirements: a Mac with Xcode 15 or later, an Apple ID, and iPhone 13 running iOS 16 or later.

1. Open `ios/TradingPlatform.xcodeproj` in Xcode.
2. Select the `TradingPlatform` target. Under Signing & Capabilities, select your Apple ID team and set a unique bundle identifier.
3. Select your iPhone 13 and press Run.
4. In the app, replace the backend URL placeholder with your private HTTPS URL and tap **Sign in with OAuth 2.0**. The iPhone opens the system browser and returns to the app after authentication.
5. Tap **Connect / Refresh**.

For safe remote access, keep Spring Boot bound to `127.0.0.1` and use a private VPN such as Tailscale plus its HTTPS serving feature to expose only the backend to your own devices. Do not port-forward port 8080 to the public internet. Avoid sending broker PIN/TOTP over plain HTTP. Use a trusted HTTPS connection.

## 3. Install the Android client

Open the `android/` folder in Android Studio, allow Gradle sync, then run the `app` configuration on an emulator or physical device. See `android/README.md` for the backend URL and private-network setup. The initial Android screen includes paper orders, strategy controls, backtesting, broker status/account access and audit history.

## 4. Angel One SmartAPI setup

1. Create/enable your SmartAPI application through Angel One's official portal.
2. Read the current [SmartAPI docs](https://smartapi.angelone.in/docs) and verify the current authentication flow for your account.
3. Open the app's Broker tab and enter the API key, client code, registered static public IP, backend local IP, MAC address, and product type. These settings are held in backend memory and cleared on restart.
4. Submit your PIN and current six-digit TOTP. The backend does not save the PIN/TOTP and keeps session tokens in process memory only. Logout or restart clears the session and disarms live mode.
   Use the web dashboard's Broker tab for API credentials and live-mode enable/disable. The native clients can use the broker session after setup, while the backend enforces the same mode and risk gates.
5. Register exact instrument mappings in the app using the current official Angel One instrument master: exchange, exact trading symbol, token, tick size in rupees, and lot size. These mappings are persisted in `backend/data/instruments.json`. Do not guess a token or tick size.
6. Fetch a live quote and historical candles; verify the symbol, price, timestamps and token against the broker.
7. Leave live mode OFF while testing paper mode, backtests, order book and risk gates. Paper orders remain available while live mode is off.

### Static IP / retail algo rules

Angel One states that applicable API order requests must originate from a registered static IP from 1 April 2026. Confirm the exact requirement for your account and API key in the SmartAPI portal. Dynamic home broadband IP may not qualify; contact your ISP for a static public IP if needed. Current retail-algo guidance also restricts market and IOC orders for algo execution; this implementation uses limit orders only. Review the current broker/exchange requirements before automated real trading.

Official references:
- [Angel One SmartAPI documentation](https://smartapi.angelone.in/docs)
- [Angel One SmartAPI access changes from April 2026](https://www.angelone.in/news/market-updates/what-s-changing-in-angel-one-s-smartapi-access-from-april-1-2026)
- [NSE retail algo FAQ](https://nsearchives.nseindia.com/web/sites/default/files/inline-files/FAQ_Retail%20Algo_03112025_NSE.pdf)

## 5. Trading modes and safety gates

### Paper mode
- Starts with `PAPER_STARTING_CASH` (default ₹100,000).
- Simulates buys/sells and brokerage; persists paper cash, holdings, trades and audit events.
- Has max order value, aggregate exposure and daily loss checks.
- Backtest is a simplified SMA crossover model; taxes, exchange fees, slippage, liquidity, corporate actions and realistic order fills are not fully modeled.

### Real account
- Broker login, RMS/positions/holdings snapshot, live LTP quote, historical candle import, order book, limit-order placement, cancellation and limit-order modification.
- Live orders use a client order tag/idempotency key and an order-rate limit. Unknown outcomes are recorded and must be reconciled manually before retry.
- Server-side kill switch starts ON after every restart. Clear it only deliberately from the authenticated app with the exact phrase `ENABLE LIVE`.
- Manual live order requires exact confirmation `LIVE`.
- Risk checks include max order notional, aggregate broker exposure, RMS daily MTM loss, instrument tick size and lot size. Broker RMS fields and position snapshots may not capture every risk factor; independently validate them and retain broker-side RMS.
- The live strategy runner polls Angel One LTP every 15 seconds, builds local one-minute candles and evaluates SMA crossover at new minute boundaries. It only starts after explicit user action. The iPhone does not need to stay open while the home backend is running.
- The strategy state is in memory and stops on backend restart. The kill switch returns to ON after restart.

## 6. API reference

All `/api/*` endpoints except `/api/health` require OAuth 2.0 access tokens. `/auth/status` and first-account `/auth/register` are public; OAuth authorization and token endpoints follow the Authorization Code with PKCE flow.

- `GET /api/account`
- `POST /api/paper/orders`
- `GET /api/trades`, `GET /api/audit`
- `GET /api/market/{symbol}/candles`, `PUT /api/market/{symbol}/candles`, `POST /api/market/demo`
- `POST /api/backtest/{symbol}`
- `GET /api/instruments`, `POST /api/instruments`, `POST /api/instruments/refresh-official`
- `GET /auth/status`, one-time `POST /auth/register`, `/oauth2/authorize`, `/oauth2/token`
- `GET /api/broker/angel/status`, `POST /api/broker/angel/configure`, `POST /api/broker/angel/login`, `POST /api/broker/angel/refresh`, `POST /api/broker/angel/logout`
- `GET /api/broker/angel/account`, `GET /api/broker/angel/order-book`, `GET /api/broker/angel/quote/{exchange}/{symbol}`
- `POST /api/broker/angel/history`
- `POST /api/live/orders`, `POST /api/live/orders/resolve-unknown`, `POST /api/broker/angel/cancel-order`, `POST /api/broker/angel/modify-limit-order`
- `POST /api/risk/live-mode`, `POST /api/risk/kill-switch`
- `GET /api/strategy/status`, `POST /api/strategy/configure`, `POST /api/strategy/start`, `POST /api/strategy/stop`

## Known limitations — do not ignore

- JUnit tests and native Android/iOS builds were not run in this authoring environment. Run `mvn clean package`, then build the mobile clients in Android Studio and Xcode before relying on them.
- Live market data uses REST LTP polling, not SmartAPI WebSocket streaming; polling is not tick-by-tick.
- Instrument mappings are entered by the user and must be verified against the current official master.
- Live order-book reconciliation is manual; no broker postback/websocket order-state listener is included.
- Broker session tokens are in memory only and must be refreshed/logged in as required.
- OAuth signing keys and authorization grants are in memory. Access tokens expire after 10 minutes; users can sign in again through the system browser. Restarting the backend invalidates active sessions and tokens.
- Strategy configuration is not persisted and strategies stop after restart.
- The local H2 database is a single-user starter store, not a high-availability trading ledger.
- This is not financial advice or a guarantee of profitable trading.
