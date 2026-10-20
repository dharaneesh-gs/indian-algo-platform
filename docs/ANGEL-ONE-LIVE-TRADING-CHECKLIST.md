# Angel One SmartAPI and live-trading readiness

## Current safeguards
- Live mode is OFF after every backend restart and can be armed only in the authenticated Broker UI after Angel One login.
- Server kill switch starts ON after every backend restart.
- Manual live order requires exact confirmation `LIVE`.
- Only NSE/BSE cash-equity LIMIT orders are implemented.
- Order quantity must match the registered instrument lot size; price must align to the configured tick size.
- The server checks order notional, exposure and broker RMS MTM fields before BUY orders.
- A client order tag is used for idempotency. If a request outcome is unknown, do not retry blindly.
- PIN/TOTP are not saved or logged; JWT/feed/refresh tokens are held in process memory and cleared on logout/restart.

## Required before real trading
1. Verify the current official SmartAPI authentication process and the account's login mode.
2. Register a static public IP for applicable API order requests, and enter the actual local IP and MAC address in the Broker UI. Runtime settings are held in memory and cleared on restart.
3. Verify exact instrument tokens, trading symbols, tick sizes and lot sizes against current Angel One instrument data.
4. Confirm product type, exchange, order variety, order tag, price protection, trading hours, and broker restrictions for the instruments you intend to trade.
5. Run `mvn test`, inspect the source, and test login, quotes, historical candles, order book, cancel/modify and error handling with no real orders first.
6. Keep live mode OFF while testing. Only arm it from the Broker UI after understanding all code paths and verifying broker configuration.
7. Use a small, manually supervised order to verify that submission, broker order ID, status, fills and account reconciliation behave as expected.
8. Check broker order book after any timeout/unknown result. Do not repeat a potentially accepted order.
9. Verify the broker's RMS controls, your account permissions and current NSE/SEBI retail algo obligations before enabling unattended strategy execution.
10. Secure remote access with HTTPS and a private VPN. Do not port-forward Spring Boot directly to the internet.

## Official docs
- SmartAPI: https://smartapi.angelone.in/docs
- Angel One April 2026 SmartAPI access update: https://www.angelone.in/news/market-updates/what-s-changing-in-angel-one-s-smartapi-access-from-april-1-2026
- NSE retail algo FAQ: https://nsearchives.nseindia.com/web/sites/default/files/inline-files/FAQ_Retail%20Algo_03112025_NSE.pdf
