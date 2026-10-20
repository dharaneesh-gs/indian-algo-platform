# Angular web client

This is the Angular 20 standalone client source for the same Spring Boot API. It includes overview/account, paper orders/trade ledger, SMA strategy controls, backtesting and candle loading, Angel One session/account/order-book controls, guarded live limit-order submission, and audit log.

## Build and embed into Spring Boot

Requirements: Node.js 20.19+ or 22.12+ and npm, plus network access for the first dependency install.

From this folder:

```bash
npm install
npm run build
```

`npm run build` builds Angular production assets and copies the result to `../backend/src/main/resources/static`. Package the backend after the Angular build:

```bash
cd ../backend
mvn clean package
```

The Spring Boot JAR then serves the Angular UI and API on the same origin. No separate Angular dev server is needed for normal use. If you want hot reload during frontend development, run `npm run build:angular` and use your preferred Angular dev-server workflow; configure a proxy for `/api`, `/auth`, and `/oauth2` if you use `ng serve`.

The main project ZIP also contains a lightweight static dashboard in Spring Boot's `static` folder so a web UI is available immediately even before installing Angular dependencies. The Angular build replaces that fallback with compiled Angular assets.

## Authentication

On first run, open the web client at `http://127.0.0.1:8080/` and create the first account from the backend computer. The browser then uses OAuth 2.0 Authorization Code with PKCE. Access tokens expire after 10 minutes. Use HTTPS or a private VPN for remote access.

## Notes

- The Angular production bundle is generated from source with the commands above.
- Real-order controls require explicit confirmation and remain blocked unless the server's live trading configuration, broker session, risk gates and kill-switch conditions permit them. Keep live trading disabled during initial testing.
