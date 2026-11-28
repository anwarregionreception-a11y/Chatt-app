# Millionaire app: project notes

Read this first, then check the code. These notes may be out of date.
Last updated: 28 November 2026.

## Owner
- Owner UID (Firebase Authentication): `AT0ZQHRqVIaSMHP2a8PYfU8MT7A2`
- The Owner role is NOT active yet. It will be granted in phase 4, enforced by Firestore rules.
- Never put passwords, owner email or phone numbers in the code.

## Working style
- Reply in short, simple Roman Urdu. The user works only from a phone (Infinix Hot 40i, Android 13).
- Write code on a branch, check it with the GitHub build, merge into `main` only with permission.

## App
- WhatsApp-like community chat: groups, coins, 16 authority levels. Display name: Millionaire (was Raabta).
- Made with Gemini AI Studio. Kotlin, Jetpack Compose, Room, Firebase.
- Package: `com.rabbta.chatapp.v1`. Firebase project: `rabbta-16040` (Spark plan). Target: about 5000 users.
- Current work is on branch `firebase-phase-1`.

## Build and install
- GitHub Actions > Build APK > Run workflow > branch `firebase-phase-1` > artifact `app-debug`.
- Install with a Split APK Installer (the file manager showed "App wasn't installed", cause unknown).

## Done
- Startup crash fixed (Room KSP compiler enabled).
- Fixed debug signing with the repo keystore.
- Firebase: Email/Password enabled, Firestore created, `firestore.rules` published in the console.
- Phase 1: online sign up, login, password reset by email, password change, user search by email or name.
- Hardcoded owner account, demo accounts and on-screen temporary password removed.
- Display name changed to Millionaire. GitHub build passed.
- Owner has an account in Firebase Authentication (UID above).

- Phase 2 code: online friend requests and 1-to-1 chat (messages are deleted from the server after
  delivery and kept in Room on the phone). Needs the new `firestore.rules` published and a two-phone test.
- Search tested successfully on the owner's setup.

## Pending
- Test phase 2 on two phones, then merge into `main` (ask first).
- No push notifications yet: messages arrive while the app is running or when it is next opened.
- Groups, coins and ratings are still local to each phone.
- Recommended: make the repo private and change any previously used passwords.
- KSP 2.3.5 shows a red annotation in the build, which still passes (optional upgrade: 2.3.12).

## Requested features
- A. Owner Admin Panel (in menu, Owner only): raise or lower own authority freely, take any number of coins,
  list all registered users, show online and offline counts, show how many IDs were used per device/IP.
- B. ID switching: stay logged in to any number of IDs.
- C. FAQ page with basic questions and answers about the app.
- D. Group list with public and private groups: Owner's group on top, then ordered by member count.
- E. Member list inside a group; tapping a name opens the profile.
- F. Spin game: 1 coin, 3 coins, 1 outline star, 3 outline stars, "try again after 24 hours".
  Random reward, one spin per user every 24 hours.
- G. Owner sends a message to all users as a WhatsApp-style heads-up notification.
- H. Rating page: top 10 coin buyers, top 10 authority users, top 10 popular groups.

## Phases
2. Friend requests, 1-to-1 chat (code written, testing pending)
3. Online groups (D, E)
4. Owner role and Admin Panel (A)
5. Coins, spin (F), rating (H)
6. Notifications (G), ID switching (B), FAQ (C)

## Open questions
1. Use a device ID instead of IP address? (mobile networks share IPs)
2. Free plan only, or Blaze when needed?
3. Equal spin chances, or rarer big rewards?

## Technical notes
- Owner rights, coins and authority must be enforced server side.
- A tamper-proof random spin and in-app broadcast notifications need Blaze/Cloud Functions.
  On the free plan, notifications can be sent from the Firebase console.
- Free Firestore quota: 20,000 writes and 50,000 reads per day. Keep usage low.

## Play Store (later)
Target SDK 36, release-signed AAB, developer account, 12 testers for 14 days, privacy policy,
account deletion, report/block, and Google Play Billing for selling coins.
