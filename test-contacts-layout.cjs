// Standalone, local-only visual regression. No real contacts are used.
// Measures the actual application stylesheet and current source.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const root = __dirname;
const fixture = fs.readFileSync(path.join(root, 'test-layout.cjs'), 'utf8');
const bridgeSource = fixture.slice(fixture.indexOf('function bridge('), fixture.indexOf('(async()=>{'));
const bridge = new Function(bridgeSource + '; return bridge;')();
const baseCSS = fs.readFileSync(path.join(root, 'assets/app.css'), 'utf8');
const candidateCSS = baseCSS;
const html = fs.readFileSync(path.join(root, 'assets/index.html'), 'utf8')
  .replace(/<meta[^>]*Content-Security-Policy[^>]*>/gi, '')
  .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '')
  .replace(/<link\b[^>]*>/gi, '');
async function measure(page, label, includeMore) {
  const geometry = await page.evaluate(({ includeMore }) => {
    const email = [...document.querySelectorAll('.directory-row .directory-person small')].at(-1);
    const add = document.querySelector('.contact-add');
    const nav = document.querySelector('.bottom');
    const more = document.querySelector('[data-action="contacts-more"]');
    const rect = e => {const r=e.getBoundingClientRect();return {top:r.top,bottom:r.bottom,left:r.left,right:r.right,height:r.height};};
    return {email:rect(email),add:rect(add),nav:rect(nav),more:includeMore?rect(more):null,position:getComputedStyle(add).position,viewport:innerWidth,scrollWidth:document.documentElement.scrollWidth};
  }, { includeMore });
  assert.equal(geometry.position, 'static', label + ': add button must remain in flow');
  assert.ok(geometry.add.height >= 44, label + ': minimum target');
  assert.ok(geometry.email.bottom <= geometry.add.top, label + ': last email overlaps add');
  if (geometry.more) assert.ok(geometry.more.bottom <= geometry.add.top, label + ': pagination overlaps add');
  assert.ok(geometry.add.bottom <= geometry.nav.top + 1, label + ': add overlaps bottom navigation');
  assert.ok(geometry.scrollWidth <= geometry.viewport, label + ': horizontal overflow');
  console.log(label, JSON.stringify(geometry));
}
(async () => {
  let browser;
  try {
    browser = await chromium.launch({...(process.env.CHROMIUM_PATH ? {executablePath:process.env.CHROMIUM_PATH}:{}),headless:true,args:['--no-sandbox','--disable-dev-shm-usage','--disable-crash-reporter','--disable-breakpad']});
    const page = await browser.newPage({viewport:{width:320,height:720}});
    await page.setContent(html);
    await page.addStyleTag({content:candidateCSS});
    await page.evaluate(bridge, {preview:true});
    await page.evaluate(() => {
      window.lucide = {createIcons(){}};
      window.Pilot.contactsPage = (book, query, cookie, request) => {
        const page = cookie ? 1 : 0;
        const people = Array.from({length:30}, (_, i) => ({sender:'Контакт '+String(page*30+i).padStart(3,'0'), email:'contact'+(page*30+i)+'@example.invalid'}));
        if (!page) people[29].email = 'last-'+'long'.repeat(20)+'@example.invalid';
        else people[29].email = 'final-'+'long'.repeat(20)+'@example.invalid';
        window.onNativeResult({request,people,cookie:page?'':'next',hasMore:!page});
      };
    });
    await page.addScriptTag({content:fs.readFileSync(path.join(root,'assets/lucide.min.js'),'utf8')});
    await page.addScriptTag({content:fs.readFileSync(path.join(root,'assets/app.js'),'utf8')});
    await page.locator('.folder-menu summary').click();
    await page.locator('[data-route="contacts"]').click();
    // Approximate text scaling without doubling viewport, padding or icons.
    // Android system font-scale still needs verification on a device.
    async function checkAddress(label) {
      const email=page.locator('.directory-row .directory-person small').last();
      await email.scrollIntoViewIfNeeded();
      const geometry=await email.evaluate(el=>{
        const r=el.getBoundingClientRect(),nav=document.querySelector('.bottom').getBoundingClientRect();
        const hit=document.elementFromPoint(r.left+Math.min(5,r.width/2),r.top+Math.min(5,r.height/2));
        return {top:r.top,bottom:r.bottom,visibleBottom:Math.min(innerHeight,nav.top),hit:hit===el||el.contains(hit)};
      });
      assert.ok(geometry.top>=0&&geometry.bottom<=geometry.visibleBottom+1,label+': last address not fully reachable '+JSON.stringify(geometry));
      assert.ok(geometry.hit,label+': last address covered');
    }
    async function enlargeText() {
      await page.evaluate(() => {
        const elements=[...document.querySelectorAll('#mail-v2 *')].filter(el=>el instanceof HTMLElement);
        for(const el of elements)if(el.dataset.scaled){el.style.fontSize='';delete el.dataset.scaled;}
        const entries=elements.map(el=>[el,parseFloat(getComputedStyle(el).fontSize)]);
        for(const [el,size] of entries){el.style.fontSize=(size*2)+'px';el.dataset.scaled='true';}
      });
    }
    await enlargeText();
    await checkAddress('page1');
    await page.evaluate(() => scrollTo(0,document.documentElement.scrollHeight));
    await measure(page,'320px / text 200% / page 1',true);

    await page.locator('[data-action="contacts-more"]').click();
    await enlargeText();
    await checkAddress('page2');
    await page.evaluate(() => scrollTo(0,document.documentElement.scrollHeight));
    await measure(page,'320px / text 200% / page 2',false);

    console.log('PASS: last email, pagination, add target and bottom navigation geometry.');
  } finally { if(browser) await browser.close(); }
})().catch(error => {console.error(error.message);process.exitCode=1;});
