'''Слой совместимости: markdown_utils экстеры.

Разметка в стиле телеграма: *жирный*, _курсив_, __подчёркнутый__, ~зачёркнутый~,
||спойлер||, `код`, ```язык
блок```, [текст](ссылка), ![смайл](tg://emoji?id=номер). Обратная косая
черта экранирует. Отступы считаются в UTF-16, как их ждёт телеграм.
'''
import re
from dataclasses import dataclass, field
from enum import Enum
from typing import List, Optional

from java import jclass


class TLEntityType(Enum):
    BOLD = 'bold'
    ITALIC = 'italic'
    UNDERLINE = 'underline'
    STRIKETHROUGH = 'strikethrough'
    SPOILER = 'spoiler'
    CODE = 'code'
    PRE = 'pre'
    TEXT_LINK = 'text_link'
    CUSTOM_EMOJI = 'custom_emoji'
    BLOCKQUOTE = 'blockquote'


_TL = {
    TLEntityType.BOLD: 'TL_messageEntityBold',
    TLEntityType.ITALIC: 'TL_messageEntityItalic',
    TLEntityType.UNDERLINE: 'TL_messageEntityUnderline',
    TLEntityType.STRIKETHROUGH: 'TL_messageEntityStrike',
    TLEntityType.SPOILER: 'TL_messageEntitySpoiler',
    TLEntityType.CODE: 'TL_messageEntityCode',
    TLEntityType.PRE: 'TL_messageEntityPre',
    TLEntityType.TEXT_LINK: 'TL_messageEntityTextUrl',
    TLEntityType.CUSTOM_EMOJI: 'TL_messageEntityCustomEmoji',
    TLEntityType.BLOCKQUOTE: 'TL_messageEntityBlockquote',
}


def to_utf16_len(text):
    return len(text.encode('utf-16-le')) // 2


def count_chars_until(text, index):
    return to_utf16_len(text[:index])


@dataclass
class RawEntity:
    type: TLEntityType
    offset: int
    length: int
    language: Optional[str] = None
    url: Optional[str] = None
    document_id: Optional[int] = None
    collapsed: bool = False

    TLRPC_ENTITIES_MAP = _TL

    def to_tlrpc_object(self):
        тип = self.type if isinstance(self.type, TLEntityType) else TLEntityType(self.type)
        e = jclass('org.telegram.tgnet.TLRPC$' + _TL[тип])()
        e.offset = int(self.offset)
        e.length = int(self.length)
        if тип == TLEntityType.PRE:
            e.language = self.language or ''
        elif тип == TLEntityType.TEXT_LINK:
            e.url = self.url or ''
        elif тип == TLEntityType.CUSTOM_EMOJI:
            e.document_id = int(self.document_id or 0)
        elif тип == TLEntityType.BLOCKQUOTE:
            try:
                e.collapsed = bool(self.collapsed)
            except Exception:
                pass
        return e


@dataclass
class ParsedMessage:
    text: str
    entities: List[RawEntity] = field(default_factory=list)

    @property
    def message(self):
        return self.text


_МЕТКИ = [('||', TLEntityType.SPOILER), ('__', TLEntityType.UNDERLINE), ('*', TLEntityType.BOLD),
          ('_', TLEntityType.ITALIC), ('~', TLEntityType.STRIKETHROUGH)]
_EMOJI = re.compile(r'tg://emoji\?id=(\d+)')


def parse_markdown(markdown):
    src = str(markdown)
    out = []
    длина = 0
    открытые = {}
    готовые = []
    i = 0
    n = len(src)

    def add(t):
        nonlocal длина
        out.append(t)
        длина += to_utf16_len(t)

    while i < n:
        c = src[i]
        if c == '\\' and i + 1 < n:
            add(src[i + 1])
            i += 2
            continue
        if src.startswith('```', i):
            конец = src.find('```', i + 3)
            if конец != -1:
                тело = src[i + 3:конец]
                язык = None
                if '\n' in тело:
                    первая, остальное = тело.split('\n', 1)
                    if первая and re.fullmatch(r'[\w+#.-]+', первая):
                        язык, тело = первая, остальное
                start = длина
                add(тело)
                готовые.append(RawEntity(TLEntityType.PRE, start, длина - start, language=язык or ''))
                i = конец + 3
                continue
        if c == '`':
            конец = src.find('`', i + 1)
            if конец != -1:
                start = длина
                add(src[i + 1:конец])
                готовые.append(RawEntity(TLEntityType.CODE, start, длина - start))
                i = конец + 1
                continue
        if c == '[' or (c == '!' and i + 1 < n and src[i + 1] == '['):
            начало = i + (2 if c == '!' else 1)
            закр = src.find(']', начало)
            if закр != -1 and закр + 1 < n and src[закр + 1] == '(':
                кон = src.find(')', закр + 2)
                if кон != -1:
                    подпись = src[начало:закр]
                    адрес = src[закр + 2:кон]
                    вложенное = parse_markdown(подпись)
                    start = длина
                    for e in вложенное.entities:
                        e.offset += start
                        готовые.append(e)
                    add(вложенное.text)
                    смайл = _EMOJI.fullmatch(адрес) or (re.fullmatch(r'\d+', адрес) and c == '!')
                    if смайл:
                        номер = int(смайл.group(1)) if hasattr(смайл, 'group') and смайл.re.groups else int(адрес)
                        готовые.append(RawEntity(TLEntityType.CUSTOM_EMOJI, start, длина - start, document_id=номер))
                    else:
                        готовые.append(RawEntity(TLEntityType.TEXT_LINK, start, длина - start, url=адрес))
                    i = кон + 1
                    continue
        найдено = False
        for метка, тип in _МЕТКИ:
            if src.startswith(метка, i):
                if метка in открытые:
                    start = открытые.pop(метка)
                    if длина > start:
                        готовые.append(RawEntity(тип, start, длина - start))
                    i += len(метка)
                    найдено = True
                elif src.find(метка, i + len(метка)) != -1:
                    открытые[метка] = длина
                    i += len(метка)
                    найдено = True
                break
        if найдено:
            continue
        add(c)
        i += 1

    готовые.sort(key=lambda e: (e.offset, -e.length))
    return ParsedMessage(''.join(out), готовые)
