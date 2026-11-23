package org.telegram.ui;

/** Шейдеры питомца. GLSL ES 1.00, чтобы шло на любом GLES 2.0. */
final class FemboyPetShaders {

    private FemboyPetShaders() {
    }

    // материалы (uMat)
    static final int FUR = 0, CREAM = 1, EYE = 2, NOSE = 3, EAR_IN = 4, SHELL = 5, LINE = 6;
    // варианты меха (uVar)
    static final int V_BODY = 0, V_HEAD = 1, V_EAR = 2, V_PAW = 3, V_TAIL = 4;

    static final String PET_VS =
            "uniform mat4 uMVP;\n" +
            "uniform mat4 uModel;\n" +
            "uniform mat4 uNrm;\n" +
            "uniform mat4 uPart;\n" +
            "uniform mat4 uHeadInv;\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec3 aNrm;\n" +
            "varying vec3 vLocal;\n" +
            "varying vec3 vPet;\n" +
            "varying vec3 vHead;\n" +
            "varying vec3 vWorld;\n" +
            "varying vec3 vN;\n" +
            "void main() {\n" +
            "  vec4 p = vec4(aPos, 1.0);\n" +
            "  vLocal = aPos;\n" +
            "  vec4 pet = uPart * p;\n" +
            "  vPet = pet.xyz;\n" +
            "  vHead = (uHeadInv * pet).xyz;\n" +
            "  vWorld = (uModel * p).xyz;\n" +
            "  vN = (uNrm * vec4(aNrm, 0.0)).xyz;\n" +
            "  gl_Position = uMVP * p;\n" +
            "}\n";

