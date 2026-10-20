# Native iPhone app setup

1. Open `ios/TradingPlatform.xcodeproj` on a Mac with Xcode 15+.
2. Select the `TradingPlatform` target and set your Apple Developer team and a unique bundle identifier.
3. Select iPhone 13 as the run destination and build/run.
4. Create the first account on the backend computer's web dashboard. In the iPhone app's Home tab, set the backend URL to your private HTTPS endpoint, then tap **Sign in with OAuth 2.0** and authenticate in the system browser. The iPhone returns to the app after authentication using PKCE. Access tokens expire after 10 minutes; sign in again when prompted.

The app uses native SwiftUI and targets iOS 16+. It has not been compiled or device-tested here.

## Secure home-computer connection
The default backend binds to loopback. Keep it there and expose it only through a private VPN and HTTPS reverse proxy (for example, a private Tailscale Serve endpoint). Use that HTTPS hostname as the backend URL. Do not expose port 8080 publicly or submit broker PIN/TOTP over plain HTTP.

Broker login PIN/TOTP are sent to the backend only when you tap Authorize and are cleared from the app form after the request. Broker JWT/feed/refresh tokens remain in backend memory, not the app.

## App features
Dashboard, paper portfolio and orders, SMA backtest, broker status/login/logout/session refresh, account snapshot, instrument mapping, live quotes, historical candle import, live strategy start/stop, real limit orders with explicit confirmation, order book, cancel/modify controls, and kill switch.
