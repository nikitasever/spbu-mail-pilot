# Desktop design context

Scope: local Windows 10/11 application, Russian interface. First vertical slice:
connect → real folders → paged messages → reading → new mail/reply/send. No demonstration
messages ship in the product. Existing Android UI stays unchanged.

Canonical project context: ../DESIGN.md and ../UX-CONTRACT.md. The existing violet
brand is retained. The desktop canvas follows the owner's requested obsidian
variant, not a replacement for Android theme tokens.

Runtime tokens live in src/styles.css: --brand #ceacf3 (project dark brand),
--canvas #110f17 (desktop obsidian variant), --surface #1b1824, --elevated #242030,
--ink #f4f0fa, --muted #a69cb5, --line white at 8%. System Segoe UI Variable/Segoe
UI fonts work without downloading fonts. Typography: 27px reading title, 13–14px
content, 10–12px utility labels.

Three columns have distinct jobs: account/folders, message index, selected mail.
The list uses inset dividers, sender avatars and clear unread weight; selected
mail has a violet rail. The reader is a quiet document surface. At 850px and
below, the list and reader alternate while folders remain accessible. Scroll
owners are the folder list, message list and reader body, each with min-height:0.

Shared owners: protocol.ts owns cancellation, request identity, fixed errors and
page validation. main.ts renders untrusted fields with textContent. System input,
button, details and form semantics remain native. Search is explicit server-side
submission; its text stays in session memory, not URLs/history/localStorage.

Pending operations have text and cancellation; stale replies cannot update a
new route. Loading and errors do not delete the displayed list during pagination.
Readonly status does not pretend that reading changed Seen. Attachment rows precede
body text; native save dialogs own destination choice. The shared Attachments
controller owns per-file progress, duplicate suppression, cancellation, retry,
completion/open actions and logout cleanup. A persistent sidebar queue stays visible
while folders/messages change. Unknown transfer sizes remain indeterminate;
MIME transfer bytes are not presented as decoded file sizes. Saves use private
transfer directories and atomic destination replacement; cancellation does not
mark a partial file as saved. Only explicitly saved document tokens can reach the
native opener; executable/active-content types are save-only. Logout clears
message content and invalidates outstanding requests. Body HTML/images/scripts
are never executed or requested by the frontend.

Production CSP permits local assets and IPC only; no external-page capabilities,
filesystem, shell or HTTP plugin are granted to the web window. OS keychain,
offline store, calendar and account switching are future work.

Composer is the shared native-backed non-modal editor (compose.ts, compose.rs).
It preserves one in-memory draft through folder/message navigation. Windows owns
file selection; selected files become private snapshots, never WebView paths.
Reply uses the server Reply-To and Message-ID/References, not subject inference.
There is a 10-second reversible grace period before SMTP. Progress separates
connection, upload, server confirmation, acceptance and independent Sent copy.
After handoff, no cancellation/delivery guarantee or automatic retry is implied.
Content-free durable receipts and native draft state lock ambiguous attempts.
Failure to append the Sent copy is a warning on an accepted send, never a reason
to send again. Unknown outcomes keep text available for copying but freeze send.
Discard/logout/close use the shared app-owned confirmation with Keep as initial
focus; Escape preserves the draft. Window destroy permission applies only to the
main local window so this close guard can finish an explicit user close. SMTP
settings use native radio semantics. Draft text is not persisted to disk.
