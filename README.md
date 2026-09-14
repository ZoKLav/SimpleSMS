# SimpleSMS

> **SMS. Without the corporate surveillance carnival attached.**

SimpleSMS is a stripped-down Android SMS client built from the old AOSP Messaging lineage and dragged into the present without dragging the modern pile of telemetry, cloud dependencies, account systems, "smart" features, engagement metrics, cross-device syncing, RCS infrastructure, or other corporate barnacles along with it.

It sends SMS.

It receives SMS.

It shows conversations.

It does not need to phone home to a trillion-dollar company so somebody can graph how often the Settings screen was opened.

That is the entire point.

---

## Why this exists

Modern mobile software has developed a deranged habit of treating the user as a telemetry source first and a customer second.


A messaging app wants cloud services, remote configuration, device identifiers, recommendation engines, account integration, background services, and six hundred megabytes of other garbage that somehow became "normal."

SimpleSMS is the opposite of that.

The operating principle is simple:

**If an SMS client does not need something to send, receive, display, organize, or notify about SMS, it does not get it.**

---

## What SimpleSMS actually is

SimpleSMS is derived from the **AOSP Messaging / Bugle-era application structure and UI**, specifically the old Android Messaging lineage used around the Marshmallow/Nougat era.

The point was not to "reimagine" the interface.

The point was to keep a good, familiar Android SMS interface and remove the network-facing crap that accumulated around modern messaging clients.

The original AOSP-style conversation list, conversation view, compose flow, swipe behavior, archive behavior, message bubbles, contact presentation, and general visual structure are retained and modernized where necessary for current Android versions.

---

## What was removed

A lot.

### Network and cloud garbage removed

- `INTERNET` permission removed
- Network-facing messaging layers removed
- Cloud messaging dependencies removed
- Account systems removed
- RCS removed
- MMS transport removed
- Attachment upload/download infrastructure removed
- Cloud sync removed
- Remote configuration removed
- Analytics removed
- Advertising SDKs removed
- Crash-reporting SDKs removed
- Usage telemetry removed
- Tracking identifiers removed
- Background network jobs removed
- "Smart" online services removed

SimpleSMS cannot quietly send analytics to a server because **the app does not have Android's Internet permission**.

### Permission bloat removed

The application requests only:

```text
READ_CONTACTS
READ_SMS
RECEIVE_SMS
SEND_SMS
POST_NOTIFICATIONS
```

`READ_CONTACTS` is used to turn phone numbers into names.

The SMS permissions are used because this is, in fact, an SMS application.

`POST_NOTIFICATIONS` is used to notify about incoming messages on Android versions that require explicit notification permission.

If a future feature requires turning the manifest into a permissions landfill, that feature can die instead.

---

## What was added

The old AOSP client was useful. It did not need to become a corporate portal.

The useful parts were kept and expanded.

### Appearance

- Configurable application accent color
- Configurable outgoing message bubble color
- Configurable incoming message bubble color
- Configurable conversation background color
- Per-conversation accent colors
- Light theme
- Dark theme
- System theme
- Correct dark-mode text inversion
- Custom hexadecimal colors
- Configurable UI/DPI scaling from 80% through 150%

### Conversation controls

- Archive conversations
- Unarchive conversations
- Archived conversation list
- Incoming messages automatically restore archived threads to the main list
- Search conversations
- Long-press conversation actions
- Configurable swipe-left action
- Configurable swipe-right action
- Swipe actions can be set to:
  - None
  - Archive / unarchive
  - Mark read
  - Delete

### Per-contact behavior

- Unique notification tone per contact/conversation
- Per-contact mute
- Per-contact conversation color
- Resettable contact overrides
- Contact-name lookup from the local Android contacts provider

No contact list is uploaded anywhere because, once again, **there is nowhere for this app to upload it to**.

### Notifications

- Configurable default notification tone
- Per-contact notification tones
- Optional vibration
- Optional message preview
- Private/secret notification visibility behavior
- Notification muting by conversation
- Android notification-channel handling
- Notification channels can be rebuilt after tone changes

### Messaging behavior

- SMS send
- SMS receive
- Multipart SMS support
- Android Telephony provider integration
- Sent / failed state handling
- Optional carrier delivery reports
- Outbox handling
- Incoming-message persistence
- Conversation read state
- Draft preservation
- Contact picker for new conversations
- "Respond via message" support required by Android's default-SMS role

### Privacy controls

- Screenshots and recent-app previews blocked by default with `FLAG_SECURE`
- Screenshot blocking can be disabled in Settings
- Keyboard personalized learning disabled by default in message fields
- Keyboard personalized learning can be enabled manually
- Application backups disabled
- Cleartext network traffic disabled
- No Internet permission
- No account dependency
- No cloud dependency

---

## Android 15 / Android 16 fixes

Old Android UI code assumes the operating system will politely keep application content away from system bars.

