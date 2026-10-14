const fs = require('node:fs'), path = require('node:path'), http = require('node:http');
const assert = require('node:assert/strict'), { chromium } = require('playwright');
const assetRoot = process.env.ASSET_ROOT || path.join(__dirname, 'assets');
const server = http.createServer((req, res) => {
  const name = req.url === '/' ? 'index.html' : req.url.slice(1);
  if (!['index.html', 'app.js', 'app.css', 'lucide.min.js'].includes(name)) return res.writeHead(404).end();
  res.setHeader('Content-Type', name.endsWith('.css') ? 'text/css' : name.endsWith('.js') ? 'application/javascript' : 'text/html');
  res.end(fs.readFileSync(path.join(assetRoot, name)));
});
(async () => {
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROMIUM_PATH || undefined });
  try {
    for (const [width, height, dark, large] of [[320,700,true,false], [393,850,false,false], [320,700,true,true], [760,360,true,false]]) {
      const page = await browser.newPage({ viewport: { width, height }, locale: 'ru-RU', reducedMotion: 'reduce' });
      const errors = []; page.on('pageerror', e => errors.push(e.message));
      await page.addInitScript(({ dark, large }) => {
        const day = new Date().toLocaleDateString('en-CA', { timeZone: 'Europe/Moscow' });
        const reply = (request, value) => window.onNativeResult({ request, ...value });
        window.bodyReads = 0; window.downloads = 0; window.sends = 0; window.offline = false;
        window.messages = Array.from({ length: 18 }, (_, i) => ({ folder: 'INBOX', validity: '7', uid: String(i+1),
          sender: 'OTRS Notifications', email: 'otrs@example.invalid', subject: i ? 'Ticket# документы '+i : 'Ticket# Защита завтра в 15-00',
          preview: 'Материалы и обсуждение', date: day+'T10:00:00+03:00', seen: false, hasAttachments: true }));
        const m = messages[0], key = JSON.stringify(['INBOX','7','1']);
        window.saved = { tasks: [], events: [], notes: [], contacts: Array.from({length: 24}, (_,i)=>({ sender: (i < 18 ? 'Александров ' : 'Петров ')+i, email: 'p'+i+'@example.invalid' })),
          settings: { theme: dark ? 'dark' : 'light', large, neuralAnalysis: false, showTasks: false },
          mailLabels: { [key]: 'important' }, responseReminders: [{ id: 'r', mail: m, at: day+'T23:00:00+03:00' }] };
        window.Pilot = { account: () => 'st811210', loadState: () => JSON.stringify(saved), saveState: j => { saved=JSON.parse(j); return true; },
          folders: r => reply(r, { folders: ['INBOX'] }), seen: (f,u,v,state,r) => { messages.find(m=>m.uid===u).seen=state;reply(r,{seen:state}); },
          page: (f,o,s,r) => reply(r, { messages, total: messages.length, nextOffset: messages.length, hasMore: false, offline: true, cachedAt: Date.now()-1200000 }),
          body: (f,u,v,r) => { bodyReads++; reply(r, { message: messages.find(m=>m.uid===u), text: ('Длинная история переписки и цитаты.\n').repeat(170),
            attachments: [{ name: 'Материалы.pdf', size: 300, path: '1.1' }], offline }); },
          calendars: (m,f,r) => reply(r, { calendars: [], events: [] }), downloadAttachments: () => downloads++, send: () => sends++ };
      }, { dark, large });
      await page.goto('http://127.0.0.1:'+server.address().port);
      const first = page.locator('.mail-row-wrap').first();
      await first.locator('.mail-row').click();
      if(process.env.PROBE){await page.locator('[data-action="back"]').click();console.log(await first.evaluate(e=>{const row=e.querySelector('.mail-row'), menu=e.querySelector('.mail-row-actions'), cal=e.querySelector('.row-calendar');return {rowBottom:row.getBoundingClientRect().bottom,menuBottom:menu.getBoundingClientRect().bottom,calendarTop:cal.getBoundingClientRect().top,wrapperBorder:getComputedStyle(e).borderBottomWidth,rowBorder:getComputedStyle(row).borderBottomWidth,badgeMargins:Array.from(row.querySelectorAll('.category-tag,.response-due,.attachment-fact,.message-label'),e=>getComputedStyle(e).marginRight)};}));await page.evaluate(()=>{document.activeElement?.blur();scrollTo(0,150);});await page.waitForTimeout(100);console.log('Scrolled banner visibility:',await page.locator('.offline-banner').evaluate(e=>getComputedStyle(e).visibility));await page.close();break;}
      assert.equal(await page.locator('.reader-files-jump').isVisible(), true);
      assert.equal(await page.locator('.reader-files-jump').textContent(), ' Вложения · 1 · Перейти к файлам');
      await page.locator('.reader-files-jump').focus(); await page.keyboard.press('Enter');
      const files = page.locator('.reader-attachments');
      assert.equal(await files.evaluate(e => e === document.activeElement), true);
      assert.ok((await files.boundingBox()).y >= (await page.locator('.top').boundingBox()).height, 'files not covered by sticky header');
      assert.equal(await page.evaluate(()=>bodyReads), 1); assert.equal(await page.evaluate(()=>downloads+sends), 0);
      await page.locator('[data-action="back"]').click();
      const geometry = await first.evaluate(e => {
        const row=e.querySelector('.mail-row'), action=e.querySelector('.mail-row-actions'), calendar=e.querySelector('.row-calendar');
        const boxes=[row,action,calendar].map(e=>e.getBoundingClientRect());
        return { border:getComputedStyle(e).borderBottomWidth, rowBorder:getComputedStyle(row).borderBottomWidth,
          actionBottom:boxes[1].bottom, rowBottom:boxes[0].bottom, calendarTop:boxes[2].top, calendarHeight:boxes[2].height };
      });
      assert.equal(geometry.border, '1px'); assert.equal(geometry.rowBorder, '0px');
      assert.ok(geometry.actionBottom <= geometry.rowBottom && geometry.calendarTop >= geometry.rowBottom-1, 'menu belongs to main row, calendar stays below');
      assert.ok(geometry.calendarHeight >= 44);
      const badges = first.locator('.category-tag,.response-due,.attachment-fact,.message-label');
      assert.equal(await badges.count(), 4);
      for (const badge of await badges.all()) assert.ok(await badge.evaluate(e=>parseFloat(getComputedStyle(e).marginRight)>=8));
      assert.equal(await page.locator('.inbox-action-dock').evaluate(e=>getComputedStyle(e).borderTopWidth), '1px');
      await page.evaluate(()=>{document.activeElement?.blur();scrollTo(0,0);}); await page.waitForTimeout(100);
      await page.evaluate(()=>scrollTo(0,150)); await page.waitForTimeout(100);
      assert.ok((await page.locator('.mobile').getAttribute('class')).includes('header-collapsed'));
      assert.equal(await page.locator('.offline-banner').evaluate(e=>getComputedStyle(e).visibility), 'visible');
      await page.locator('.mail-row-wrap').last().evaluate(e=>e.scrollIntoView({block:'end'}));
      assert.ok((await page.locator('.mail-row-wrap').last().boundingBox()).y+(await page.locator('.mail-row-wrap').last().boundingBox()).height <= (await page.locator('.inbox-action-dock').boundingBox()).y+1);
      await page.evaluate(()=>{scrollTo(0,0);offline=true;}); await first.locator('.mail-row').click();
      await page.locator('[data-action="reader-more"]').click(); await page.locator('[data-action="row-refresh"]').click();
      await page.locator('.reader-files-jump').click(); assert.equal(await page.locator('.reader-offline-file').count(), 1);
      assert.equal(await page.evaluate(()=>downloads+sends), 0);
      await page.locator('[data-action="back"]').click();
      await page.locator('.folder-menu>summary').click(); await page.locator('#menu [data-route="contacts"]').click();
      await page.locator('[data-action="contact-letter"][data-id="П"]').click();
      const heading = page.locator('[data-contact-letter="П"]');
      assert.equal(await heading.evaluate(e=>e===document.activeElement), true);
      const h=await heading.boundingBox(), bar=await page.locator('.contact-letter-nav').boundingBox();
      assert.ok(h.y >= bar.y+bar.height, 'letter heading not obscured by either sticky panel');
      assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth), true);
      assert.deepEqual(errors, []); await page.close();
    }
    console.log('PASS: calendar row/menu boundary, separated badges, dock edge, natural offline banner, keyboard attachment jump without downloads, offline files and contact letter focus at narrow/light/dark/large/landscape.');
  } finally { await browser.close(); server.close(); }
})().catch(e=>{console.error(e);server.close();process.exit(1);});
