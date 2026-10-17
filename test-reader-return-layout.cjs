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
      const target=page.locator('.mail-row-wrap').nth(8);
      await target.evaluate(e=>e.scrollIntoView({block:'center'}));
      const before=await page.evaluate(()=>scrollY);
      await target.locator('.mail-row').click();
      await page.locator('.reader-files-jump').click();
      await page.locator('[data-action="back"]').click();
      const after=await page.evaluate(()=>scrollY);
      assert.ok(Math.abs(after-before)<8,'list scroll restored after reading: before='+before+', after='+after);
      await target.locator('.mail-row').click();
      await page.locator('.reader-files-jump').click();
      await page.evaluate(()=>window.appBack());
      assert.ok(Math.abs((await page.evaluate(()=>scrollY))-before)<8,'system Back restores the same position for cached mail');
      assert.equal(await page.evaluate(()=>bodyReads),1,'returning and reopening does not fetch the body again');
      assert.equal(await page.evaluate(()=>downloads+sends),0);
      assert.deepEqual(errors,[]);
      await page.close();
    }
    console.log('PASS: returning from a long reader restores inbox position at narrow/light/dark/large/landscape without downloads or SMTP.');
  } finally {await browser.close();server.close();}
})().catch(e=>{console.error(e);server.close();process.exitCode=1;});
