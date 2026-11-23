"""Small pure-Python replacement for the stdlib audioop helpers we need.

Chaquopy's embedded Python has no audioop extension, and the voice changer plugin
uses only three of its functions: ratecv, tomono and reverse. Everything works on
16-bit little-endian PCM (with a fallback for 8/32 bit), so this is enough.
"""

import struct


def _to_samples(data, width):
    if width == 1:
        return [b - 256 if b > 127 else b for b in data]
    if width == 2:
        count = len(data) // 2
        return list(struct.unpack("<%dh" % count, data[:count * 2]))
    if width == 4:
        count = len(data) // 4
        return list(struct.unpack("<%di" % count, data[:count * 4]))
    raise ValueError("unsupported sample width: %r" % width)


def _from_samples(samples, width):
    clipped = [max(-32768, min(32767, int(x))) for x in samples]
    if width == 1:
        return bytes((x + 256) % 256 for x in clipped)
    if width == 2:
        return struct.pack("<%dh" % len(clipped), *clipped)
    if width == 4:
        return struct.pack("<%di" % len(clipped), *clipped)
    raise ValueError("unsupported sample width: %r" % width)


def reverse(data, width):
    return _from_samples(reversed(_to_samples(data, width)), width)


def tomono(data, width, lfactor, rfactor):
    return _from_samples([x * lfactor + x * rfactor for x in _to_samples(data, width)], width)


def ratecv(data, width, nchannels, inrate, outrate, state=None, weightA=1, weightB=0):
    """Linear-interpolation resampling. Returns (pcm, state), like the stdlib."""
    if not data or inrate <= 0 or outrate <= 0 or nchannels <= 0:
        return (b"", state)
    if inrate == outrate:
        return (data, state)
    samples = _to_samples(data, width)
    frames = len(samples) // nchannels
    if frames == 0:
        return (b"", state)
    out_frames = max(0, int(frames * outrate / inrate))
    out = []
    for i in range(out_frames):
        pos = i * inrate / outrate
        i0 = int(pos)
        frac = pos - i0
        i1 = min(i0 + 1, frames - 1)
        for ch in range(nchannels):
            a = samples[i0 * nchannels + ch]
            b = samples[i1 * nchannels + ch]
            out.append(a + (b - a) * frac)
    return (_from_samples(out, width), state)
