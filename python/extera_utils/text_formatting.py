'''Слой совместимости: extera_utils.text_formatting.

parse_text(текст, parse_mode="HTML" или "Markdown") отдаёт словарь
{"message": текст без разметки, "entities": список объектов TLRPC}.
'''
import html
from html.parser import HTMLParser

from markdown_utils import RawEntity, TLEntityType, parse_markdown, to_utf16_len

_ТЕГИ = {
    'b': TLEntityType.BOLD, 'strong': TLEntityType.BOLD,
    'i': TLEntityType.ITALIC, 'em': TLEntityType.ITALIC,
    'u': TLEntityType.UNDERLINE, 'ins': TLEntityType.UNDERLINE,
    's': TLEntityType.STRIKETHROUGH, 'strike': TLEntityType.STRIKETHROUGH, 'del': TLEntityType.STRIKETHROUGH,
    'tg-spoiler': TLEntityType.SPOILER, 'spoiler': TLEntityType.SPOILER,
    'code': TLEntityType.CODE, 'pre': TLEntityType.PRE,
    'a': TLEntityType.TEXT_LINK, 'tg-emoji': TLEntityType.CUSTOM_EMOJI,
    'blockquote': TLEntityType.BLOCKQUOTE,
}


class Parser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.text = []
        self.length = 0
        self.stack = []
        self.entities = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        тип = _ТЕГИ.get(tag)
        if tag == 'span' and 'tg-spoiler' in (attrs.get('class') or ''):
            тип = TLEntityType.SPOILER
        if tag == 'br':
            self.handle_data('\n')
            return
        self.stack.append((tag, тип, self.length, attrs))

    def handle_endtag(self, tag):
        for k in range(len(self.stack) - 1, -1, -1):
            if self.stack[k][0] == tag:
                _, тип, start, attrs = self.stack.pop(k)
                break
        else:
            return
        if тип is None or self.length <= start:
            return
        if тип == TLEntityType.CODE and any(t == 'pre' for t, *_ in self.stack):
            # <pre><code class="language-x"> — язык уходит в pre
            язык = (attrs.get('class') or '').replace('language-', '')
            for k, (t, тп, s, a) in enumerate(self.stack):
                if t == 'pre':
                    a['language'] = язык
            return
        e = RawEntity(тип, start, self.length - start)
        if тип == TLEntityType.TEXT_LINK:
            e.url = attrs.get('href') or ''
        elif тип == TLEntityType.CUSTOM_EMOJI:
            e.document_id = int(attrs.get('emoji-id') or attrs.get('emoji_id') or 0)
        elif тип == TLEntityType.PRE:
            e.language = attrs.get('language') or ''
        elif тип == TLEntityType.BLOCKQUOTE:
            e.collapsed = 'expandable' in attrs
        self.entities.append(e)

    def handle_data(self, data):
        self.text.append(data)
        self.length += to_utf16_len(data)


class HTML:
    @staticmethod
    def parse(text):
        p = Parser()
        p.feed(text)
        p.close()
        return ''.join(p.text), sorted(p.entities, key=lambda e: (e.offset, -e.length))

    @staticmethod
    def unparse(text, entities):
        return html.escape(text)


class Markdown:
    @staticmethod
    def parse(text):
        готово = parse_markdown(text)
        return готово.text, готово.entities


def parse_text(text, parse_mode='HTML'):
    mode = str(parse_mode or '').lower()
    if mode == 'html':
        message, raw = HTML.parse(str(text))
    elif mode in ('markdown', 'md', 'markdownv2'):
        message, raw = Markdown.parse(str(text))
    else:
        return {'message': str(text), 'entities': []}
    return {'message': message, 'entities': [e.to_tlrpc_object() for e in raw]}
