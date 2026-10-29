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
filesystem, shell or HTTP plugin are granted to the web window. Offline mail, multiple saved accounts and calendar writes are future work.

Composer is the shared native-backed non-modal editor (compose.ts, compose.rs).
It preserves one active local draft per authenticated server/port/user through navigation and restarts. Windows owns
file selection; selected files become private snapshots, never WebView paths.
Reply uses the server Reply-To and Message-ID/References, not subject inference.
There is a 10-second reversible grace period before SMTP. Progress separates
connection, upload, server confirmation, acceptance and independent Sent copy.
After handoff, no cancellation/delivery guarantee or automatic retry is implied.
Content-free durable receipts and native draft state lock ambiguous attempts.
Failure to append the Sent copy is a warning on an accepted send, never a reason
to send again. Unknown outcomes keep text available for copying but freeze send.
Deletion and close/logout failures use the shared app-owned confirmation with Keep as initial
focus; Escape preserves the draft. Window destroy permission applies only to the
main local window so this close guard can finish an explicit user close. SMTP
settings use native radio semantics. Draft text/SMTP settings and file snapshots persist encrypted with Windows DPAPI.
The saved-account checkbox is opt-in; one saved account is shown without exposing
its password, and reconnect is an explicit action. Forgetting credentials leaves
an encrypted draft intact. Owners are SHA-256 identities of server/port/user.
Autosave revisions are monotonic and writes atomic; pending SMTP state is durable
before transmission. Restore never auto-sends and preserves terminal send locks.
Close/minimize saves and hides the editor; deletion has its own confirmation.
Normal window close/logout flushes autosave first; failures retain edits and offer
Keep before an explicit close without saving. Failed decryption is visible and
never silently replaced. The native persistence owners are vault.rs and compose.rs.


Calendar uses the existing CalDav/CalendarDiscovery/CalendarRecurrence Java core.
The sidebar switches between mail and calendar without ending the mail session.
Calendar connection credentials are entered separately and saved explicitly through
Windows DPAPI, one source per mail owner. The source may discover multiple calendars;
one is selected with the platform-owned native select (keyboard and popup owned by
Windows/WebView). CalendarScreen owns day/week/month/agenda and the day modal.
Calendar-content owns normal scrolling, the settings form and day modal have bounded
scroll areas; at short heights the calendar screen can scroll to keep actions reachable.
Month/week use Monday-first civil dates; event times use Europe/Moscow independently
of Windows locale/timezone. All-day DTEND is exclusive. A week crossing a month loads
both months and merges occurrence IDs. Month cells and agenda never claim an empty
day while its data is missing. Refresh failures retain the correct cached period and
expose both the cause and staleness. Events live only in session RAM, cleared on logout;
opening the calendar explicitly reads its saved profile and requests missing periods.
The day modal uses the existing time-rail hierarchy: time, title, calendar, location,
expandable literal description. Escape/close restores focus; no remote markup runs.
Server events are read-only; no create/task controls imply unavailable writes.
Disconnect confirms removal of the local connection only. Native owner/epoch/profile
checks guard commits and late results; cancellation closes the separate HTTP transport.
All calendar URLs require HTTPS and the discovery origin; credentials never reach a
redirect to another origin. Same-origin redirects are followed within a bounded limit.
The public profile contains names/IDs, origin and login,
never a password or private collection URL. Frontend month queries contain calendar
IDs, not credentials/paths. Native code resolves credentials and the collection URL.


Contacts reuse the shared section switcher in main.ts and the native select,
search form, inline live feedback and scroll tokens from mail/calendar. The three
sections share one account; navigation never silently drops a draft. ContactsScreen
owns read-only university book selection and explicit server search (no live
queries or URL/localStorage persistence). Its alphabet is an operable horizontal
scroller, not an incomplete subset. Contacts-list owns scrolling; footer actions
remain reachable at 760×600. Each row uses the mail avatar palette, name/email and
an explicit Write action. The native book selector accepts platform popup geometry.
Book/search/letter changes reset paging and cancel old requests; refresh errors
keep only the same route's previously loaded data. Paging is bounded, explicit,
deduplicated by case-insensitive email and checked against repeated cursor tokens.
The fixed university HTTPS origin, account/epoch boundary, separate worker and
socket cancellation live in DesktopContacts/Engine and mail_request. No new vault
or Android contact store is implied. Records stay in RAM and are cleared at logout.
Composer.address is the single recipient-add owner: preserve existing draft/text/
files/headers, deduplicate, cancel an unstarted send countdown, refuse terminal or
in-flight sends, autosave through the existing composer service. No external mailto
handler or browser navigation gets contact data. Remote contact changes/import are
outside this milestone; no UI promises them. Fixture E2E covers navigation, search,
IME, safe content, paging, stale replies, errors, recipient append and send locks.


Local contact book extends the existing ContactsScreen through LocalContacts, not
a second list. Native contact_book.rs is the only persistence/mutation owner; it
shares the existing DPAPI/atomic-write vault and session gate. Books are scoped by
mail server/port/user, loaded only after authentication, and never stored in WebView
storage. UUID revisions protect every create/edit/delete against stale writes.
Corruption, a foreign owner or failed decrypt must block writes rather than seed an
empty replacement. Up to 5000 contacts; UI displays explicit 50-row batches and
filters the complete loaded local book. This is device-local storage, not sync.
Native book select remains platform-owned. Unsupported university accounts default
to the local book; server books retain their existing read-only rules.
Local editor uses a native dialog, shared field tokens and inline validation; Save
commits only after native confirmation and returns to the list. Failed writes keep
inputs. A conflict freezes Save and preserves text for copying; the user must close,
reload and reopen instead of silently rebasing stale edits. Delete identifies the
contact and affects only this book. confirmation.ts owns shared confirmations for
contact deletion and dirty-editor dismissal: Keep first, Escape preserves, native
focus trap, return focus to the invoker or the New Contact action after row removal.
The existing composer window-close owner accepts an external contact guard before
flushing the mail draft, so no competing close listener bypasses autosave. Native
close during a pending contact commit is blocked; dirty contact cancellation never
discards mail. Controls expose pending state and suppress duplicate submits; late
local reads cannot replace a newer mutation. Local data is cleared from DOM/memory
on logout while the encrypted book remains. Import/export and remote contact writes
are outside this milestone. Failure/CRUD/close/persistence fixture E2E and genuine
Windows DPAPI/CAS unit checks are the verification owners.
