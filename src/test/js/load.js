// Loads the browser-global Mini App modules into a fake `window` for node:test.
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const DIR = path.resolve(__dirname, '../../main/resources/static/miniapp/js');

function load() {
  const window = {};
  const ctx = vm.createContext({ window, console, Math, Number, String, Set, Map, RegExp, JSON });
  for (const f of ['util.js', 'pitch.js']) {
    vm.runInContext(fs.readFileSync(path.join(DIR, f), 'utf8'), ctx, { filename: f });
  }
  return window.MiniApp;
}

module.exports = { load };