    static final String PET_FS =
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
            "precision highp float;\n" +
            "#else\n" +
            "precision mediump float;\n" +
            "#endif\n" +
            "uniform float uMat;\n" +
            "uniform float uVar;\n" +
            "uniform vec3 uColor;\n" +
            "uniform vec3 uAccent;\n" +
            "uniform vec3 uCam;\n" +
            "uniform mat4 uView;\n" +
            "uniform float uCrack;\n" +
            "uniform float uClip;\n" +
            "uniform float uShell;\n" +
            "uniform vec4 uEyeL;\n" +
            "uniform vec4 uEyeR;\n" +
            "uniform vec3 uBodyC;\n" +
            "uniform vec3 uBodyR;\n" +
            "varying vec3 vLocal;\n" +
            "varying vec3 vPet;\n" +
            "varying vec3 vHead;\n" +
            "varying vec3 vWorld;\n" +
            "varying vec3 vN;\n" +
            // хеш без sin, чтобы не терять точность на телефонах
            "float hash(vec3 p) {\n" +
            "  p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));\n" +
            "  p *= 17.0;\n" +
            "  return fract(p.x * p.y * p.z * (p.x + p.y + p.z));\n" +
            "}\n" +
            "float noise(vec3 x) {\n" +
            "  vec3 i = floor(x);\n" +
            "  vec3 f = fract(x);\n" +
            "  f = f * f * (3.0 - 2.0 * f);\n" +
            "  return mix(mix(mix(hash(i), hash(i + vec3(1.0, 0.0, 0.0)), f.x),\n" +
            "                 mix(hash(i + vec3(0.0, 1.0, 0.0)), hash(i + vec3(1.0, 1.0, 0.0)), f.x), f.y),\n" +
            "             mix(mix(hash(i + vec3(0.0, 0.0, 1.0)), hash(i + vec3(1.0, 0.0, 1.0)), f.x),\n" +
            "                 mix(hash(i + vec3(0.0, 1.0, 1.0)), hash(i + vec3(1.0, 1.0, 1.0)), f.x), f.y), f.z);\n" +
            "}\n" +
            // крупные пятна для скорлупы
            "float spots(vec3 p) {\n" +
            "  vec3 q = p * 4.2;\n" +
            "  float n = noise(q + 7.3) * 0.72 + noise(q * 2.3 + 3.1) * 0.28;\n" +
            "  float s = smoothstep(0.58, 0.64, n);\n" +
            "  s -= 0.4 * smoothstep(0.72, 0.78, n);\n" +
            "  return clamp(s, 0.0, 1.0);\n" +
            "}\n" +
            // мелкие пятнышки и чёрточки меха; dens от 0 до 1 = гуще
            // ячейки: в каждой одно пятнышко со случайным местом и размером,
            // смотрим только 8 ближних ячеек
            "float dots(vec3 p, float dens) {\n" +
            "  vec3 q = p * vec3(5.5, 8.0, 4.0);\n" +
            "  vec3 c = floor(q);\n" +
            "  vec3 f = q - c;\n" +
            "  vec3 o = step(0.5, f) - 1.0;\n" +
            "  float best = 0.0;\n" +
            "  for (int i = 0; i < 8; i++) {\n" +
            "    vec3 k = o + vec3(mod(float(i), 2.0), mod(floor(float(i) / 2.0), 2.0), floor(float(i) / 4.0));\n" +
            "    vec3 id = c + k;\n" +
            "    float h = hash(id);\n" +
            "    vec3 pt = k + 0.3 + 0.4 * vec3(hash(id + 11.3), hash(id + 27.1), hash(id + 41.7));\n" +
            "    float r = (0.32 + 0.16 * hash(id + 5.9)) * step(0.4 - 0.4 * dens, h);\n" +
            "    float d = length(f - pt);\n" +
            "    best = max(best, 1.0 - smoothstep(r * 0.5, r, d));\n" +
            "  }\n" +
            "  return best * 0.85;\n" +
            "}\n" +
            // тонкая полоска шириной w вокруг d = 0
            "float band(float d, float w) {\n" +
            "  return 1.0 - smoothstep(w, w * 1.8, abs(d));\n" +
            "}\n" +
            // линия трещины на яйце: зигзаг вокруг верхней трети
            "float crackY(vec3 l) {\n" +
            "  float a = atan(l.z, l.x) / 6.2831853;\n" +
            "  float tri = abs(fract(a * 9.0) * 2.0 - 1.0);\n" +
            "  return 0.38 + 0.14 * tri;\n" +
            "}\n" +
            "void main() {\n" +
            "  if (uMat > 5.5) { gl_FragColor = vec4(uColor, 1.0); return; }\n" +
            // верхние слои шерсти: редкие волоски по краю силуэта
            "  vec3 N = normalize(vN);\n" +
            "  vec3 V = normalize(uCam - vWorld);\n" +
            "  if (uShell > 0.0 && (vPet.y < 0.18 || dot(N, V) > 0.45\n" +
            "      || noise(vPet * 70.0) < 0.42 + 0.2 * uShell + 0.4 * max(dot(N, V), 0.0))) discard;\n" +
            "  vec3 alb = uColor;\n" +
            "  float spec = 0.0;\n" +
            "  float shin = 16.0;\n" +
            "  float rimK = 1.0;\n" +
            "  float fur = 1.0;\n" +
            "  float glint = 0.0;\n" +
            "  vec3 creamC = vec3(0.97, 0.9, 0.76);\n" +
            "  vec3 inkC = vec3(0.24, 0.14, 0.07);\n" +
            "  if (uMat < 0.5) {\n" +
            "    vec3 back = uColor * vec3(0.74, 0.66, 0.58);\n" +
            "    float cream = 0.0;\n" +
            "    float ink = 0.0;\n" +
            "    vec3 b = (vPet - uBodyC) / uBodyR;\n" +
            "    if (uVar < 0.5 || (uVar > 2.5 && uVar < 3.5)) {\n" +
            // тело, бёдра и лапки: тёмная спина, светлые грудь, живот и лапы изнутри
            "      alb = mix(uColor, back, smoothstep(-0.1, 0.9, b.y) * 0.6);\n" +
            "      cream = smoothstep(0.25, 0.8, -b.y * 0.95 + b.z * 0.6) * 0.9;\n" +
            "      cream = max(cream, smoothstep(-0.45, -0.85, b.y) * (1.0 - smoothstep(0.55, 0.9, abs(b.x))) * 0.75);\n" +
            "      float dens = smoothstep(0.1, 0.8, abs(b.x)) * (1.0 - smoothstep(0.6, 1.0, b.y));\n" +
            "      ink = dots(vPet, dens) * (1.0 - cream);\n" +
            "      if (uVar > 2.5) {\n" +
            // лапка: светлый носок и щёлочки между пальцами
            "        cream = 0.12 + 0.4 * smoothstep(0.3, 0.95, vLocal.z);\n" +
            "        float toe = band(fract(vLocal.x * 2.0 + 0.5) - 0.5, 0.05) * smoothstep(0.4, 0.8, vLocal.z) * step(0.0, vLocal.y);\n" +
            "        ink = max(ink * 0.5, toe * 0.55);\n" +
            "      }\n" +
            "    } else if (uVar < 1.5) {\n" +
            // голова: координаты головы, перед в +z, радиус 1
            "      vec3 h = vHead;\n" +
            "      float ax = abs(h.x);\n" +
            "      alb = mix(uColor, back, smoothstep(0.2, 1.0, h.y - h.z * 0.4) * 0.55);\n" +
            "      float muzzle = smoothstep(0.05, -0.3, h.y) * smoothstep(0.45, 0.8, h.z) * (1.0 - smoothstep(0.32, 0.55, ax));\n" +
            "      cream = max(muzzle, smoothstep(-0.3, -0.62, h.y));\n" +
            "      cream = max(cream, smoothstep(-0.12, -0.42, h.y) * smoothstep(0.0, 0.5, h.z) * 0.75);\n" +
            "      float d = min(length(vPet - uEyeL.xyz) / uEyeL.w, length(vPet - uEyeR.xyz) / uEyeR.w);\n" +
            "      cream = max(cream, smoothstep(0.9, 1.04, d) * (1.0 - smoothstep(1.3, 1.6, d)) * 0.9);\n" +
            // полоски на лбу: средняя и по две сбоку, рваные
            "      float wob = (noise(h * 5.0) - 0.5) * 0.08;\n" +
            "      float fore = smoothstep(0.3, 0.5, h.y) * smoothstep(-0.5, 0.2, h.z);\n" +
            "      float st = band(h.x + wob, 0.028);\n" +
            "      st = max(st, band(ax - 0.17 + wob - 0.08 * h.y, 0.022) * 0.8);\n" +
            "      st = max(st, band(ax - 0.36 + wob - 0.12 * h.y, 0.02) * 0.55);\n" +
            "      st *= fore * smoothstep(0.3, 0.5, noise(vec3(h.x * 2.0, h.y * 8.0, h.z * 2.0)));\n" +
            // слёзные дорожки от внутренних уголков глаз вниз
            "      float tx = 0.15 + 0.12 * (0.02 - h.y);\n" +
            "      float tear = band(ax - tx, 0.026) * smoothstep(-0.34, -0.2, h.y) * (1.0 - smoothstep(0.0, 0.08, h.y)) * step(0.35, h.z);\n" +
            // полоски на щеках от внешних уголков назад
            "      float cy = -0.06 - 0.35 * (ax - 0.6);\n" +
            "      float chk = smoothstep(0.62, 0.72, ax) * max(band(h.y - cy, 0.024), band(h.y - cy + 0.19, 0.022) * 0.75);\n" +
            "      chk *= step(-0.2, h.z);\n" +
            "      float nape = dots(vPet * 1.2, 0.4) * smoothstep(0.3, -0.2, h.z);\n" +
            "      ink = max(max(st, chk), nape) * (1.0 - muzzle * 0.8);\n" +
            "      ink = max(ink, tear);\n" +
            "    } else if (uVar < 2.5) {\n" +
            // ухо снаружи: светлее к краям, чуть темнее у кончика
            "      alb = mix(uColor * 1.04, back, smoothstep(0.55, 1.0, vLocal.y) * 0.45);\n" +
            "    } else {\n" +
            // хвост: тёмные кольца и тёмный кончик
            "      float a = atan(vLocal.z, vLocal.x);\n" +
            "      ink = band(fract(a * 1.6) - 0.5, 0.12) * 0.8;\n" +
            "    }\n" +
            "    alb = mix(alb, creamC, cream);\n" +
            "    alb = mix(alb, inkC, clamp(ink, 0.0, 1.0) * 0.88);\n" +
            // ворс: мелкие вытянутые штрихи
            "    alb *= 0.93 + 0.1 * noise(vPet * vec3(40.0, 16.0, 40.0));\n" +
            "    N = normalize(N + (vec3(noise(vPet * 23.0), noise(vPet * 23.0 + 4.1), noise(vPet * 23.0 + 8.3)) - 0.5) * 0.18);\n" +
            // верхний слой шерсти светлее, это просвет на кончиках волосков
            "    if (uShell > 0.0) alb = mix(alb, creamC, 0.1);\n" +
            "    spec = 0.03;\n" +
            "  } else if (uMat < 1.5) {\n" +
            "    alb = vec3(0.97, 0.93, 0.85) * (0.92 + 0.12 * noise(vPet * 40.0));\n" +
            "    rimK = 0.6;\n" +
            "  } else if (uMat < 2.5) {\n" +
            // глаз: uVar = радиус зрачка, перед глаза смотрит в +z
            "    float r = length(vLocal.xy);\n" +
            "    vec3 iris = mix(vec3(0.62, 0.36, 0.1), vec3(0.28, 0.14, 0.05), smoothstep(0.3, 0.88, r));\n" +
            "    alb = mix(vec3(0.02, 0.015, 0.012), iris, smoothstep(uVar - 0.03, uVar + 0.02, r));\n" +
            "    alb = mix(alb, vec3(0.06, 0.035, 0.02), smoothstep(0.84, 0.94, r));\n" +
            "    if (vLocal.z < 0.0) alb = vec3(0.03);\n" +
            "    spec = 0.9; shin = 90.0; rimK = 0.15; fur = 0.0; glint = 1.0;\n" +
            "  } else if (uMat < 3.5) {\n" +
            "    spec = 0.35; shin = 40.0; rimK = 0.3; fur = 0.0;\n" +
            "  } else if (uMat < 4.5) {\n" +
            // ухо изнутри: светлое, к краям белые пушистые волоски
            "    float edge = smoothstep(0.35, 0.95, abs(vLocal.x) / max(0.05, 1.0 - 0.55 * (vLocal.y + 1.0) * 0.5));\n" +
            "    alb = mix(vec3(0.72, 0.5, 0.36), vec3(0.9, 0.78, 0.64), smoothstep(-0.9, 0.3, vLocal.y));\n" +
            "    float hair = noise(vec3(vLocal.x * 30.0, vLocal.y * 5.0, 0.5));\n" +
            "    alb = mix(alb, vec3(0.98, 0.94, 0.88), clamp(edge * 0.55 + hair * 0.25, 0.0, 1.0));\n" +
            "    rimK = 0.5; fur = 0.6;\n" +
            "  } else {\n" +
            // скорлупа: тот же мех пятнами, но гладкая и с бликом
            "    float cy = crackY(vLocal);\n" +
            "    if (uClip > 0.5 && vLocal.y > cy) discard;\n" +
            "    if (uClip < -0.5 && vLocal.y < cy) discard;\n" +
            "    float s = spots(vLocal * 1.6);\n" +
            "    alb = mix(vec3(0.9, 0.72, 0.46), vec3(0.36, 0.22, 0.1), s * 0.85);\n" +
            "    alb = mix(alb, creamC, (1.0 - smoothstep(-0.9, 0.2, vLocal.y)) * 0.35);\n" +
            "    if (uCrack > 0.0) {\n" +
            "      float line = 1.0 - smoothstep(0.015, 0.045, abs(vLocal.y - cy));\n" +
            "      float a = atan(vLocal.z, vLocal.x) / 6.2831853;\n" +
            "      float y2 = cy - 0.1 - 0.1 * abs(fract(a * 23.0 + 0.3) * 2.0 - 1.0);\n" +
            "      float branch = (1.0 - smoothstep(0.01, 0.03, abs(vLocal.y - y2)))\n" +
            "          * step(0.55, noise(vec3(a * 12.0, 0.0, 1.7)));\n" +
            "      alb = mix(alb, vec3(0.16, 0.09, 0.05), max(line, branch) * uCrack);\n" +
            "    }\n" +
            "    spec = 0.3; shin = 30.0; fur = 0.0;\n" +
            "    if (!gl_FrontFacing) { alb = vec3(0.22, 0.13, 0.07); N = -N; spec = 0.0; rimK = 0.0; }\n" +
            "  }\n" +
            "  vec3 L = normalize(vec3(-0.45, 0.85, 0.6));\n" +
            "  float diff = max((dot(N, L) + 0.5) / 1.5, 0.0);\n" +
            "  vec3 amb = mix(vec3(0.5, 0.4, 0.33), vec3(0.7, 0.68, 0.7), 0.5 + 0.5 * N.y);\n" +
            "  float ao = mix(0.6, 1.0, smoothstep(0.0, 0.45, vPet.y));\n" +
            "  vec3 col = alb * (amb * 0.7 + vec3(1.0, 0.94, 0.84) * diff * 0.8) * ao;\n" +
            "  float nv0 = max(dot(N, V), 0.0);\n" +
            "  float rim = pow(1.0 - nv0, 3.0);\n" +
            // пушок: край силуэта светлее и мягче, как просвет в шерсти
            "  col = mix(col, alb * 1.15 + vec3(0.06, 0.05, 0.03), pow(1.0 - nv0, 2.0) * 0.55 * fur);\n" +
            "  col += uAccent * rim * (0.3 - 0.15 * fur) * rimK;\n" +
            "  vec3 H = normalize(L + V);\n" +
            "  col += vec3(pow(max(dot(N, H), 0.0), shin) * spec);\n" +
            "  if (glint > 0.5) {\n" +
            // блик в глазу привязан к камере, чтобы был виден всегда
            "    vec3 nv = normalize((uView * vec4(N, 0.0)).xyz);\n" +
            "    col = mix(col, vec3(1.0), smoothstep(0.972, 0.982, dot(nv, normalize(vec3(-0.35, 0.45, 0.82)))));\n" +
            "    col = mix(col, vec3(1.0), 0.6 * smoothstep(0.992, 0.996, dot(nv, normalize(vec3(0.3, -0.25, 0.92)))));\n" +
            "  }\n" +
            "  gl_FragColor = vec4(min(col, vec3(1.0)), 1.0);\n" +
            "}\n";

    static final String SHADOW_VS =
            "uniform mat4 uMVP;\n" +
            "attribute vec3 aPos;\n" +
            "varying vec2 vUv;\n" +
            "void main() {\n" +
            "  vUv = aPos.xz;\n" +
            "  gl_Position = uMVP * vec4(aPos, 1.0);\n" +
            "}\n";

    // выход в premultiplied alpha: TextureView ждёт именно его
    static final String SHADOW_FS =
            "precision mediump float;\n" +
            "uniform float uAlpha;\n" +
            "varying vec2 vUv;\n" +
            "void main() {\n" +
            "  float a = (1.0 - smoothstep(0.15, 1.0, length(vUv))) * uAlpha;\n" +
            "  gl_FragColor = vec4(vec3(0.12, 0.08, 0.05) * a, a);\n" +
            "}\n";
}
