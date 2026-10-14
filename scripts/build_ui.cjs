const fs = require('node:fs');
const path = require('node:path');
const esbuild = require('esbuild');
const root = path.resolve(__dirname, '..');

async function bundleUi() {
  const result = await esbuild.build({
    absWorkingDir: root,
    entryPoints: ['ui/app.mjs'],
    outfile: 'assets/app.js',
    bundle: true,
    format: 'iife',
    platform: 'browser',
    target: 'es2022',
    charset: 'utf8',
    minify: false,
    treeShaking: false,
    sourcemap: false,
    legalComments: 'inline',
    write: false,
    metafile: true,
    banner: {js: '/* Generated from ui/app.mjs. Edit ui/ sources and run npm run build:ui. */'}
  });
  if (result.outputFiles.length !== 1) throw new Error('UI must compile into one offline JavaScript asset');
  const imports = Object.values(result.metafile.outputs).flatMap(output => output.imports);
  if (imports.length) throw new Error('External runtime imports are not allowed in the offline UI bundle');
  return result.outputFiles[0].contents;
}

async function buildUi(check = false, target = path.join(root, 'assets/app.js')) {
  const data = await bundleUi();
  const matches = fs.existsSync(target) && Buffer.from(data).equals(fs.readFileSync(target));
  if (check) {
    if (!matches) throw new Error('assets/app.js is stale; run npm run build:ui');
    return false;
  }
  if (matches) return false;
  const temporary = target + '.' + process.pid + '.tmp';
  try {
    fs.writeFileSync(temporary, data, {flag: 'wx'});
    fs.renameSync(temporary, target);
  } finally {
    if (fs.existsSync(temporary)) fs.unlinkSync(temporary);
  }
  return true;
}

module.exports = {bundleUi, buildUi};
if (require.main === module) {
  const args = process.argv.slice(2);
  if (args.some(arg => arg !== '--check') || args.length > 1) {
    console.error('Usage: node scripts/build_ui.cjs [--check]');
    process.exitCode = 1;
  } else {
    buildUi(args.includes('--check')).then(changed => {
      console.log(args.includes('--check') ? 'UI checked' : changed ? 'UI generated' : 'UI unchanged');
    }).catch(error => {console.error('UI build failed: ' + error.message); process.exitCode = 1;});
  }
}
