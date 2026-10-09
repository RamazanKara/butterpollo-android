import { readdir, readFile, stat } from 'node:fs/promises';
import { dirname, extname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('.', import.meta.url));
const optionalVideos = new Set(['assets/demo-16x9.mp4', 'assets/demo-9x16.mp4']);
const external = new Set();
const errors = [];
const pendingVideos = new Set();
let references = 0;

async function walk(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    if (entry.name.startsWith('.') || entry.name === '_review') continue;
    const path = join(directory, entry.name);
    if (entry.isDirectory()) files.push(...await walk(path));
    else files.push(path);
  }
  return files;
}

const pages = (await walk(root)).filter(file => extname(file) === '.html');
const documents = new Map(await Promise.all(pages.map(async file => [file, await readFile(file, 'utf8')])));
const ids = new Map();
for (const [file, html] of documents) {
  const values = [...html.matchAll(/\bid="([^"]+)"/g)].map(match => match[1]);
  if (new Set(values).size !== values.length) errors.push(relative(root, file) + ': duplicate id');
  ids.set(file, new Set(values));
}

async function check(file, value, attribute) {
  references++;
  const label = relative(root, file);
  if (/^https?:/i.test(value)) {
    try {
      const url = new URL(value);
      if (url.protocol !== 'https:') errors.push(label + ': insecure URL ' + value);
      if (attribute !== 'href') errors.push(label + ': external asset ' + value);
      external.add(value);
      const repoPrefix = '/RamazanKara/rubylight-android/blob/main/';
      if (url.hostname === 'github.com' && url.pathname.startsWith(repoPrefix)) {
        const repoFile = resolve(root, '..', decodeURIComponent(url.pathname.slice(repoPrefix.length)));
        await stat(repoFile);
      }
    } catch {
      errors.push(label + ': invalid URL or missing repository file ' + value);
    }
    return;
  }
  if (value.startsWith('/') || /^[a-z][a-z\d+.-]*:/i.test(value)) {
    errors.push(label + ': non-relative local reference ' + value);
    return;
  }
  const [pathAndQuery, fragment] = value.split('#');
  const path = decodeURIComponent(pathAndQuery.split('?')[0]);
  let target = path ? resolve(dirname(file), path) : file;
  let local = relative(root, target).replaceAll('\\', '/');
  if (local.startsWith('../') || local === '..') {
    errors.push(label + ': reference leaves site ' + value);
    return;
  }
  try {
    let info = await stat(target);
    if (info.isDirectory()) {
      target = join(target, 'index.html');
      info = await stat(target);
    }
    if (!info.isFile()) throw new Error('Not a file');
    if (fragment && extname(target) === '.html' && !ids.get(target)?.has(decodeURIComponent(fragment))) {
      errors.push(label + ': missing anchor ' + value);
    }
  } catch {
    if (attribute === 'data-src' && optionalVideos.has(local)) pendingVideos.add(local);
    else errors.push(label + ': missing file ' + value);
  }
}

for (const [file, html] of documents) {
  for (const match of html.matchAll(/\b(href|src|poster|data-src|data-portrait-poster)="([^"]*)"/g)) {
    await check(file, match[2], match[1]);
  }
  const og = html.match(/property="og:image" content="([^"]+)"/);
  if (og) await check(file, og[1], 'content');
}
for (const file of (await walk(root)).filter(file => extname(file) === '.css')) {
  const css = await readFile(file, 'utf8');
  for (const match of css.matchAll(/url\(\s*["']?([^"')\s]+)["']?\s*\)/g)) await check(file, match[1], 'url');
}
if (errors.length) {
  console.error(errors.join('\n'));
  process.exitCode = 1;
} else {
  console.log('PASS: ' + pages.length + ' pages, ' + references + ' references; local files, anchors and repository doc paths.');
}
if (pendingVideos.size) console.log('Expected future video files: ' + [...pendingVideos].join(', '));
console.log('External URLs: ' + external.size + ' syntax-checked; remote availability is not checked.');
