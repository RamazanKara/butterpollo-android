# RUBYLIGHT site

Plain HTML, CSS and JavaScript. No build step or package installation.
All site navigation and assets use relative paths. External links point to
the release, source documentation and RUBYLIGHT host.

## Preview and check

From the repository worktree:

```powershell
& C:\nodejs\node.exe site/preview.mjs
```

Open http://127.0.0.1:4317/rubylight-android/. The project prefix mirrors
GitHub Pages. Stop the server with Ctrl+C. Unknown paths serve the custom
404 page, including nested paths.

In another terminal:

```powershell
& C:\nodejs\node.exe site/check-links.mjs
& C:\nodejs\node.exe --check site/assets/site.js
```

The checker validates local files, anchors, relative paths and linked repository
documents. It checks external URL syntax without making network requests.
Only the two future MP4 files below are allowed to be absent.

## Media

- `assets/settings.png`: copied and optimized from `docs/screenshots/11-controller-choose.png`.
- `assets/controller.png`: copied and optimized from `docs/screenshots/12-controller-buttons.png`.
- `assets/launcher.png` and `assets/favicon.png`: the launcher icon from
  `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`.
- `assets/og.png`: the 1200 × 630 raster export of `assets/og.svg`.
- The screenshots retain their native resolution and pixels. Their PNG image data
  is losslessly recompressed and unused metadata is removed.
- The video posters are local SVG artwork.

Add recorded footage as `assets/demo-16x9.mp4` and `assets/demo-9x16.mp4`.
Use H.264 MP4 with the metadata at the beginning of the file for progressive
playback. Keep the demo silent, or include captions for speech. No autoplay
is used. Near the viewport, the page checks for the matching video, attaches
its source and enables native controls. Until footage exists, the branded
poster remains visible with no broken play control. Portrait uses the
9:16 source and falls back to 16:9; desktop uses 16:9.

The privacy page follows `docs/PRIVACY.md`, including public network checks,
local diagnostics, report sharing and Android backups. Endpoint names stay
in the linked source policy. Update both documents together when policy changes.

## Review

Keep captures in `site/_review/` (ignored by Git). Review each of the seven
HTML pages at 360, 768 and 1440 CSS pixels, in light and dark mode.

- No unintended overlap or horizontal page scrolling.
- No clipped text, broken images, blurry screenshots or unfinished copy.
- Consistent spacing, readable line lengths and visible keyboard focus.
- Navigation, language links, skip links and in-page links work.
- Reduced motion disables smooth scrolling.
- Missing videos retain their poster; supplied videos expose working controls.

## Publish an orphan gh-pages branch

Run these commands yourself from the worktree after reviewing the site.
They publish only the site, using a separate temporary repository. The new
`gh-pages` branch starts with a root commit and has no Android commit history.
No custom Actions workflow is used.

```powershell
$siteSource = (Resolve-Path ./site).Path
$pagesFolder = Join-Path $env:TEMP ('rubylight-pages-' + [guid]::NewGuid().ToString('N'))
$pagesRemote = 'https://github.com/RamazanKara/rubylight-android.git'

git init --initial-branch=gh-pages $pagesFolder
Get-ChildItem -LiteralPath $siteSource -Force |
    Where-Object { $_.Name -notin @('_review', '.gitignore', 'README.md', 'preview.mjs', 'check-links.mjs') } |
    Copy-Item -Destination $pagesFolder -Recurse

git -C $pagesFolder add .
git -C $pagesFolder commit -m "docs(site): publish RUBYLIGHT site"
git -C $pagesFolder remote add origin $pagesRemote
git -C $pagesFolder push -u origin gh-pages
```

For later updates, clone the existing branch into a fresh temporary directory,
replace its tracked site files with the current `site/` contents using the
same exclusions, then commit and push normally. Keep the existing branch
history; do not create another orphan branch for an update.

Enable branch-based Pages using an authenticated repository administrator
account:

```powershell
gh api --method POST repos/RamazanKara/rubylight-android/pages -f build_type=legacy -f "source[branch]=gh-pages" -f "source[path]=/"
```

When Pages already exists, update its source:

```powershell
gh api --method PUT repos/RamazanKara/rubylight-android/pages -f build_type=legacy -f "source[branch]=gh-pages" -f "source[path]=/"
gh api repos/RamazanKara/rubylight-android/pages --jq .html_url
```

The published site is https://ramazankara.github.io/rubylight-android/.
`.nojekyll` keeps the published directory static. The Pages API source and
`legacy` build type are documented in the
[GitHub Pages REST API](https://docs.github.com/en/rest/pages/pages#create-a-github-pages-site).
