#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
DIST_DIR="$ROOT_DIR/dist"

cd "$ROOT_DIR"

mkdir -p "$DIST_DIR/assets/highlight"

cp node_modules/highlight.js/styles/github.css \
  "$DIST_DIR/assets/highlight/highlight.css"

./node_modules/.bin/asciidoctor-revealjs \
  -D "$DIST_DIR" \
  -a revealjsdir=https://cdn.jsdelivr.net/npm/reveal.js@4.5.0 \
  slides.adoc

python3 - <<'PY'
from pathlib import Path

dist = Path("dist")
slides = dist / "slides.html"
html = slides.read_text()
html = html.replace(
    '<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/reveal.js@4.5.0/plugin/highlight/monokai.css"/>',
    '<link rel="stylesheet" href="assets/highlight/highlight.css"/>',
)
html = html.replace(
    '<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/10.7.3/highlight.min.js"></script>',
    '''<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/highlight.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/languages/scala.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/languages/graphql.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.11.1/languages/sql.min.js"></script>''',
)

# Inject Mermaid.js as ES module
mermaid_init = """
<script type="module">
  import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs';
  
  mermaid.initialize({
    startOnLoad: false,
    theme: 'default',
    themeVariables: {
      fontSize: '40px'
    },
    flowchart: { useMaxWidth: true, htmlLabels: false },
    securityLevel: 'loose'
  });

  function initMermaid() {
    var style = document.createElement('style');
    style.innerHTML = '.reveal .slides { transform: none !important; width: 100% !important; height: auto !important; margin: 0 !important; top: 0 !important; left: 0 !important; }';
    document.head.appendChild(style);

    var sections = document.querySelectorAll('.slides section');
    sections.forEach(function(s) { s.style.display = 'block'; }); // Unhide all
    
    mermaid.run({ querySelector: '.mermaid' }).then(function() {
      document.head.removeChild(style);
      sections.forEach(function(s) { s.style.display = ''; });
    }).catch(function(e) {
      console.error(e);
      document.head.removeChild(style);
      sections.forEach(function(s) { s.style.display = ''; });
    });
  }

  if (typeof Reveal !== 'undefined') {
    if (Reveal.isReady()) {
      initMermaid();
    } else {
      Reveal.on('ready', initMermaid);
    }
  } else {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', initMermaid);
    } else {
      initMermaid();
    }
  }
</script>
"""
html = html.replace('</body>', mermaid_init + '</body>')

slides.write_text(html)

(dist / "index.html").write_text(
    """<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta http-equiv="refresh" content="0; url=slides.html">
    <title>Redirecting...</title>
  </head>
  <body>
    <p><a href="slides.html">Open slides</a></p>
  </body>
</html>
"""
)
PY

echo "Built $DIST_DIR/slides.html"
