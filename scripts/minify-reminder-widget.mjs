#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');
const srcPath = path.join(root, 'src/main/widget/reminder.html');
const outPath = path.join(root, 'src/main/resources/widget/reminder.html');
const check = process.argv.includes('--check');

const require = createRequire(path.join(root, 'widget-minify/package.json'));
const { minify: minifyHtml } = require('html-minifier-terser');
const { minify: minifyJs } = require('terser');
const terserPkg = require('terser/package.json');
const htmlMinPkg = require('html-minifier-terser/package.json');

function fail(msg) {
  console.error(msg);
  process.exit(1);
}

function splitHtml(html) {
  const open = '<script>';
  const close = '</script>';
  const i = html.indexOf(open);
  const j = html.indexOf(close, i + open.length);
  if (i < 0 || j < 0) {
    fail('reminder.html: missing <script>...</script>');
  }
  return {
    before: html.slice(0, i + open.length),
    script: html.slice(i + open.length, j),
    after: html.slice(j),
  };
}

function assertScriptSafe(script) {
  for (const bad of ['</span', '</div', '</b>', '</script']) {
    if (script.includes(bad)) {
      fail(`minified script contains forbidden sequence: ${bad}`);
    }
  }
}

async function build() {
  if (!fs.existsSync(srcPath)) {
    fail(`missing source: ${srcPath}`);
  }
  const raw = fs.readFileSync(srcPath, 'utf8');
  const { before, script, after } = splitHtml(raw);

  const js = await minifyJs(script, {
    compress: { passes: 2, dead_code: true, unused: true },
    mangle: true,
    format: { comments: false, ecma: 2015 },
    module: false,
  });
  if (!js.code) {
    fail('terser produced empty script');
  }
  assertScriptSafe(js.code);

  const shell = `${before}__SCRIPT__${after}`;
  const minShell = await minifyHtml(shell, {
    collapseWhitespace: true,
    conservativeCollapse: true,
    removeComments: true,
    minifyCSS: true,
    minifyJS: false,
    keepClosingSlash: true,
    caseSensitive: true,
  });
  if (!minShell.includes('__SCRIPT__')) {
    fail('html minify removed script placeholder');
  }
  if (!minShell.includes('<script>') || !minShell.includes('</script>')) {
    fail('html minify broke <script> tags');
  }

  const out = minShell.replace('__SCRIPT__', js.code);
  const scriptStart = out.indexOf('<script>') + '<script>'.length;
  const scriptEnd = out.indexOf('</script>', scriptStart);
  assertScriptSafe(out.slice(scriptStart, scriptEnd));

  if (!out.includes('class="pitch"') && !out.includes("class='pitch'")) {
    fail('minified HTML lost class="pitch"');
  }

  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  if (check) {
    if (!fs.existsSync(outPath)) {
      fail(`missing committed resource: ${outPath}`);
    }
    const committed = fs.readFileSync(outPath, 'utf8');
    if (committed !== out) {
      fail(
        'src/main/resources/widget/reminder.html is stale; run:\n' +
          '  npm --prefix widget-minify ci && node scripts/minify-reminder-widget.mjs'
      );
    }
    console.log(
      `ok reminder widget minify (terser ${terserPkg.version}, html-minifier-terser ${htmlMinPkg.version})`
    );
    return;
  }

  fs.writeFileSync(outPath, out);
  console.log(`wrote ${path.relative(root, outPath)} (${Buffer.byteLength(out, 'utf8')} bytes)`);
}

build().catch((e) => fail(e.stack || String(e)));