Modern Android laughed at that assumption and shoved applications edge-to-edge.

SimpleSMS includes compatibility fixes for that mess:

- Status-bar insets handled on modern Android
- Navigation-bar insets handled on modern Android
- Display-cutout handling added
- Bottom conversation content kept above the system navigation area
- Compose / New Message button kept above the navigation bar
- Top conversation content kept below the application banner
- Conversation lists use explicit top clearance
- List children are clipped to their padded content region so the first SMS cannot slide underneath the toolbar

The result is the old AOSP interface without Android 16 trying to eat the first and last message on the screen.

---

## Security and privacy reality check

SimpleSMS is a **private SMS client**.

It does **not** make SMS a private protocol.

SMS itself is ancient carrier infrastructure. Messages and metadata pass through the cellular provider. The application cannot magically turn carrier SMS into Signal by deleting an analytics SDK.

The privacy goal is narrower and more useful:

**The SMS network already gets what the SMS network requires. The SMS app does not need to become another data collector on top of it.**

SimpleSMS removes that extra observer.

No fake "military-grade messaging" marketing.

No pretending carrier SMS is end-to-end encrypted.

No privacy theater.

Just fewer parties sticking their hands into data they do not need.

---

## RCS

No.

RCS is intentionally not implemented.

SimpleSMS is an SMS application, not an entry point into another provider-operated realtime messaging ecosystem.

If realtime encrypted messaging is required, use a purpose-built end-to-end encrypted messenger.

Trying to bolt every possible communications protocol into one application is how an SMS client turns into a 300 MB service platform with sixteen background processes and a privacy policy longer than a mortgage agreement.

---

## Design philosophy

The feature-selection algorithm is extremely complicated:

1. Does an SMS client need it?
2. Does it improve the SMS client without turning it into a service platform?
3. Can it operate locally?
4. Does it respect the user's device instead of treating it as corporate real estate?

If the answer starts sounding like:

> "Well, a cloud backend would allow..."

No.

If the feature requires an analytics SDK to understand whether people use it:

No.

If the feature requires an account for no technical reason:

No.

If the feature exists primarily to increase "engagement":

Absolutely fucking not.

Software is allowed to do its job and then shut up.

---

## What SimpleSMS is not

SimpleSMS is not:

- a social network
- an advertising platform
- an analytics experiment
- a cloud storage provider
- an account ecosystem
- an RCS client
- a telemetry collector
- a behavioral profiling tool
- a corporate growth funnel
- an excuse to harvest a contact list
- an excuse to keep a process connected to the Internet forever

It is an SMS client.

An SMS client should not need a mission statement longer than that, but apparently the software industry required one.

---

## Current feature summary

| Feature | Status |
|---|---|
| SMS send/receive | Yes |
| Multipart SMS | Yes |
| AOSP-style conversation UI | Yes |
| Contacts integration | Yes |
| Search | Yes |
| Archive | Yes |
| Swipe actions | Configurable |
| Per-contact colors | Yes |
| Per-contact notification tones | Yes |
| Delivery reports | Optional |
| Dark mode | Yes |
| UI/DPI scaling | Yes |
| Screenshot blocking | Default on |
| Keyboard personalized learning | Default off |
| Android 7.0 support | Yes |
| Android 16 display handling | Yes |
| Internet permission | **No** |
| Analytics | **No** |
| Telemetry | **No** |
| Ads | **No** |
| Cloud account | **No** |
| RCS | **No** |
| MMS transport | **No** |
| Corporate nonsense | **Actively rejected** |

---

## Version notes

### 1.2.3

- Actual AOSP Messaging-derived UI structure retained
- SMS-only backend retained
- Network-facing functionality removed
- Internet permission removed
- RCS removed
- MMS transport removed
- Telemetry and cloud functionality absent
- Archive support added
- Configurable swipe actions added
- Per-contact notification tones added
- Per-contact mute added
- Per-conversation colors added
- Global accent/bubble/background color controls added
- UI/DPI scaling added
- Dark mode added and text inversion corrected
- SMS delivery reports added
- Outgoing send-state handling added
- Screenshot protection added
- Keyboard personalized-learning control added
- Android 15/16 edge-to-edge display fixes added
- Status/navigation-bar clearance added
- Compose button navigation-bar overlap fixed
- Conversation bottom-content overlap fixed
- Topmost message toolbar overlap fixed
- RecyclerView top clipping/clearance fixed
- Java 8 source compatibility retained
- Android 7.0 Nougat compatibility retained

---

## Licensing

No Faults Software License:

https://github.com/ZoKLav/no_fault_license

Read the licenses.

Yes, actually read them.

"No warranty" does not mean "please open a GitHub issue demanding compensation because a phone from 2017 did something weird."

---

## Final note

An application should not need to send a status report to a corporation every time somebody taps a button.

SimpleSMS does the boring thing:

**It works locally, handles SMS, and minds its own fucking business.**
