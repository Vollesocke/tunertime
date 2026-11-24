#!/usr/bin/env python3
"""Generate the Markdown manuals from the in-app help.

    python3 tools/help_to_md.py

reads app/src/main/assets/help/de.html and en.html (the single source,
shown by Help in the app) and writes doc/Handbuch.md and doc/manual.md,
so that the manuals can be read on GitHub.  Edit the HTML, then run this
again and commit all three.  Python standard library only.
"""
import os
import re
import sys
from html.parser import HTMLParser

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
HELP = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'help')

# html file, markdown file, note at the top, link to the other language
JOBS = [
    ('de.html', 'Handbuch.md',
     '_Dieses Handbuch wird aus der Hilfe der App erzeugt '
     '(`app/src/main/assets/help/de.html`, mit '
     '`tools/help_to_md.py`). Änderungen bitte dort machen, nicht hier._',
     '[English version: manual.md](manual.md)'),
    ('en.html', 'manual.md',
     '_This manual is generated from the in-app help '
     '(`app/src/main/assets/help/en.html`, with '
     '`tools/help_to_md.py`). Please edit it there, not here._',
     '[Deutsche Fassung: Handbuch.md](Handbuch.md)'),
]


class Conv(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.o = []
        self.lists = []
        self.pre = False
        self.href = None
        self.skip = False
        self.ids = []

    def w(self, x):
        self.o.append(x)

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        cls = a.get('class', '').split()
        if tag == 'p' and 'top' in cls:        # the "back to contents" line
            self.skip = True
        elif self.skip:
            return
        elif tag == 'p' and 'version' in cls:
            self.w('\n_')
        elif tag == 'h1':
            self.w('\n# ')
        elif tag == 'h2':
            self.w('\n\n## ')
        elif tag == 'h3':
            self.w('\n\n### ')
        elif tag == 'p':
            self.w('\n\n')
        elif tag in ('ul', 'ol'):
            self.lists.append([tag, 0])
            self.w('\n')
        elif tag == 'li':
            top = self.lists[-1]
            top[1] += 1
            self.w('\n' + '   ' * (len(self.lists) - 1) +
                   ('- ' if top[0] == 'ul' else '%d. ' % top[1]))
        elif tag == 'pre':
            self.pre = True
            self.w('\n\n```\n')
        elif tag == 'b' or (tag == 'span' and 'b' in cls):
            self.w('**')
        elif tag == 'a':
            self.href = a.get('href')
            self.w('[')
        elif tag == 'br':
            self.w('  \n')
        elif tag == 'dt':
            self.w('\n\n**')
        elif tag == 'dd':
            self.w('\n')
        elif tag == 'section':
            self.ids.append(a.get('id'))

    def handle_endtag(self, tag):
        if self.skip:
            if tag == 'p':
                self.skip = False
            return
        if tag in ('ul', 'ol'):
            self.lists.pop()
            self.w('\n')
        elif tag == 'pre':
            self.pre = False
            self.w('\n```\n')
        elif tag == 'b':
            self.w('**')
        elif tag == 'span':
            pass  # only span.b is used; closed below
        elif tag == 'a':
            self.w('](%s)' % self.href)
        elif tag == 'dt':
            self.w('**')

    def handle_data(self, data):
        if self.skip:
            return
        if not self.pre:
            data = re.sub(r'\s+', ' ', data)
        self.w(data)


def slug(heading):
    """GitHub's anchor for a heading."""
    h = re.sub(r'[^\w\- ]', '', heading.lower())
    return h.replace(' ', '-')


def convert(html_text, note, other):
    # span.b becomes <b> first, so that its end tag is known
    html_text = re.sub(r'<nav>.*?</nav>', '', html_text, flags=re.S)
    html_text = re.sub(r'<head>.*?</head>', '', html_text, flags=re.S)
    html_text = re.sub(r'<span class="b">(.*?)</span>', r'<b>\1</b>',
                       html_text, flags=re.S)
    html_text = re.sub(r'<span class="[^"]*">(.*?)</span>', r'\1',
                       html_text, flags=re.S)
    c = Conv()
    c.feed(html_text)
    md = ''.join(c.o).replace('****', '')
    md = re.sub(r'(?m)^(_.*?)\s*$', r'\1_', md)        # version line
    md = re.sub(r' +\n', '\n', md)
    md = re.sub(r'\n{3,}', '\n\n', md)
    heads = re.findall(r'(?m)^## (.*)$', md)
    anchors = dict(zip(c.ids, (slug(h) for h in heads)))
    md = re.sub(r'\]\(#([\w-]+)\)',
                lambda m: '](#%s)' % anchors.get(m.group(1), m.group(1)),
                md)
    toc = '\n'.join('%d. [%s](#%s)' % (i + 1, h.split('. ', 1)[1], slug(h))
                    for i, h in enumerate(heads))
    first_h2 = md.index('\n## ')
    md = md[:first_h2] + '\n\n' + toc + '\n' + md[first_h2:]
    # title, version line, then the note and the other language
    title, rest = md.strip().split('\n', 1)
    return '%s\n\n%s\n\n%s\n\n%s\n' % (title, note + ' ' + other,
                                       rest.strip().replace('\n\n', '\n\n', 1),
                                       '')


def main():
    for src, dst, note, other in JOBS:
        with open(os.path.join(HELP, src), encoding='utf-8') as f:
            md = convert(f.read(), note, other)
        with open(os.path.join(ROOT, 'doc', dst), 'w', encoding='utf-8',
                  newline='\n') as f:
            f.write(md.rstrip() + '\n')
        print('doc/' + dst)


if __name__ == '__main__':
    sys.exit(main())
