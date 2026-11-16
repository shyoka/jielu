/* 节律 · 手机端交互 */
(function () {
  "use strict";

  const J = window.JIELU;
  const PREFS_KEY = "jielu.prefs.v1";
  const PALETTES = {
    clay: "陶土",
    moss: "苔藓",
    iris: "鸢尾",
    tide: "海盐"
  };
  const TOGGLE_MESSAGES = {
    classReminder: ["课前提醒已开启", "课前提醒已关闭"],
    silentCampus: ["上课自动静音已开启", "上课自动静音已关闭"],
    dailyBrief: ["每日概览已开启", "每日概览已关闭"]
  };

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));
  const prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  const app = $("#app");
  const stage = $(".stage");
  const phoneFrame = $(".phone");
  const phoneWrap = $(".phone-wrap");
  const pager = $("#pager");
  const pageToday = $("#page-today");
  const pageCalendar = $("#page-calendar");
  const pageSettings = $("#page-settings");
  const weekStrip = $("#weekStrip");
  const heroSlot = $("#heroSlot");
  const todayListSlot = $("#todayListSlot");
  const clockSlot = $("#clockSlot");
  const dayEyebrow = $("#dayEyebrow");
  const dayNum = $("#dayNum");
  const dayWeek = $("#dayWeek");
  const daySub = $("#daySub");
  const calMonthLabel = $("#calMonthLabel");
  const calGrid = $("#calGrid");
  const calStats = $("#calStats");
  const selectedTitle = $("#selectedTitle");
  const selectedMeta = $("#selectedMeta");
  const agendaList = $("#agendaList");
  const profileWeek = $("#profileWeek");
  const paletteSwatches = $("#paletteSwatches");
  const paletteName = $("#paletteName");
  const themeSegmented = $("#themeSegmented");
  const snackbar = $("#snackbar");
  const bottomSheet = $("#bottomSheet");
  const sheetScrim = $("#sheetScrim");
  const sheetBody = $("#sheetBody");
  const themeMedia = window.matchMedia("(prefers-color-scheme: dark)");

  const initialPrefs = loadPrefs();
  const nowAtLoad = new Date();
  const state = {
    theme: ["auto", "light", "dark"].includes(initialPrefs.theme) ? initialPrefs.theme : "auto",
    palette: PALETTES[initialPrefs.palette] ? initialPrefs.palette : "clay",
    toggles: Object.assign(
      { classReminder: true, silentCampus: false, dailyBrief: true },
      initialPrefs.toggles || {}
    ),
    debug: Boolean(initialPrefs.debug),
    selectedDate: J.atMidnight(nowAtLoad),
    viewYear: nowAtLoad.getFullYear(),
    viewMonth: nowAtLoad.getMonth(),
    activePage: 0,
    lastTodayKey: J.dateKey(nowAtLoad),
    courseContext: null
  };

  let minuteTimer = null;
  let scrollFrame = null;
  let snackbarTimer = null;
  let lastFocusedElement = null;
  let sheetOpen = false;
  let navScrollLockUntil = 0;
  let lastHeroKey = "";
  let pageDemoTimer = null;
  let loadingDemoEl = null;

  const PHONE_DESIGN_WIDTH = 384;
  const PHONE_DESIGN_HEIGHT = 830;

  /* ---------- 文案与格式化 ---------- */

  function escapeHTML(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function icon(name, className) {
    return `<svg class="icon${className ? " " + className : ""}" viewBox="0 0 24 24" aria-hidden="true"><use href="#i-${name}"></use></svg>`;
  }

  function loadPrefs() {
    try {
      const raw = localStorage.getItem(PREFS_KEY);
      return raw ? JSON.parse(raw) : {};
    } catch (error) {
      return {};
    }
  }

  function savePrefs() {
    try {
      localStorage.setItem(
        PREFS_KEY,
        JSON.stringify({
          theme: state.theme,
          palette: state.palette,
          toggles: state.toggles,
          debug: state.debug
        })
      );
    } catch (error) {}
  }

  function weekLine(date) {
    const parity = J.getWeekParity(date);
    return parity ? `${J.getWeekLabel(date)} · ${parity}` : J.getWeekLabel(date);
  }

  function formatCountdown(minutes) {
    if (minutes < 1) return "即将开始";
    if (minutes < 60) return `还有 ${minutes} 分钟`;
    return `还有 ${J.formatDuration(minutes)}`;
  }

  function formatDayLabel(date) {
    return `${J.formatDate(date)} 周${J.weekdayCN(date, true)}`;
  }

  function getStatusForCourse(course, date, now) {
    if (!J.sameDate(date, now)) return { type: "normal", label: "" };
    const nowMinutes = now.getHours() * 60 + now.getMinutes();
    const start = J.toMinutes(course.start);
    const end = J.toMinutes(course.end);
    if (nowMinutes >= start && nowMinutes < end) {
      return { type: "active", label: "进行中" };
    }
    if (nowMinutes >= end) {
      return { type: "past", label: "已结束" };
    }
    if (start - nowMinutes <= 30) {
      return { type: "soon", label: "即将开始" };
    }
    return { type: "normal", label: "" };
  }

  /* ---------- 外观 ---------- */

  function updateStageScale() {
    if (!stage || !phoneFrame || !phoneWrap) return;
    const compact =
      document.documentElement.dataset.native === "1" ||
      window.matchMedia("(max-width: 759px), (pointer: coarse)").matches;
    if (compact) {
      phoneFrame.style.removeProperty("transform");
      phoneWrap.style.removeProperty("width");
      phoneWrap.style.removeProperty("height");
      return;
    }
    const style = window.getComputedStyle(stage);
    const paddingTop = parseFloat(style.paddingTop) || 0;
    const paddingBottom = parseFloat(style.paddingBottom) || 0;
    // stage 固定为一屏高，但仍以视口高度兜底，防止首帧测量到被内容撑开的高度
    const viewportHeight = window.innerHeight || document.documentElement.clientHeight;
    const availableHeight = Math.min(stage.clientHeight || viewportHeight, viewportHeight) - paddingTop - paddingBottom;
    const scale = Math.max(0.32, Math.min(1, availableHeight / PHONE_DESIGN_HEIGHT));
    phoneFrame.style.transform = `scale(${scale})`;
    phoneWrap.style.width = `${PHONE_DESIGN_WIDTH * scale}px`;
    phoneWrap.style.height = `${PHONE_DESIGN_HEIGHT * scale}px`;
  }

  function resolvedTheme() {
    if (state.theme === "dark") return "dark";
    if (state.theme === "light") return "light";
    return themeMedia.matches ? "dark" : "light";
  }

  function applyAppearance() {
    const theme = resolvedTheme();
    document.documentElement.dataset.theme = theme;
    document.documentElement.dataset.palette = state.palette;

    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) {
      const surface = getComputedStyle(document.documentElement)
        .getPropertyValue("--surface")
        .trim();
      if (surface) meta.setAttribute("content", surface);
    }

    $$("[data-theme-mode]", themeSegmented).forEach((button) => {
      button.setAttribute("aria-pressed", String(button.dataset.themeMode === state.theme));
    });

    $$(".swatch", paletteSwatches).forEach((button) => {
      button.setAttribute("aria-pressed", String(button.dataset.palette === state.palette));
    });

    $$("[data-intro-palette]").forEach((button) => {
      const active = button.dataset.introPalette === state.palette;
      button.classList.toggle("is-active", active);
      button.setAttribute("aria-pressed", String(active));
    });

    const introPaletteName = $("#introPaletteName");
    if (introPaletteName) introPaletteName.textContent = PALETTES[state.palette];
    if (paletteName) paletteName.textContent = `${PALETTES[state.palette]} · Material You`;

    syncWatchPalette();
    syncSystemBars(theme);
  }

  /* 原生壳（APK）注入的桥接：让状态栏 / 导航栏跟随当前 surface 色 */
  let colorProbe = null;

  function resolveColor(value, fallback) {
    if (!value) return fallback;
    try {
      if (!colorProbe) {
        colorProbe = document.createElement("canvas");
        colorProbe.width = 1;
        colorProbe.height = 1;
      }
      const ctx = colorProbe.getContext("2d", { willReadFrequently: true });
      ctx.clearRect(0, 0, 1, 1);
      ctx.fillStyle = value;
      ctx.fillRect(0, 0, 1, 1);
      const data = ctx.getImageData(0, 0, 1, 1).data;
      return `#${[data[0], data[1], data[2]]
        .map((n) => n.toString(16).padStart(2, "0"))
        .join("")}`;
    } catch (error) {
      return fallback;
    }
  }

  function syncSystemBars(theme) {
    const bridge = window.JieluNative;
    if (!bridge || typeof bridge.setSystemBars !== "function") return;
    try {
      const app = $(".app");
      const surface = app ? getComputedStyle(app).backgroundColor : "";
      const color = resolveColor(surface, theme === "dark" ? "#151110" : "#fdfaf7");
      bridge.setSystemBars(color, theme === "light");
    } catch (error) {}
  }

  function syncWatchPalette() {
    $$("iframe").forEach((frame) => {
      try {
        frame.contentWindow.postMessage(
          { type: "jielu:appearance", palette: state.palette },
          "*"
        );
      } catch (error) {}
    });
  }

  /* ---------- 开源信息 ---------- */

  const REPO = {
    owner: "shyoka",
    name: "jielu",
    url: "https://github.com/shyoka/jielu",
    issues: "https://github.com/shyoka/jielu/issues",
    profile: "https://github.com/shyoka",
    license: "MIT",
    // 手表安装包与手机包放在同一发布页，latest 永远指向最新一版
    wearRelease: "https://github.com/shyoka/jielu/releases/latest",
    version: "1.6.1"
  };

  function openExternal(url) {
    if (!url) return;
    const bridge = window.JieluNative;
    if (bridge && typeof bridge.openUrl === "function") {
      try {
        bridge.openUrl(url);
        return;
      } catch (error) {}
    }
    window.open(url, "_blank", "noopener");
  }

  function copyText(text) {
    return new Promise((resolve) => {
      const fallback = () => {
        const area = document.createElement("textarea");
        area.value = text;
        area.setAttribute("readonly", "");
        area.style.cssText = "position:fixed;top:-1000px;left:0;opacity:0";
        document.body.appendChild(area);
        area.select();
        let ok = false;
        try {
          ok = document.execCommand("copy");
        } catch (error) {
          ok = false;
        }
        document.body.removeChild(area);
        resolve(ok);
      };

      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(() => resolve(true)).catch(fallback);
      } else {
        fallback();
      }
    });
  }

  const SOURCE_FACTS = [
    ["界面", "Material You · 无框架"],
    ["数据", "localStorage · 不出设备"],
    ["构建", "WebView 壳 · 单 Activity"],
    ["许可", "MIT · 可商用可修改"]
  ];

  function openSourceSheet() {
    openSheet(`
      <div class="notes-kicker">
        <p>开源信息 · OPEN SOURCE</p>
        <button class="sheet-close" type="button" data-close-sheet aria-label="关闭">${icon("x")}</button>
      </div>
      <h2 class="notes-title" id="sheetTitle">节律是<br /><span>开源的。</span></h2>
      <p class="notes-intro">全部代码公开托管在 GitHub，欢迎读、改、提 issue。课程表这种东西每个人的学期都不一样，与其等一个更新，不如自己动手改。</p>

      <div class="oss-repo-card">
        <span class="oss-repo-mark">${icon("github")}</span>
        <div class="oss-repo-copy">
          <b>${REPO.owner}/${REPO.name}</b>
          <small>${REPO.url.replace("https://", "")}</small>
        </div>
        <span class="oss-license">${REPO.license}</span>
      </div>

      <div class="oss-facts">
        ${SOURCE_FACTS.map(
          ([label, value]) => `<div class="oss-fact"><small>${label}</small><b>${value}</b></div>`
        ).join("")}
      </div>

      <div class="oss-note">
        <b>当前版本 ${REPO.version}</b>
        <p>HTML / CSS / JavaScript 手写，没有前端框架；Android 端是一个单 Activity 的 WebView 壳。课程与偏好只写在本机 localStorage，没有账号，也没有埋点。</p>
      </div>

      <div class="oss-actions">
        <button class="btn btn-primary" type="button" data-open-repo>${icon("github")}在 GitHub 打开</button>
        <button class="btn btn-tonal" type="button" data-copy-repo>${icon("copy")}复制仓库地址</button>
      </div>

      <div class="oss-foot-links">
        <button type="button" data-open-issues>${icon("heart")}报告问题或提想法</button>
        <button type="button" data-close-sheet>${icon("x")}知道了</button>
      </div>
    `);
  }

  function buildPaletteSwatches() {
    paletteSwatches.innerHTML = J.TONES.map(
      (tone) => `
        <button class="swatch" type="button" style="--swatch: ${J.PALETTE_SEEDS[tone.key]}" data-palette="${tone.key}" aria-label="${tone.name}" aria-pressed="${tone.key === state.palette}">
          ${icon("check")}
        </button>`
    ).join("");
  }

  /* ---------- 今日课表 ---------- */

  function renderToday(options) {
    const opts = options || {};
    const pageScrollTop = opts.resetScroll ? 0 : pageToday.scrollTop;
    const now = new Date();
    const date = state.selectedDate;
    const isToday = J.sameDate(date, now);
    const courses = J.getCoursesForDate(date);
    const stats = J.getDayStats(courses);
    const weekLabel = J.getWeekLabel(date);

    const parity = J.getWeekParity(date);
    dayEyebrow.innerHTML = `${date.getFullYear()} 年 ${date.getMonth() + 1} 月 <span class="sep"></span> ${
      isToday ? "今天" : weekLabel
    }${parity ? ` <span class="sep"></span> ${parity}` : ""}`;
    dayNum.textContent = String(date.getDate());
    dayWeek.textContent = J.weekdayCN(date);

    if (courses.length) {
      daySub.textContent = `${stats.count} 节课 · ${stats.range} · ${J.formatDuration(stats.minutes)}`;
    } else {
      const weekend = date.getDay() === 0 || date.getDay() === 6;
      daySub.textContent = weekend ? "周末没有课" : `没有课程 · ${weekLabel}`;
    }

    if (isToday) {
      clockSlot.innerHTML = `
        <div class="clock-card">
          <span class="live-label"><span class="live-dot"></span>现在</span>
          <span class="time num" data-clock>${J.formatClock(now)}</span>
        </div>`;
    } else {
      clockSlot.innerHTML = `
        <button class="back-today" type="button" id="backToday">
          ${icon("calendar")}回到今天
        </button>`;
    }

    weekStrip.innerHTML = J.getWeekDates(date)
      .map((day) => {
        const selected = J.sameDate(day, date);
        const today = J.sameDate(day, now);
        const count = J.getCoursesForDate(day).length;
        const cls = [
          "day-pill",
          selected ? "is-selected" : "",
          today ? "is-today" : ""
        ]
          .filter(Boolean)
          .join(" ");
        return `
          <button class="${cls}" type="button" data-date="${J.dateKey(day)}" aria-pressed="${selected}" aria-label="${J.formatDate(day, { full: true })} ${J.weekdayCN(day)}${count ? `，${count} 节课` : "，没有课程"}">
            <span class="wd">${J.weekdayCN(day, true)}</span>
            <span class="d">${day.getDate()}</span>
            ${count ? '<span class="dot"></span>' : ""}
          </button>`;
      })
      .join("");

    heroSlot.innerHTML =
      buildAdjustBanner(J.getAdjustmentForDate(date)) +
      buildHero(courses, date, isToday, now);

    // 进度环只在「换了一节课 / 换了一天」时从零转起，分钟级的重绘不打扰它
    const heroCourse = $(".hero[data-course-id]", heroSlot);
    const heroKey = heroCourse ? heroCourse.dataset.courseId : "";
    if (heroKey !== lastHeroKey) {
      lastHeroKey = heroKey;
      animateHeroRing();
    }

    if (courses.length) {
      todayListSlot.innerHTML = `
        <div class="section-head">
          <h3>${isToday ? "今日安排" : "当日安排"}</h3>
          <div class="section-tools">
            <span class="section-meta">${stats.count} 节 · ${J.formatDuration(stats.minutes)}</span>
            <button class="section-add" type="button" data-add-course aria-label="添加课程" title="添加课程">${icon("plus")}</button>
          </div>
        </div>
        <div class="course-list">
          ${courses.map((course) => buildCourseRow(course, date, now)).join("")}
        </div>`;
    } else {
      todayListSlot.innerHTML = buildUpcomingSection(date);
    }

    pageToday.scrollTop = pageScrollTop;
  }

  function buildAdjustBanner(adjustment) {
    if (!adjustment) return "";
    const adj = adjustment.adjustment;
    let title = "";
    let detail = "";
    let tone = "in";
    if (adjustment.type === "swap-in") {
      const source = J.parseDateKey(adj.from);
      title = "调休上课";
      detail = `按${J.weekdayCN(source)}的课表${adj.note ? ` · ${adj.note}` : ""}`;
      tone = "in";
    } else if (adjustment.type === "swap-out") {
      const target = J.parseDateKey(adj.to);
      title = "今天停课";
      detail = `已调到 ${J.formatDate(target)} 周${J.weekdayCN(target, true)}${
        adj.note ? ` · ${adj.note}` : ""
      }`;
      tone = "out";
    } else {
      title = "今天放假";
      detail = adj.note || "今天没有课程";
      tone = "off";
    }
    return `
      <div class="adjust-banner adjust-banner--${tone}">
        <span class="adjust-mark">${icon("calendar")}</span>
        <span class="adjust-copy"><b>${title}</b><small>${escapeHTML(detail)}</small></span>
      </div>`;
  }

  function buildRing(progress, value, label, muted) {
    const radius = 44;
    const circumference = 2 * Math.PI * radius;
    const valueOffset = muted
      ? circumference * 0.86
      : circumference * (1 - Math.max(0.015, Math.min(progress, 1)));
    return `
      <div class="hero-ring">
        <svg viewBox="0 0 100 100" aria-hidden="true">
          <circle class="ring-track" cx="50" cy="50" r="${radius}"></circle>
          <circle class="ring-value" cx="50" cy="50" r="${radius}" style="stroke-dasharray:${circumference.toFixed(
            2
          )};stroke-dashoffset:${valueOffset.toFixed(2)}"></circle>
        </svg>
        <div class="ring-label"><b>${value}</b><span>${label}</span></div>
      </div>`;
  }

  function buildHero(courses, date, isToday, now) {
    const stats = J.getDayStats(courses);
    const nowMinutes = now.getHours() * 60 + now.getMinutes();

    if (!courses.length) {
      return buildEmptyHero(date, isToday);
    }

    if (!isToday) {
      const first = courses[0];
      return `
        <article class="hero c-${first.tone}" role="button" tabindex="0" data-course-id="${first.id}" aria-label="查看${escapeHTML(first.name)}详情">
          <div class="hero-top">
            <span class="status-pill"><span class="status-dot"></span>当日安排</span>
            <span class="hero-time">${stats.range}</span>
          </div>
          <div class="hero-body">
            <div class="hero-copy">
              <h2>${stats.count} 节课，慢慢来。</h2>
              <p class="hero-meta">${icon("clock")}${stats.start} 开始 <span class="meta-sep">·</span> ${escapeHTML(first.name)}</p>
            </div>
            <div class="hero-side"><div class="count-orb"><b>${stats.count}</b><span>节课</span></div></div>
          </div>
          <div class="hero-foot hero-foot--plain">
            <div class="hero-footline"><span>${J.formatDuration(stats.minutes)}</span><span>查看第一节</span></div>
          </div>
        </article>`;
    }

    const { current, next } = J.getCurrentAndNext(courses, nowMinutes);

    if (current) {
      const start = J.toMinutes(current.start);
      const end = J.toMinutes(current.end);
      const progress = (nowMinutes - start) / (end - start);
      const remaining = Math.max(1, end - nowMinutes);
      const elapsed = Math.max(0, nowMinutes - start);
      return `
        <article class="hero c-${current.tone}" role="button" tabindex="0" data-course-id="${current.id}" aria-label="查看${escapeHTML(current.name)}详情">
          <div class="hero-top">
            <span class="status-pill"><span class="status-dot"></span>正在上课</span>
            <span class="hero-time">${current.start} – ${current.end}</span>
          </div>
          <div class="hero-body">
            <div class="hero-copy">
              <h2>${escapeHTML(current.name)}</h2>
              <p class="hero-meta">${icon("pin")}${escapeHTML(current.room)} <span class="meta-sep">·</span> ${escapeHTML(current.teacher)}</p>
            </div>
            <div class="hero-side">${buildRing(progress, remaining, "分钟后下课", false)}</div>
          </div>
          <div class="hero-foot">
            <div class="hero-progress"><span style="width:${Math.min(100, Math.max(0, progress * 100)).toFixed(1)}%"></span></div>
            <div class="hero-footline"><span>${elapsed ? `已上 ${elapsed} 分钟` : "刚刚开始"}</span><span>${Math.round(progress * 100)}%</span></div>
          </div>
        </article>`;
    }

    if (next) {
      const minutesUntil = J.toMinutes(next.start) - nowMinutes;
      const index = courses.indexOf(next) + 1;
      return `
        <article class="hero c-${next.tone}" role="button" tabindex="0" data-course-id="${next.id}" aria-label="查看${escapeHTML(next.name)}详情">
          <div class="hero-top">
            <span class="status-pill"><span class="status-dot"></span>接下来</span>
            <span class="hero-time">${next.start} – ${next.end}</span>
          </div>
          <div class="hero-body">
            <div class="hero-copy">
              <h2>${escapeHTML(next.name)}</h2>
              <p class="hero-meta">${icon("pin")}${escapeHTML(next.room)} <span class="meta-sep">·</span> ${escapeHTML(next.teacher)}</p>
            </div>
            <div class="hero-side">${buildRing(0, minutesUntil, "分钟后", true)}</div>
          </div>
          <div class="hero-foot hero-foot--plain">
            <div class="hero-footline"><span>${J.getPeriodLabel(next) || `第 ${index} 节`} · ${escapeHTML(next.kind)}</span><span>${formatCountdown(minutesUntil)}</span></div>
          </div>
        </article>`;
    }

    const nextDay = J.getNextTeachingDay(date, 14);
    const nextLine = nextDay
      ? `下一节 · ${formatDayLabel(nextDay.date)} ${nextDay.courses[0].start} ${nextDay.courses[0].name}`
      : "这学期的课已经告一段落";
    return `
      <article class="hero hero--quiet" aria-label="今天的课程已结束">
        <div class="hero-top">
          <span class="status-pill"><span class="status-dot"></span>今日完成</span>
          <span class="hero-time">${stats.end} 下课</span>
        </div>
        <div class="hero-body">
          <div class="hero-copy">
            <h2>今天的课，结束啦。</h2>
            <p class="hero-meta">${icon("check")}${stats.count} 节课 · ${J.formatDuration(stats.minutes)}</p>
          </div>
          <div class="hero-side"><div class="hero-symbol">${icon("check")}</div></div>
        </div>
        <div class="hero-foot hero-foot--plain">
          <div class="hero-footline"><span class="ellipsis">${nextLine}</span><span>慢慢走</span></div>
        </div>
      </article>`;
  }

  function buildEmptyHero(date, isToday) {
    if (!J.getAllCourses().length) {
      return `
        <article class="hero hero--quiet" aria-label="课表还是空的">
          <div class="hero-top">
            <span class="status-pill"><span class="status-dot"></span>新的开始</span>
            <span class="hero-time">${J.weekdayCN(date)}</span>
          </div>
          <div class="hero-body">
            <div class="hero-copy">
              <h2>课表还空着。</h2>
              <p class="hero-meta">${icon("book")}添加第一门课程，让节律开始运转</p>
            </div>
            <div class="hero-side"><div class="hero-symbol">${icon("book")}</div></div>
          </div>
          <div class="hero-foot hero-foot--plain">
            <div class="hero-footline"><span>从一门课开始</span><span>慢慢来</span></div>
          </div>
        </article>`;
    }
    const next = J.getNextCourseFrom(date, false);
    const beforeTerm = J.getWeekNumber(date) < 1;
    const nextLine = next
      ? `下一节 · ${formatDayLabel(next.date)} ${next.course.start} ${next.course.name}`
      : beforeTerm
        ? "学期还没开始，先歇一歇"
        : "学期课程已经结束";
    return `
      <article class="hero hero--quiet" aria-label="${isToday ? "今天没有课程" : "这一天没有课程"}">
        <div class="hero-top">
          <span class="status-pill"><span class="status-dot"></span>${isToday ? "今天休息" : "没有课程"}</span>
          <span class="hero-time">${J.weekdayCN(date)}</span>
        </div>
        <div class="hero-body">
          <div class="hero-copy">
            <h2>${isToday ? "给自己留一点空白。" : "这一天没有课。"}</h2>
            <p class="hero-meta">${icon("leaf")}把时间留给更重要的事</p>
          </div>
          <div class="hero-side"><div class="hero-symbol">${icon("leaf")}</div></div>
        </div>
        <div class="hero-foot hero-foot--plain">
          <div class="hero-footline"><span class="ellipsis">${nextLine}</span><span>${isToday ? "好好休息" : "翻翻日历"}</span></div>
        </div>
      </article>`;
  }

  function buildCourseRow(course, date, now) {
    const status = getStatusForCourse(course, date, now);
    const classes = ["course-item", `c-${course.tone}`];
    if (status.type === "active") classes.push("is-active");
    if (status.type === "past") classes.push("is-past");
    const kicker =
      status.label && status.type !== "soon"
        ? `<span class="status${status.type === "past" ? " is-finished" : ""}">${status.label}</span>`
        : status.type === "soon"
          ? `<span class="status">${status.label}</span>`
          : "";
    return `
      <button class="${classes.join(" ")}" type="button" data-course-id="${course.id}" aria-label="查看${escapeHTML(course.name)}详情">
        <span class="course-time">
          <span class="start">${course.start}</span>
          <span class="end">${course.end}</span>
        </span>
        <span class="course-body">
          <span class="course-kicker">${escapeHTML(course.kind)}${kicker}</span>
          <h4>${escapeHTML(course.name)}</h4>
          <span class="course-meta">${escapeHTML(course.room)} <span>·</span> ${escapeHTML(course.teacher)}</span>
        </span>
        <span class="course-chev">${icon("chevron-right")}</span>
      </button>`;
  }

  function buildUpcomingSection(date) {
    const days = [];
    for (let offset = 1; offset <= 14 && days.length < 2; offset += 1) {
      const nextDate = J.addDays(date, offset);
      const courses = J.getCoursesForDate(nextDate);
      if (courses.length) days.push({ date: nextDate, courses });
    }

    if (!days.length) {
      const blank = !J.getAllCourses().length;
      return `
        <div class="section-head"><h3>接下来</h3><span class="section-meta">${blank ? "等待第一节课" : "学期尾声"}</span></div>
        <div class="agenda-empty">
          <span class="empty-orb">${icon(blank ? "book" : "sparkles")}</span>
          <h4>${blank ? "课表还是空的" : "暂时没有新的课程"}</h4>
          <p>${blank ? "添加第一门课程，节律就开始运转了。" : "给这段时间留一点呼吸，或者添加一门课。"}</p>
          <button class="btn btn-tonal empty-action" type="button" data-add-course>${icon("plus")}添加课程</button>
        </div>`;
    }

    return `
      <div class="section-head"><h3>接下来</h3><span class="section-meta">下一段节奏</span></div>
      <div class="upcoming-list">
        ${days
          .map(
            ({ date: nextDate, courses }) => `
            <button class="upcoming-item" type="button" data-date="${J.dateKey(nextDate)}" aria-label="查看${J.formatDate(nextDate, { full: true })}的课表">
              <span class="upcoming-date"><b>${nextDate.getDate()}</b><span>周${J.weekdayCN(nextDate, true)}</span></span>
              <span class="upcoming-copy">
                <h4>${escapeHTML(courses[0].name)}</h4>
                <p>${courses[0].start} · ${escapeHTML(courses[0].room)}</p>
              </span>
              <span class="upcoming-pill">${courses.length} 节</span>
              ${icon("chevron-right")}
            </button>`
          )
          .join("")}
      </div>`;
  }

  /* ---------- 日历 ---------- */

  function renderCalendar() {
    const pageScrollTop = pageCalendar.scrollTop;
    const now = new Date();
    const selected = state.selectedDate;
    const first = new Date(state.viewYear, state.viewMonth, 1);
    const offset = (first.getDay() + 6) % 7;
    const gridStart = J.addDays(first, -offset);
    const stats = J.monthStats(state.viewYear, state.viewMonth);

    calMonthLabel.textContent = `${state.viewYear} 年 ${state.viewMonth + 1} 月`;
    calStats.textContent = `${stats.count} 节课 · ${stats.activeDays} 个上课日`;

    const cells = [];
    for (let index = 0; index < 42; index += 1) {
      const date = J.addDays(gridStart, index);
      const isOtherMonth = date.getMonth() !== state.viewMonth;
      const isSelected = J.sameDate(date, selected);
      const isToday = J.sameDate(date, now);
      const courses = J.getCoursesForDate(date);
      const dots = courses
        .slice(0, 3)
        .map((course) => `<span class="cal-dot c-${course.tone}"></span>`)
        .join("");
      const classes = [
        "cal-cell",
        isOtherMonth ? "is-other" : "",
        isSelected ? "is-selected" : "",
        isToday ? "is-today" : ""
      ]
        .filter(Boolean)
        .join(" ");
      cells.push(`
        <button class="${classes}" type="button" data-date="${J.dateKey(date)}" aria-pressed="${isSelected}" aria-label="${J.formatDate(date, { full: true })} ${J.weekdayCN(date)}${courses.length ? `，${courses.length} 节课` : "，没有课程"}">
          <span class="cal-face">
            <span>${date.getDate()}</span>
            <span class="cal-dots">${dots}</span>
          </span>
        </button>`);
    }
    calGrid.innerHTML = cells.join("");

    const selectedCourses = J.getCoursesForDate(selected);
    const selectedStats = J.getDayStats(selectedCourses);
    selectedTitle.textContent = `${J.formatDate(selected)} · ${J.weekdayCN(selected)}`;
    selectedMeta.textContent = selectedCourses.length
      ? `${selectedCourses.length} 节课 · ${selectedStats.range} · ${weekLine(selected)}`
      : `没有课程 · ${weekLine(selected)}`;

    if (!selectedCourses.length) {
      agendaList.innerHTML = `
        <div class="agenda-empty">
          <span class="empty-orb">${icon("leaf")}</span>
          <h4>留一点空白</h4>
          <p>换一天看看，或把时间留给自己。</p>
          <button class="btn btn-tonal empty-action" type="button" data-add-course>${icon("plus")}添加课程</button>
        </div>`;
    } else {
      const nowMinutes = now.getHours() * 60 + now.getMinutes();
      agendaList.innerHTML = `
        <div class="agenda-list">
          ${selectedCourses
            .map((course) => {
              const isToday = J.sameDate(selected, now);
              const isCurrent =
                isToday &&
                nowMinutes >= J.toMinutes(course.start) &&
                nowMinutes < J.toMinutes(course.end);
              return `
                <button class="agenda-item c-${course.tone}${isCurrent ? " is-current" : ""}" type="button" data-course-id="${course.id}" aria-label="查看${escapeHTML(course.name)}详情">
                  <span class="agenda-time"><b>${course.start}</b><span>${course.end}</span></span>
                  <span class="agenda-accent"></span>
                  <span class="agenda-copy">
                    <h4>${escapeHTML(course.name)}${isCurrent ? '<span class="now-tag">现在</span>' : ""}</h4>
                    <p>${escapeHTML(course.room)} · ${escapeHTML(course.teacher)}</p>
                  </span>
                  ${icon("chevron-right")}
                </button>`;
            })
            .join("")}
        </div>`;
    }

    pageCalendar.scrollTop = pageScrollTop;
  }

  /* ---------- 设置 ---------- */

  function renderSettingsDynamic() {
    const now = new Date();
    const week = J.getWeekNumber(now);
    const term = J.getTerm();
    const parity = J.getWeekParity(now);
    profileWeek.textContent =
      week < 1 ? "假期" : week > term.weeks ? "学期结束" : `第 ${week} 周${parity ? ` · ${parity}` : ""}`;

    const courseCountLabel = $("#courseCountLabel");
    if (courseCountLabel) {
      const all = J.getAllCourses();
      const credits = all.reduce((sum, course) => sum + (Number(course.credits) || 0), 0);
      courseCountLabel.textContent = `${all.length} 门课程 · ${credits} 学分`;
    }
    const periodSummary = $("#periodSummary");
    if (periodSummary) {
      const periods = J.getPeriods();
      periodSummary.textContent = periods.length
        ? `${periods.length} 节 · ${periods[0].start} 开始`
        : "还没有节次";
    }
    const termSummary = $("#termSummary");
    if (termSummary) {
      const start = J.parseDateKey(term.start);
      termSummary.textContent = `${start.getFullYear()} 年 ${start.getMonth() + 1} 月 ${start.getDate()} 日 · ${term.weeks} 周`;
    }

    const adjustSummary = $("#adjustSummary");
    if (adjustSummary) {
      const adjustments = J.getAdjustments();
      adjustSummary.textContent = adjustments.length
        ? `${adjustments.length} 条临时调整 · 可重置`
        : "临时调课、放假与补课";
    }

    paletteName.textContent = `${PALETTES[state.palette]} · Material You`;
    $$("[data-theme-mode]", themeSegmented).forEach((button) => {
      button.setAttribute("aria-pressed", String(button.dataset.themeMode === state.theme));
    });
    $$("[data-pref]").forEach((button) => {
      const key = button.dataset.pref;
      if (key in state.toggles) {
        button.setAttribute("aria-checked", String(Boolean(state.toggles[key])));
      }
    });

    const debugToggle = $("[data-debug-toggle]");
    if (debugToggle) {
      debugToggle.setAttribute("aria-checked", String(state.debug));
    }
    const debugCard = $("#debugCard");
    if (debugCard) {
      debugCard.hidden = !state.debug;
    }
    const debugHint = $("#debugHint");
    if (debugHint) {
      debugHint.textContent = state.debug ? "动画实验室已展开" : "开启后可快速预览动画";
    }
  }

  /* ---------- 选择日期 / 页面 ---------- */

  function selectDate(date, options) {
    const opts = options || {};
    state.selectedDate = J.atMidnight(date);
    state.viewYear = state.selectedDate.getFullYear();
    state.viewMonth = state.selectedDate.getMonth();
    renderToday({ resetScroll: Boolean(opts.resetScroll) });
    renderCalendar();
  }

  function setActivePage(index) {
    if (state.activePage === index) return;
    state.activePage = index;
    $$(".nav-item", $("#bottomNav")).forEach((item, itemIndex) => {
      const active = itemIndex === index;
      item.classList.toggle("is-active", active);
      if (active) {
        item.setAttribute("aria-current", "page");
      } else {
        item.removeAttribute("aria-current");
      }
    });
  }

  function goToPage(index, options) {
    const opts = options || {};
    const target = Math.max(0, Math.min(2, index));
    setActivePage(target);
    navScrollLockUntil = Date.now() + 520;
    pager.scrollTo({
      left: pager.clientWidth * target,
      behavior: opts.instant || prefersReducedMotion ? "auto" : "smooth"
    });
  }

  function goToTodaySchedule() {
    goToPage(0);
    pageToday.scrollTo({ top: 0, behavior: prefersReducedMotion ? "auto" : "smooth" });
  }

  /* ---------- 底部 Sheet ---------- */

  function openSheet(html) {
    lastFocusedElement = document.activeElement;
    sheetBody.innerHTML = html;
    app.classList.add("is-sheet-open");
    bottomSheet.setAttribute("aria-hidden", "false");
    sheetOpen = true;
    window.requestAnimationFrame(() => {
      const closeButton = $(".sheet-close", sheetBody);
      if (closeButton) closeButton.focus({ preventScroll: true });
    });
  }

  function closeSheet() {
    if (!sheetOpen) return;
    sheetOpen = false;
    app.classList.remove("is-sheet-open");
    bottomSheet.setAttribute("aria-hidden", "true");
    if (lastFocusedElement && typeof lastFocusedElement.focus === "function") {
      lastFocusedElement.focus({ preventScroll: true });
    }
    lastFocusedElement = null;
    manageSelect = false;
    manageSelected.clear();
  }

  function openCourseSheet(course, date) {
    if (!course) return;
    const now = new Date();
    const start = J.toMinutes(course.start);
    const end = J.toMinutes(course.end);
    const nowMinutes = now.getHours() * 60 + now.getMinutes();
    const isToday = J.sameDate(date, now);
    const isActive = isToday && nowMinutes >= start && nowMinutes < end;
    const isPast = isToday && nowMinutes >= end;
    const chip = isActive
      ? `<span class="sheet-status-chip">正在进行</span>`
      : isPast
        ? `<span class="sheet-status-chip" style="background: var(--surface-container-high); color: var(--on-surface-variant)">已结束</span>`
        : "";

    const periodLabel = J.getPeriodLabel(course);
    const weekText = `${J.formatWeeks(course)}${periodLabel ? ` · ${periodLabel}` : ""}`;

    openSheet(`
      <div class="c-${course.tone}">
        <div class="sheet-kicker-row">
          <span class="sheet-kicker">${escapeHTML(course.kind)} · ${course.credits} 学分</span>
          <span class="sheet-head-actions">
            <button class="sheet-icon-btn" type="button" data-edit-course="${course.id}" aria-label="编辑课程">${icon("edit")}</button>
            <button class="sheet-close" type="button" data-close-sheet aria-label="关闭">${icon("x")}</button>
          </span>
        </div>
        <h2 class="sheet-title" id="sheetTitle">${escapeHTML(course.name)}</h2>
        <p class="sheet-subline">${J.formatDate(date, { full: true })}<span class="dot"></span>${J.weekdayCN(date)}<span class="dot"></span>${weekLine(date)}</p>

        <div class="sheet-time-row">
          <span class="sheet-time-icon">${icon("clock")}</span>
          <span class="sheet-time-main">
            <b>${course.start} – ${course.end}</b>
            <span>${J.getDayStats([course]).minutes} 分钟 · ${escapeHTML(weekText)}</span>
          </span>
          ${chip}
        </div>

        <div class="sheet-detail-grid">
          <div class="sheet-detail">${icon("pin")}<span><small>上课地点</small><b>${escapeHTML(course.room)}</b></span></div>
          <div class="sheet-detail">${icon("user")}<span><small>授课教师</small><b>${escapeHTML(course.teacher)}</b></span></div>
        </div>

        ${
          course.note
            ? `<div class="sheet-note">
          <span>备注</span>
          <p>${escapeHTML(course.note)}</p>
        </div>`
            : ""
        }

        <div class="sheet-actions">
          <button class="btn btn-primary" type="button" data-sheet-action="reminder">${icon("bell")}加入提醒</button>
          <button class="btn btn-tonal" type="button" data-sheet-action="materials">${icon("book")}课程资料</button>
        </div>
      </div>
    `);
  }

  /* ---------- 课程编辑 ---------- */

  function dayName(day) {
    return day === 0 ? "周日" : `周${J.WEEKDAYS[day]}`;
  }

  function dayChipsHTML(selected) {
    return [1, 2, 3, 4, 5, 6, 0]
      .map(
        (day) =>
          `<button class="chip${day === selected ? " is-active" : ""}" type="button" data-day="${day}" aria-pressed="${day === selected}">${J.WEEKDAYS[day]}</button>`
      )
      .join("");
  }

  function toneChipsHTML(selected) {
    return J.COURSE_TONES.map(
      (tone) =>
        `<button class="tone-chip c-${tone.key}${tone.key === selected ? " is-active" : ""}" type="button" data-tone="${tone.key}" aria-label="${tone.name}" aria-pressed="${tone.key === selected}"><span></span></button>`
    ).join("");
  }

  function weekOptionsHTML(selected, termWeeks) {
    let html = "";
    for (let week = 1; week <= termWeeks; week += 1) {
      html += `<option value="${week}"${week === selected ? " selected" : ""}>第 ${week} 周</option>`;
    }
    return html;
  }

  function periodOptionsHTML(course) {
    const periods = J.getPeriods();
    const groups = [];
    for (let index = 0; index < periods.length; index += 2) {
      const startIndex = index + 1;
      const endIndex = Math.min(index + 2, periods.length);
      groups.push({
        start: startIndex,
        end: endIndex,
        clock: `${periods[index].start}–${periods[endIndex - 1].end}`
      });
    }
    if (course && course.periodStart) {
      const exists = groups.some(
        (group) => group.start === course.periodStart && group.end === course.periodEnd
      );
      if (!exists) {
        const first = periods[course.periodStart - 1];
        const last = periods[course.periodEnd - 1];
        if (first && last) {
          groups.unshift({
            start: course.periodStart,
            end: course.periodEnd,
            clock: `${first.start}–${last.end}`
          });
        }
      }
    }
    const options = [`<option value="">自定义时间</option>`].concat(
      groups.map((group) => {
        const label =
          group.start === group.end
            ? `第 ${group.start} 节`
            : `第 ${group.start}–${group.end} 节`;
        const selected =
          course &&
          course.periodStart === group.start &&
          course.periodEnd === group.end
            ? " selected"
            : "";
        return `<option value="${group.start}-${group.end}"${selected}>${label} · ${group.clock}</option>`;
      })
    );
    return options.join("");
  }

  function openCourseEditor(course, presetDate) {
    const term = J.getTerm();
    const isEdit = Boolean(course);
    const day = course ? course.day : (presetDate ? presetDate.getDay() : 1);
    const start = course ? course.start : "08:00";
    const end = course ? course.end : "09:40";
    const weekStart = course ? course.weekStart : 1;
    const weekEnd = course ? course.weekEnd : term.weeks;
    const parity = course ? course.parity : "all";
    const tone = course ? course.tone : "clay";
    const title = isEdit ? "编辑课程" : "添加课程";
    const kicker = isEdit ? "调整这门课的时间与周次" : `${dayName(day)} · 建立一段新节奏`;

    openSheet(`
      <form class="course-form" id="courseForm" data-course-id="${isEdit ? course.id : ""}" novalidate>
        <div class="sheet-kicker-row">
          <span class="sheet-kicker">${isEdit ? "编辑课程" : "新课程"}</span>
          <button class="sheet-close" type="button" data-close-sheet aria-label="关闭">${icon("x")}</button>
        </div>
        <h2 class="sheet-title" id="sheetTitle">${title}</h2>
        <p class="sheet-subline">${kicker}</p>

        <label class="field">
          <span class="field-label">课程名称</span>
          <input class="text-input" type="text" name="name" value="${isEdit ? escapeHTML(course.name) : ""}" maxlength="40" placeholder="例如：高等数学 A" required />
        </label>

        <div class="field">
          <span class="field-label">星期</span>
          <input type="hidden" name="day" value="${day}" />
          <div class="chip-row" data-chip-group="day">${dayChipsHTML(day)}</div>
        </div>

        <div class="field-row">
          <label class="field">
            <span class="field-label">上课时间</span>
            <input class="text-input" type="time" name="start" value="${start}" required />
          </label>
          <label class="field">
            <span class="field-label">下课时间</span>
            <input class="text-input" type="time" name="end" value="${end}" required />
          </label>
        </div>

        <label class="field">
          <span class="field-label">按节次填写 <small>会覆盖上面的时间</small></span>
          <select class="text-input select-input" name="period" id="coursePeriod">${periodOptionsHTML(course)}</select>
        </label>

        <div class="field-row">
          <label class="field">
            <span class="field-label">上课地点</span>
            <input class="text-input" type="text" name="room" value="${isEdit ? escapeHTML(course.room) : ""}" maxlength="40" placeholder="教学楼 201" />
          </label>
          <label class="field">
            <span class="field-label">授课教师</span>
            <input class="text-input" type="text" name="teacher" value="${isEdit ? escapeHTML(course.teacher) : ""}" maxlength="30" placeholder="教师姓名" />
          </label>
        </div>

        <div class="field-row">
          <label class="field">
            <span class="field-label">课程类型</span>
            <input class="text-input" type="text" name="kind" list="kindOptions" value="${isEdit ? escapeHTML(course.kind) : "专业必修"}" maxlength="20" />
          </label>
          <label class="field">
            <span class="field-label">学分</span>
            <input class="text-input" type="number" name="credits" min="0" max="20" step="0.5" inputmode="decimal" value="${isEdit ? course.credits : 2}" />
          </label>
        </div>
        <datalist id="kindOptions">
          <option value="公共基础"></option>
          <option value="学科基础"></option>
          <option value="专业必修"></option>
          <option value="专业选修"></option>
          <option value="实践课程"></option>
        </datalist>

        <div class="field">
          <span class="field-label">上课周次</span>
          <div class="week-row">
            <select class="text-input select-input" name="weekStart" aria-label="起始周">${weekOptionsHTML(weekStart, term.weeks)}</select>
            <span class="week-dash">–</span>
            <select class="text-input select-input" name="weekEnd" aria-label="结束周">${weekOptionsHTML(weekEnd, term.weeks)}</select>
          </div>
          <input type="hidden" name="parity" value="${parity}" />
          <div class="segmented segmented--small" data-chip-group="parity" role="group" aria-label="单双周">
            <button type="button" data-parity="all" aria-pressed="${parity === "all"}" class="${parity === "all" ? "is-active" : ""}">每周</button>
            <button type="button" data-parity="odd" aria-pressed="${parity === "odd"}" class="${parity === "odd" ? "is-active" : ""}">单周</button>
            <button type="button" data-parity="even" aria-pressed="${parity === "even"}" class="${parity === "even" ? "is-active" : ""}">双周</button>
          </div>
        </div>

        <div class="field">
          <span class="field-label">课程颜色</span>
          <input type="hidden" name="tone" value="${tone}" />
          <div class="tone-row" data-chip-group="tone">${toneChipsHTML(tone)}</div>
        </div>

        <label class="field">
          <span class="field-label">备注 <small>可选</small></span>
          <textarea class="text-input text-area" name="note" rows="3" maxlength="400" placeholder="本周要带的东西、作业提醒……">${isEdit ? escapeHTML(course.note) : ""}</textarea>
        </label>

        <p class="form-error" id="courseFormError" hidden></p>

        <div class="form-actions">
          ${isEdit ? `<button class="text-btn danger" type="button" data-delete-course="${course.id}">删除</button>` : "<span></span>"}
          <button class="btn btn-primary" type="submit">${isEdit ? "保存修改" : "添加课程"}</button>
        </div>
      </form>
    `);
  }

  function handleCourseSubmit(form) {
    const errorEl = $("#courseFormError");
    const fail = (message) => {
      if (errorEl) {
        errorEl.textContent = message;
        errorEl.hidden = false;
        errorEl.scrollIntoView({ block: "center", behavior: "smooth" });
      }
    };
    if (errorEl) errorEl.hidden = true;

    const data = new FormData(form);
    const name = String(data.get("name") || "").trim();
    const start = String(data.get("start") || "");
    const end = String(data.get("end") || "");
    const weekStart = Number(data.get("weekStart"));
    const weekEnd = Number(data.get("weekEnd"));
    const period = String(data.get("period") || "");

    if (!name) {
      fail("先给课程起个名字吧。");
      return;
    }
    if (!J.isValidTime(start) || !J.isValidTime(end)) {
      fail("时间格式看起来不对。");
      return;
    }
    if (J.toMinutes(end) <= J.toMinutes(start)) {
      fail("下课时间需要晚于上课时间。");
      return;
    }
    if (weekStart > weekEnd) {
      fail("起始周不能晚于结束周。");
      return;
    }

    let periodStart = null;
    let periodEnd = null;
    if (period) {
      const parts = period.split("-").map(Number);
      const periods = J.getPeriods();
      const first = periods[parts[0] - 1];
      const last = periods[parts[1] - 1];
      // 只有时间与节次表完全一致时才保留节次绑定，避免手动改时间后仍显示旧节次
      if (first && last && first.start === start && last.end === end) {
        periodStart = parts[0];
        periodEnd = parts[1];
      }
    }

    const id = form.dataset.courseId;
    const payload = {
      id: id || undefined,
      name,
      day: Number(data.get("day")),
      start,
      end,
      room: String(data.get("room") || "").trim(),
      teacher: String(data.get("teacher") || "").trim(),
      kind: String(data.get("kind") || "专业必修").trim() || "专业必修",
      credits: Number(data.get("credits")) || 0,
      note: String(data.get("note") || "").trim(),
      weekStart,
      weekEnd,
      parity: String(data.get("parity") || "all"),
      tone: String(data.get("tone") || "clay"),
      periodStart,
      periodEnd
    };

    const conflicts = J.findConflicts(payload);
    if (conflicts.length && form.dataset.conflictOk !== "1") {
      form.dataset.conflictOk = "1";
      fail(`与「${conflicts[0].name}」时间重叠，再点一次保存仍要${id ? "修改" : "添加"}。`);
      return;
    }

    if (id) {
      J.updateCourse(id, payload);
      showSnackbar("课程已更新");
    } else {
      J.addCourse(payload);
      showSnackbar("课程已添加");
    }
    closeSheet();
  }

  /* ---------- 课程管理 ---------- */

  let manageSelect = false;
  const manageSelected = new Set();

  function manageItemHTML(course, selectMode, picked) {
    const meta = `${escapeHTML(J.formatWeeks(course))}${
      course.room ? ` · ${escapeHTML(course.room)}` : ""
    }`;
    const body = `
      <span class="manage-time"><b>${course.start}</b><span>${course.end}</span></span>
      <span class="manage-copy">
        <b>${escapeHTML(course.name)}</b>
        <small>${meta}</small>
      </span>`;
    if (selectMode) {
      return `
        <button class="manage-item c-${course.tone}${picked ? " is-picked" : ""}" type="button" data-manage-check="${course.id}" aria-pressed="${picked}">
          <span class="pick-box" aria-hidden="true">${picked ? icon("check") : ""}</span>
          ${body}
        </button>`;
    }
    return `
      <button class="manage-item c-${course.tone}" type="button" data-edit-course="${course.id}" aria-label="编辑${escapeHTML(course.name)}">
        ${body}
        ${icon("edit", "manage-icon")}
      </button>`;
  }

  function openCourseManager() {
    const courses = J.getAllCourses();
    const term = J.getTerm();
    if (!manageSelect) manageSelected.clear();
    const groups = [1, 2, 3, 4, 5, 6, 0]
      .map((day) => {
        const list = courses.filter((course) => course.day === day);
        if (!list.length) return "";
        return `
          <div class="manage-day">
            <h3>${dayName(day)}<span>${list.length} 门</span></h3>
            ${list
              .map((course) =>
                manageItemHTML(course, manageSelect, manageSelected.has(course.id))
              )
              .join("")}
          </div>`;
      })
      .join("");

    const footer = manageSelect
      ? `<div class="form-actions form-actions--sticky">
          <button class="text-btn" type="button" data-manage-cancel>退出</button>
          <button class="btn btn-tonal" type="button" data-manage-all>${
            courses.length && manageSelected.size === courses.length
              ? "取消全选"
              : "全选"
          }</button>
          <button class="btn btn-tonal manage-delete" type="button" data-manage-delete ${
            manageSelected.size ? "" : "disabled"
          }>${manageSelected.size ? `删除 (${manageSelected.size})` : "删除"}</button>
        </div>`
      : `<div class="form-actions form-actions--sticky">
          <button class="btn btn-tonal" type="button" data-manage-select>${icon(
            "check"
          )}批量管理</button>
          <button class="btn btn-primary" type="button" data-add-course>${icon(
            "plus"
          )}添加课程</button>
        </div>`;

    openSheet(`
      <div class="sheet-kicker-row">
        <span class="sheet-kicker">我的课表</span>
        <button class="sheet-close" type="button" data-close-sheet aria-label="关闭">${icon("x")}</button>
      </div>
      <h2 class="sheet-title" id="sheetTitle">${
        manageSelect ? "选择要删除的课程" : "课程管理"
      }</h2>
      <p class="sheet-subline">${
        manageSelect
          ? `已选 ${manageSelected.size} 门 · 点按切换`
          : `${courses.length} 门课程 · ${courses.reduce(
              (sum, course) => sum + (Number(course.credits) || 0),
              0
            )} 学分 · ${term.weeks} 周学期`
      }</p>
      ${
        groups
          ? `<div class="manage-list">${groups}</div>`
          : `<div class="agenda-empty"><span class="empty-orb">${icon("book")}</span><h4>课表还是空的</h4><p>添加第一门课程，节律就开始运转了。</p></div>`
      }
      ${footer}
    `);
  }

  function syncManageSelection() {
    const total = J.getAllCourses().length;
    $$("[data-manage-check]", sheetBody).forEach((button) => {
      const on = manageSelected.has(button.dataset.manageCheck);
      button.classList.toggle("is-picked", on);
      button.setAttribute("aria-pressed", String(on));
      const box = $(".pick-box", button);
      if (box) box.innerHTML = on ? icon("check") : "";
    });
    const del = $("[data-manage-delete]", sheetBody);
    if (del) {
      del.disabled = manageSelected.size === 0;
      del.textContent = manageSelected.size ? `删除 (${manageSelected.size})` : "删除";
    }
    const all = $("[data-manage-all]", sheetBody);
    if (all) {
      all.textContent =
        total && manageSelected.size === total ? "取消全选" : "全选";
    }
    const subtitle = $(".sheet-subline", sheetBody);
    if (subtitle) subtitle.textContent = `已选 ${manageSelected.size} 门 · 点按切换`;
  }

  /* ---------- 调休与调课 ---------- */

  function openAdjustManager() {
    const rows = J.getAdjustments()
      .map((adj) => {
        const from = J.parseDateKey(adj.from);
        const to = adj.to ? J.parseDateKey(adj.to) : null;
        const title =
          adj.kind === "off"
            ? `${J.formatDate(from)} 周${J.weekdayCN(from, true)} · 放假`
            : `${J.formatDate(from)} 周${J.weekdayCN(from, true)} → ${J.formatDate(
                to
              )} 周${J.weekdayCN(to, true)}`;
        const sub =
          adj.note ||
          (adj.kind === "off" ? "当天没有课程" : "当天按源日期的课表上课");
        return `
          <div class="adjust-item">
            <span class="adjust-item-copy"><b>${title}</b><small>${escapeHTML(
          sub
        )}</small></span>
            <button class="icon-btn icon-btn--danger" type="button" data-adjust-delete="${
              adj.id
            }" aria-label="删除这条调整">${icon("trash")}</button>
          </div>`;
      })
      .join("");

    openSheet(`
      <div class="sheet-kicker-row">
        <span class="sheet-kicker">临时安排</span>
        <button class="sheet-close" type="button" data-close-sheet aria-label="关闭">${icon("x")}</button>
      </div>
      <h2 class="sheet-title" id="sheetTitle">调休与调课</h2>
      <p class="sheet-subline">只影响选中的日期 · 随时可以重置</p>
      ${
        rows
          ? `<div class="adjust-list">${rows}</div>`
          : `<div class="agenda-empty"><span class="empty-orb">${icon(
              "calendar"
            )}</span><h4>还没有临时调整</h4><p>把某天的课挪到另一天，或标记一天放假。</p></div>`
      }
      <div class="data-actions">
        <button class="btn btn-tonal" type="button" data-adjust-new="swap">${icon(
          "calendar"
        )}新建调课</button>
        <button class="btn btn-tonal" type="button" data-adjust-new="off">${icon(
          "moon"
        )}标记放假</button>
      </div>
    `);
  }

  function openAdjustEditor(kind) {
    const isSwap = kind !== "off";
    const today = J.atMidnight(new Date());
    const fromKey = J.dateKey(today);
    let toKey = J.dateKey(J.addDays(J.startOfWeek(today), 5));
    if (toKey === fromKey) toKey = J.dateKey(J.addDays(today, 7));
    openSheet(`
      <div class="sheet-kicker-row">
        <span class="sheet-kicker">${isSwap ? "临时调课" : "标记放假"}</span>
        <button class="sheet-close" type="button" data-close-sheet aria-label="关闭">${icon("x")}</button>
      </div>
      <h2 class="sheet-title" id="sheetTitle">${
        isSwap ? "把课挪到另一天" : "让这一天休息"
      }</h2>
      <p class="sheet-subline">${
        isSwap ? "只对这两个日期生效，下周恢复原样" : "只影响选中的这一天"
      }</p>
      <form id="adjustForm" data-kind="${isSwap ? "swap" : "off"}">
        <label class="field">
          <span class="field-label">${isSwap ? "原来的上课日" : "放假日"}</span>
          <input class="text-input" type="date" name="from" value="${fromKey}" required />
        </label>
        ${
          isSwap
            ? `<label class="field">
                <span class="field-label">调到哪天</span>
                <input class="text-input" type="date" name="to" value="${toKey}" required />
              </label>`
            : ""
        }
        <label class="field">
          <span class="field-label">备注（可选）</span>
          <input class="text-input" type="text" name="note" maxlength="60" placeholder="${
            isSwap ? "例如：周一课调到周六" : "例如：国庆放假"
          }" />
        </label>
        <p class="form-hint">${
          isSwap
            ? "目标日期原有的课程会被临时替换。"
            : "这一天的课程会暂时隐藏，删除这条记录即可恢复。"
        }</p>
        <p class="form-error" id="adjustError" hidden></p>
        <div class="form-actions">
          <button class="btn btn-primary" type="submit">保存</button>
        </div>
      </form>
    `);
  }

  function handleAdjustSubmit(form) {
    const kind = form.dataset.kind === "off" ? "off" : "swap";
    const from = form.querySelector('input[name="from"]').value;
    const toInput = form.querySelector('input[name="to"]');
    const note = form.querySelector('input[name="note"]').value.trim();
    const errorEl = $("#adjustError");
    try {
      J.addAdjustment({ kind, from, to: toInput ? toInput.value : "", note });
      openAdjustManager();
      showSnackbar(kind === "swap" ? "调课已生效" : "已标记放假");
    } catch (error) {
      if (errorEl) {
        errorEl.textContent = error.message || "保存失败";
        errorEl.hidden = false;
      }
    }
  }

  /* ---------- 作息时间 ---------- */

  function periodRowHTML(period, index) {
    return `
      <div class="period-row" data-period-row>
        <span class="period-index">${index + 1}</span>
        <input class="text-input time-input" type="time" data-period-start value="${period.start}" aria-label="第 ${index + 1} 节开始" />
        <span class="period-dash">–</span>
        <input class="text-input time-input" type="time" data-period-end value="${period.end}" aria-label="第 ${index + 1} 节结束" />
        <button class="icon-btn icon-btn--danger" type="button" data-remove-period aria-label="删除第 ${index + 1} 节">${icon("trash")}</button>
      </div>`;
  }

  function renumberPeriods() {
    $$("[data-period-row]", sheetBody).forEach((row, index) => {
      const label = $(".period-index", row);
      if (label) label.textContent = String(index + 1);
    });
  }

  function openPeriodEditor() {
    const periods = J.getPeriods();
    openSheet(`
      <div class="sheet-kicker-row">
        <span class="sheet-kicker">作息时间</span>
        <button class="sheet-close" type="button" data-close-sheet aria-label="关闭">${icon("x")}</button>
      </div>
      <h2 class="sheet-title" id="sheetTitle">每天的时间骨架</h2>
      <p class="sheet-subline">调整上下课时间，绑定节次的课程会一起更新</p>

      <div class="period-list" id="periodList">
        ${periods.map((period, index) => periodRowHTML(period, index)).join("")}
      </div>
      <button class="btn btn-tonal period-add" type="button" data-add-period>${icon("plus")}添加一节</button>

      <p class="form-error" id="periodError" hidden></p>
      <div class="form-actions">
        <button class="btn btn-tonal" type="button" data-close-sheet>取消</button>
        <button class="btn btn-primary" type="button" data-save-periods>保存作息</button>
      </div>
    `);
  }

  function collectPeriods() {
    return $$("[data-period-row]", sheetBody).map((row) => ({
      start: $("[data-period-start]", row).value,
      end: $("[data-period-end]", row).value
    }));
  }

  function savePeriodsFromSheet() {
    const errorEl = $("#periodError");
    const periods = collectPeriods();
    if (errorEl) errorEl.hidden = true;
    if (!periods.length) {
      if (errorEl) {
        errorEl.textContent = "至少保留一节课。";
        errorEl.hidden = false;
      }
      return;
    }
    for (let index = 0; index < periods.length; index += 1) {
      if (!J.isValidTime(periods[index].start) || !J.isValidTime(periods[index].end)) {
        if (errorEl) {
          errorEl.textContent = `第 ${index + 1} 节的时间不完整。`;
          errorEl.hidden = false;
        }
        return;
      }
      if (J.toMinutes(periods[index].end) <= J.toMinutes(periods[index].start)) {
        if (errorEl) {
          errorEl.textContent = `第 ${index + 1} 节的下课时间需要晚于上课时间。`;
          errorEl.hidden = false;
        }
        return;
      }
    }
    const bound = J.getAllCourses().filter((course) => course.periodStart).length;
    J.savePeriods(periods);
    closeSheet();
    showSnackbar(bound ? `作息已更新 · ${bound} 门课程同步` : "作息已更新");
  }

  /* ---------- 学期设置 ---------- */

  function openTermEditor() {
    const term = J.getTerm();
    const now = new Date();
    const week = J.getWeekNumber(now);
    const parity = J.getWeekParity(now);
    const status =
      week < 1
        ? "学期还没开始"
        : week > term.weeks
          ? "学期已经结束"
          : `今天是第 ${week} 周${parity ? `（${parity}）` : ""}`;
    openSheet(`
      <form id="termForm">
        <div class="sheet-kicker-row">
          <span class="sheet-kicker">学期设置</span>
          <button class="sheet-close" type="button" data-close-sheet aria-label="关闭">${icon("x")}</button>
        </div>
        <h2 class="sheet-title" id="sheetTitle">第几周，由你定义</h2>
        <p class="sheet-subline">${status}</p>

        <label class="field">
          <span class="field-label">开学日期 <small>第一周的周一</small></span>
          <input class="text-input" type="date" name="start" value="${term.start}" required />
        </label>
        <label class="field">
          <span class="field-label">学期总周数</span>
          <input class="text-input" type="number" name="weeks" min="1" max="40" inputmode="numeric" value="${term.weeks}" required />
        </label>

        <p class="form-hint">调整后，单双周与教学周会重新计算，课程不会丢失。</p>
        <p class="form-error" id="termError" hidden></p>

        <div class="form-actions">
          <button class="btn btn-tonal" type="button" data-close-sheet>取消</button>
          <button class="btn btn-primary" type="submit">保存设置</button>
        </div>
      </form>
    `);
  }

  function handleTermSubmit(form) {
    const data = new FormData(form);
    const start = String(data.get("start") || "");
    const weeks = Number(data.get("weeks"));
    const errorEl = $("#termError");
    if (errorEl) errorEl.hidden = true;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(start)) {
      if (errorEl) {
        errorEl.textContent = "请选择有效的开学日期。";
        errorEl.hidden = false;
      }
      return;
    }
    if (!weeks || weeks < 1 || weeks > 40) {
      if (errorEl) {
        errorEl.textContent = "学期周数需要在 1–40 之间。";
        errorEl.hidden = false;
      }
      return;
    }
    J.saveTerm({ start, weeks });
    closeSheet();
    showSnackbar("学期设置已保存");
  }

  /* ---------- 备份与恢复 ---------- */

  function openDataTools() {
    let exported = "";
    try {
      exported = J.exportData();
    } catch (error) {}
    openSheet(`
      <div class="sheet-kicker-row">
        <span class="sheet-kicker">备份与恢复</span>
        <button class="sheet-close" type="button" data-close-sheet aria-label="关闭">${icon("x")}</button>
      </div>
      <h2 class="sheet-title" id="sheetTitle">把课表握在手里</h2>
      <p class="sheet-subline">数据只在本机，复制即备份</p>

      <p class="form-hint">下面是一份完整的课表 JSON。复制保存，换设备时粘贴回来即可恢复。</p>
      <textarea class="text-input data-area" id="dataArea" spellcheck="false" aria-label="课表数据">${escapeHTML(exported)}</textarea>

      <div class="data-actions">
        <button class="btn btn-tonal" type="button" data-copy-data>全选并复制</button>
        <button class="btn btn-tonal" type="button" data-import-data>从文本导入</button>
      </div>

      <p class="form-error" id="dataError" hidden></p>

      <div class="danger-zone">
        <button class="text-btn danger" type="button" data-reset-data>恢复示例课表</button>
        <button class="text-btn danger" type="button" data-clear-courses>清空所有课程</button>
      </div>
    `);
  }

  function copyDataText() {
    const area = $("#dataArea");
    if (!area) return;
    area.focus();
    area.select();
    area.setSelectionRange(0, area.value.length);
    let copied = false;
    try {
      copied = document.execCommand("copy");
    } catch (error) {}
    showSnackbar(copied ? "已复制，粘贴到备忘录即可备份" : "已全选，请长按手动复制");
  }

  function importDataFromSheet() {
    const area = $("#dataArea");
    const errorEl = $("#dataError");
    if (!area) return;
    if (errorEl) errorEl.hidden = true;
    try {
      const result = J.importData(area.value.trim());
      closeSheet();
      showSnackbar(`已导入 ${result.courses} 门课程`);
    } catch (error) {
      if (errorEl) {
        errorEl.textContent = `导入失败：${
          error.message || "请确认粘贴的是完整的课表 JSON。"
        }`;
        errorEl.hidden = false;
      }
    }
  }

  /* ---------- 数据刷新 ---------- */

  function syncWatchData() {
    $$("iframe").forEach((frame) => {
      try {
        frame.contentWindow.postMessage({ type: "jielu:data" }, "*");
      } catch (error) {}
    });
  }

  function refreshAll() {
    renderToday({ resetScroll: false });
    renderCalendar();
    renderSettingsDynamic();
    syncWatchData();
  }

  function openDesignNotes() {
    openSheet(`
      <div>
        <div class="notes-kicker">
          <p>设计手记 · ABOUT THE MAKER</p>
          <button class="sheet-close" type="button" data-close-sheet aria-label="关闭">${icon("x")}</button>
        </div>
        <h2 class="notes-title" id="sheetTitle">少一点打扰，<br /><span>多一点节奏。</span></h2>
        <p class="notes-intro">节律不是一张功能清单，而是一次关于「刚刚好」的练习：在最小的屏幕上，只留下此刻真正需要的信息；在最小的手腕上，只点亮必要的像素。</p>

        <div class="note-principles">
          <div class="note-principle">
            <span class="note-index">01</span>
            <div><b>进入，即课表</b><p>没有启动页，也没有需要等待的加载动画。打开应用，第一眼就是今天的时间、课程与教室；上下滑动，就能读完一整天。</p></div>
          </div>
          <div class="note-principle">
            <span class="note-index">02</span>
            <div><b>手指的方向，就是时间的方向</b><p>上下是今天，左右是场景。课表、日历、设置共用一条横向的轴，底部导航确认你所在的位置，滑动与点击都通向同一个答案。</p></div>
          </div>
          <div class="note-principle">
            <span class="note-index">03</span>
            <div><b>Material You，但不喧哗</b><p>用取色回应你的偏好，用大圆角与留白降低密度。课程颜色是信息，不是装饰；所有的动效都只在需要确认动作时出现。</p></div>
          </div>
        </div>

        <div class="power-note">
          <div class="power-head">${icon("battery")}POWER, QUIETLY</div>
          <p>手表端以纯黑为主，让 OLED 像素真正熄灭；没有秒针、没有循环动画，只在分钟变化时醒来。手机端同样对齐到分钟更新，切到后台即暂停计时。省电不是隐藏功能，而是界面的一部分。</p>
        </div>

        <div class="notes-sign"><span>shyoka × WorkBuddy</span><span>2026 · 秋</span></div>
      </div>
    `);
  }

  function showSnackbar(message) {
    if (!message) return;
    window.clearTimeout(snackbarTimer);
    snackbar.textContent = message;
    snackbar.classList.add("is-visible");
    snackbarTimer = window.setTimeout(() => {
      snackbar.classList.remove("is-visible");
    }, 2200);
  }

  /* ---------- 调试模式 ---------- */

  function toggleDebug() {
    state.debug = !state.debug;
    savePrefs();
    renderSettingsDynamic();
    showSnackbar(state.debug ? "调试模式已开启 · 动画实验室在下面" : "调试模式已关闭");
  }

  function runDebugAction(action) {
    if (action === "sheet") {
      openDebugSheet();
      return;
    }
    if (action === "loading") {
      playLoadingDemo();
      return;
    }
    if (action === "pages") {
      playPageDemo();
      return;
    }
    if (action === "ring") {
      playRingDemo();
      return;
    }
    if (action === "snackbar") {
      showSnackbar("一条会自己消失的提示");
    }
  }

  function openDebugSheet() {
    openSheet(`
      <div class="sheet-kicker-row">
        <span class="sheet-kicker">调试 · 弹窗</span>
        <button class="sheet-close" type="button" data-close-sheet aria-label="关闭">${icon("x")}</button>
      </div>
      <h2 class="sheet-title" id="sheetTitle">弹窗动画</h2>
      <p class="sheet-subline">从底部滑入 · 遮罩同步淡入</p>

      <p class="form-hint">这张弹窗只用来检查动画：滑入的弹簧、遮罩的透明度、收起时的回落。点右上角、遮罩或下面的按钮都能关上；再点一次会重新入场。</p>

      <div class="sheet-actions">
        <button class="btn btn-tonal" type="button" data-close-sheet>收起弹窗</button>
        <button class="btn btn-primary" type="button" data-reopen-sheet>再来一次</button>
      </div>
    `);
  }

  function playLoadingDemo() {
    if (loadingDemoEl) return;
    const el = document.createElement("div");
    el.className = "demo-loading";
    el.setAttribute("role", "status");
    el.innerHTML = `
      <div class="demo-loading-card">
        <span class="demo-spinner" aria-hidden="true"></span>
        <p>正在加载…</p>
      </div>`;
    app.appendChild(el);
    loadingDemoEl = el;
    window.requestAnimationFrame(() => el.classList.add("is-visible"));
    window.setTimeout(() => {
      el.classList.remove("is-visible");
      window.setTimeout(() => {
        el.remove();
        loadingDemoEl = null;
      }, 320);
    }, 2200);
  }

  function playPageDemo() {
    window.clearInterval(pageDemoTimer);
    const start = state.activePage;
    // 从当前页依次走到下一页，最后回到出发的地方
    const order = [(start + 1) % 3, (start + 2) % 3, start];
    let index = 0;
    goToPage(order[index]);
    pageDemoTimer = window.setInterval(() => {
      index += 1;
      if (index >= order.length) {
        window.clearInterval(pageDemoTimer);
        pageDemoTimer = null;
        return;
      }
      goToPage(order[index]);
    }, 1150);
  }

  function playRingDemo() {
    goToPage(0);
    window.setTimeout(() => animateHeroRing(true), 620);
  }

  /**
   * 让 hero 上的进度环从零转起来。
   * 新元素默认就是终值，先拿 dasharray（整圈）当起点，强制一帧后再过渡回去。
   */
  function animateHeroRing(force) {
    if (prefersReducedMotion) return;
    const ring = $(".ring-value", heroSlot);
    if (!ring) return;
    const target = ring.style.strokeDashoffset;
    const full = ring.style.strokeDasharray;
    if (!target || !full) return;
    if (!force && ring.dataset.played === "1") return;
    ring.dataset.played = "1";
    ring.style.transition = "none";
    ring.style.strokeDashoffset = full;
    void ring.getBoundingClientRect();
    ring.style.transition = "stroke-dashoffset 0.95s cubic-bezier(0.05, 0.7, 0.1, 1)";
    window.requestAnimationFrame(() => {
      ring.style.strokeDashoffset = target;
    });
  }

  /* ---------- 事件绑定 ---------- */

  function bindEvents() {
    $(".brand").addEventListener("click", (event) => {
      event.preventDefault();
      goToTodaySchedule();
    });

    $$("[data-watch-download]").forEach((button) => {
      button.addEventListener("click", () => openExternal(REPO.wearRelease));
    });

    $("#bottomNav").addEventListener("click", (event) => {
      const button = event.target.closest("[data-page-target]");
      if (!button) return;
      goToPage(Number(button.dataset.pageTarget));
    });

    pager.addEventListener(
      "scroll",
      () => {
        if (scrollFrame) return;
        scrollFrame = window.requestAnimationFrame(() => {
          scrollFrame = null;
          if (Date.now() < navScrollLockUntil) return;
          const index = Math.round(pager.scrollLeft / pager.clientWidth);
          setActivePage(Math.max(0, Math.min(2, index)));
        });
      },
      { passive: true }
    );

    window.addEventListener("resize", () => {
      updateStageScale();
      pager.scrollTo({ left: pager.clientWidth * state.activePage, behavior: "auto" });
    });

    pageToday.addEventListener("click", (event) => {
      if (event.target.closest("[data-add-course]")) {
        openCourseEditor(null, state.selectedDate);
        return;
      }

      const backToday = event.target.closest("#backToday");
      if (backToday) {
        selectDate(new Date(), { resetScroll: false });
        showSnackbar("已回到今天");
        return;
      }

      const dateButton = event.target.closest("[data-date]");
      if (dateButton) {
        const date = J.parseDateKey(dateButton.dataset.date);
        selectDate(date, { resetScroll: true });
        return;
      }

      const courseButton = event.target.closest("[data-course-id]");
      if (courseButton) {
        const course = J.getCoursesForDate(state.selectedDate).find(
          (item) => item.id === courseButton.dataset.courseId
        );
        openCourseSheet(course, state.selectedDate);
      }
    });

    pageToday.addEventListener("keydown", (event) => {
      if (event.key !== "Enter" && event.key !== " ") return;
      const hero = event.target.closest(".hero[data-course-id]");
      if (!hero) return;
      event.preventDefault();
      const course = J.getCoursesForDate(state.selectedDate).find(
        (item) => item.id === hero.dataset.courseId
      );
      openCourseSheet(course, state.selectedDate);
    });

    pageCalendar.addEventListener("click", (event) => {
      if (event.target.closest("[data-add-course]")) {
        openCourseEditor(null, state.selectedDate);
        return;
      }

      const dateButton = event.target.closest(".cal-cell[data-date]");
      if (dateButton) {
        const date = J.parseDateKey(dateButton.dataset.date);
        selectDate(date);
        return;
      }

      const courseButton = event.target.closest("[data-course-id]");
      if (courseButton) {
        const course = J.getCoursesForDate(state.selectedDate).find(
          (item) => item.id === courseButton.dataset.courseId
        );
        openCourseSheet(course, state.selectedDate);
        return;
      }

      if (event.target.closest("#openDayBtn")) {
        goToTodaySchedule();
      }
    });

    $("#calPrev").addEventListener("click", () => changeMonth(-1));
    $("#calNext").addEventListener("click", () => changeMonth(1));
    $("#calTodayBtn").addEventListener("click", () => {
      const today = new Date();
      state.selectedDate = J.atMidnight(today);
      state.viewYear = today.getFullYear();
      state.viewMonth = today.getMonth();
      renderToday({ resetScroll: false });
      renderCalendar();
      showSnackbar("已回到今天");
    });

    pageSettings.addEventListener("click", (event) => {
      if (event.target.closest("[data-debug-toggle]")) {
        toggleDebug();
        return;
      }
      const debugAction = event.target.closest("[data-debug-action]");
      if (debugAction) {
        runDebugAction(debugAction.dataset.debugAction);
        return;
      }
      if (event.target.closest("[data-manage-courses]")) {
        openCourseManager();
        return;
      }
      if (event.target.closest("[data-edit-periods]")) {
        openPeriodEditor();
        return;
      }
      if (event.target.closest("[data-edit-term]")) {
        openTermEditor();
        return;
      }
      if (event.target.closest("[data-adjust-tools]")) {
        openAdjustManager();
        return;
      }
      if (event.target.closest("[data-data-tools]")) {
        openDataTools();
      }
    });

    $("#themeSegmented").addEventListener("click", (event) => {
      const button = event.target.closest("[data-theme-mode]");
      if (!button) return;
      state.theme = button.dataset.themeMode;
      applyAppearance();
      savePrefs();
      const labels = { auto: "已跟随系统", light: "已切换为浅色", dark: "已切换为深色" };
      showSnackbar(labels[state.theme]);
    });

    paletteSwatches.addEventListener("click", (event) => {
      const button = event.target.closest("[data-palette]");
      if (!button) return;
      setPalette(button.dataset.palette, true);
    });

    $$("[data-intro-palette]").forEach((button) => {
      button.addEventListener("click", () => setPalette(button.dataset.introPalette, true));
    });

    $$("[data-pref]").forEach((button) => {
      button.addEventListener("click", () => {
        const key = button.dataset.pref;
        if (!(key in state.toggles)) return;
        state.toggles[key] = !state.toggles[key];
        button.setAttribute("aria-checked", String(state.toggles[key]));
        savePrefs();
        const messages = TOGGLE_MESSAGES[key];
        if (messages) showSnackbar(messages[state.toggles[key] ? 0 : 1]);
      });
    });

    $$("[data-open-notes]").forEach((button) => {
      button.addEventListener("click", openDesignNotes);
    });

    $$("[data-open-source]").forEach((button) => {
      button.addEventListener("click", openSourceSheet);
    });

    $$("[data-open-profile]").forEach((link) => {
      link.addEventListener("click", (event) => {
        event.preventDefault();
        openExternal(REPO.profile);
      });
    });

    $$("[data-send-watch]").forEach((button) => {
      button.addEventListener("click", () => {
        const bridge = window.JieluNative;
        if (!bridge || typeof bridge.sendToWatch !== "function") {
          showSnackbar("网页版不能直连手表，请用安装版");
          return;
        }
        try {
          const payload = JSON.stringify({
            term: J.getTerm(),
            periods: J.getPeriods(),
            courses: J.getAllCourses()
          });
          showSnackbar("正在发给手表…");
          bridge.sendToWatch(payload);
        } catch (error) {
          showSnackbar("发送失败，请重试");
        }
      });
    });

    window.JieluWatchResult = (status) => {
      const messages = {
        ok: "课表已发送到手表",
        "no-node": "没找到手表，先在表上打开节律",
        "no-gms": "这台设备缺少 Google Play 服务",
        fail: "发送失败，检查蓝牙后再试"
      };
      showSnackbar(messages[status] || "发送失败，请重试");
    };

    $$("[data-info]").forEach((button) => {
      button.addEventListener("click", () => {
        if (button.dataset.info === "license") {
          openSourceSheet();
          return;
        }
        const messages = {
          privacy: "课程与偏好仅保存在本机，不会上传。",
          feedback: "谢谢你的反馈，这一版还在继续打磨。"
        };
        showSnackbar(messages[button.dataset.info]);
      });
    });

    $$("[data-close-sheet]").forEach((button) => {
      button.addEventListener("click", closeSheet);
    });

    sheetScrim.addEventListener("click", closeSheet);

    sheetBody.addEventListener("click", (event) => {
      if (event.target.closest("[data-close-sheet]")) {
        closeSheet();
        return;
      }

      if (event.target.closest("[data-reopen-sheet]")) {
        closeSheet();
        window.setTimeout(openDebugSheet, 420);
        return;
      }

      if (event.target.closest("[data-add-course]")) {
        openCourseEditor(null, state.selectedDate);
        return;
      }

      if (event.target.closest("[data-open-repo]")) {
        openExternal(REPO.url);
        return;
      }

      if (event.target.closest("[data-open-issues]")) {
        openExternal(REPO.issues);
        return;
      }

      if (event.target.closest("[data-copy-repo]")) {
        copyText(REPO.url).then((ok) => {
          showSnackbar(ok ? "仓库地址已复制" : REPO.url);
        });
        return;
      }

      const editButton = event.target.closest("[data-edit-course]");
      if (editButton) {
        const course = J.getCourseById(editButton.dataset.editCourse);
        if (course) openCourseEditor(course);
        return;
      }

      const deleteButton = event.target.closest("[data-delete-course]");
      if (deleteButton) {
        const course = J.getCourseById(deleteButton.dataset.deleteCourse);
        if (course && window.confirm(`确定删除「${course.name}」吗？`)) {
          J.deleteCourse(course.id);
          closeSheet();
          showSnackbar("课程已删除");
        }
        return;
      }

      if (event.target.closest("[data-manage-select]")) {
        manageSelect = true;
        manageSelected.clear();
        openCourseManager();
        return;
      }

      if (event.target.closest("[data-manage-cancel]")) {
        manageSelect = false;
        manageSelected.clear();
        openCourseManager();
        return;
      }

      const manageCheck = event.target.closest("[data-manage-check]");
      if (manageCheck) {
        const id = manageCheck.dataset.manageCheck;
        if (manageSelected.has(id)) manageSelected.delete(id);
        else manageSelected.add(id);
        syncManageSelection();
        return;
      }

      if (event.target.closest("[data-manage-all]")) {
        const all = J.getAllCourses();
        if (manageSelected.size === all.length) manageSelected.clear();
        else all.forEach((course) => manageSelected.add(course.id));
        syncManageSelection();
        return;
      }

      if (event.target.closest("[data-manage-delete]")) {
        const ids = Array.from(manageSelected);
        if (!ids.length) return;
        if (window.confirm(`确定删除选中的 ${ids.length} 门课程吗？`)) {
          const removed = J.deleteCourses(ids);
          manageSelected.clear();
          manageSelect = false;
          openCourseManager();
          showSnackbar(`已删除 ${removed} 门课程`);
        }
        return;
      }

      if (event.target.closest("[data-adjust-new]")) {
        openAdjustEditor(
          event.target.closest("[data-adjust-new]").dataset.adjustNew
        );
        return;
      }

      const adjustDelete = event.target.closest("[data-adjust-delete]");
      if (adjustDelete) {
        if (window.confirm("删除这条临时调整吗？")) {
          J.removeAdjustment(adjustDelete.dataset.adjustDelete);
          openAdjustManager();
          showSnackbar("已恢复原课表");
        }
        return;
      }

      const dayButton = event.target.closest("[data-day]");
      if (dayButton) {
        const group = dayButton.closest("[data-chip-group]");
        $$("[data-day]", group).forEach((button) => {
          const active = button === dayButton;
          button.classList.toggle("is-active", active);
          button.setAttribute("aria-pressed", String(active));
        });
        const hidden = $('input[name="day"]', sheetBody);
        if (hidden) hidden.value = dayButton.dataset.day;
        return;
      }

      const parityButton = event.target.closest("[data-parity]");
      if (parityButton) {
        const group = parityButton.closest("[data-chip-group]");
        $$("[data-parity]", group).forEach((button) => {
          const active = button === parityButton;
          button.classList.toggle("is-active", active);
          button.setAttribute("aria-pressed", String(active));
        });
        const hidden = $('input[name="parity"]', sheetBody);
        if (hidden) hidden.value = parityButton.dataset.parity;
        return;
      }

      const toneButton = event.target.closest("[data-tone]");
      if (toneButton) {
        const group = toneButton.closest("[data-chip-group]");
        $$("[data-tone]", group).forEach((button) => {
          const active = button === toneButton;
          button.classList.toggle("is-active", active);
          button.setAttribute("aria-pressed", String(active));
        });
        const hidden = $('input[name="tone"]', sheetBody);
        if (hidden) hidden.value = toneButton.dataset.tone;
        return;
      }

      if (event.target.closest("[data-add-period]")) {
        const rows = $$("[data-period-row]", sheetBody);
        const lastRow = rows[rows.length - 1];
        const lastEnd = lastRow ? $("[data-period-end]", lastRow).value : "08:00";
        const startMinutes = J.toMinutes(lastEnd) + 10;
        const list = $("#periodList");
        if (list) {
          list.insertAdjacentHTML(
            "beforeend",
            periodRowHTML(
              { start: J.fromMinutes(startMinutes), end: J.fromMinutes(startMinutes + 45) },
              rows.length
            )
          );
          renumberPeriods();
          const added = list.lastElementChild;
          if (added) $("[data-period-start]", added).focus();
        }
        return;
      }

      const removePeriod = event.target.closest("[data-remove-period]");
      if (removePeriod) {
        const rows = $$("[data-period-row]", sheetBody);
        if (rows.length <= 1) {
          showSnackbar("至少保留一节课");
          return;
        }
        removePeriod.closest("[data-period-row]").remove();
        renumberPeriods();
        return;
      }

      if (event.target.closest("[data-save-periods]")) {
        savePeriodsFromSheet();
        return;
      }

      if (event.target.closest("[data-copy-data]")) {
        copyDataText();
        return;
      }

      if (event.target.closest("[data-import-data]")) {
        importDataFromSheet();
        return;
      }

      if (event.target.closest("[data-reset-data]")) {
        if (window.confirm("恢复示例课表会覆盖当前所有课程，确定继续吗？")) {
          J.resetData();
          closeSheet();
          showSnackbar("已恢复示例课表");
        }
        return;
      }

      if (event.target.closest("[data-clear-courses]")) {
        if (window.confirm("清空后可以重新添加课程，确定吗？")) {
          J.clearCourses();
          closeSheet();
          showSnackbar("课表已清空");
        }
        return;
      }

      const action = event.target.closest("[data-sheet-action]");
      if (!action) return;
      if (action.dataset.sheetAction === "reminder") {
        if (action.dataset.done === "true") return;
        action.dataset.done = "true";
        action.innerHTML = `${icon("check")}提醒已设置`;
        showSnackbar("将在课前 10 分钟提醒你");
      } else if (action.dataset.sheetAction === "materials") {
        showSnackbar("课程资料将在课程平台中打开");
      }
    });

    sheetBody.addEventListener("submit", (event) => {
      if (event.target.id === "courseForm") {
        event.preventDefault();
        handleCourseSubmit(event.target);
      } else if (event.target.id === "termForm") {
        event.preventDefault();
        handleTermSubmit(event.target);
      } else if (event.target.id === "adjustForm") {
        event.preventDefault();
        handleAdjustSubmit(event.target);
      }
    });

    sheetBody.addEventListener("change", (event) => {
      const target = event.target;
      if (target.id === "coursePeriod") {
        const value = target.value;
        const form = target.closest("form");
        if (value && form) {
          const parts = value.split("-").map(Number);
          const periods = J.getPeriods();
          const startPeriod = periods[parts[0] - 1];
          const endPeriod = periods[parts[1] - 1];
          if (startPeriod && endPeriod) {
            form.querySelector('input[name="start"]').value = startPeriod.start;
            form.querySelector('input[name="end"]').value = endPeriod.end;
          }
        }
        return;
      }

      if (target.name === "start" || target.name === "end") {
        const form = target.closest("form");
        const periodSelect = form ? form.querySelector("#coursePeriod") : null;
        if (periodSelect) periodSelect.value = "";
        return;
      }

      if (target.name === "weekStart") {
        const form = target.closest("form");
        const endSelect = form ? form.querySelector('select[name="weekEnd"]') : null;
        if (endSelect && Number(endSelect.value) < Number(target.value)) {
          endSelect.value = target.value;
        }
      }
    });

    sheetBody.addEventListener("input", (event) => {
      const form = event.target.closest("#courseForm");
      if (form) delete form.dataset.conflictOk;
    });

    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape") {
        if (sheetOpen) {
          event.preventDefault();
          closeSheet();
        }
        return;
      }

      if (sheetOpen && event.key === "Tab") {
        trapSheetFocus(event);
        return;
      }

      if (
        !sheetOpen &&
        (event.key === "ArrowLeft" || event.key === "ArrowRight") &&
        document.activeElement &&
        !["INPUT", "TEXTAREA", "SELECT", "BUTTON", "A"].includes(
          document.activeElement.tagName
        )
      ) {
        const next = event.key === "ArrowLeft" ? state.activePage - 1 : state.activePage + 1;
        if (next >= 0 && next <= 2) {
          goToPage(next);
        }
      }
    });

    themeMedia.addEventListener("change", () => {
      if (state.theme === "auto") applyAppearance();
    });

    document.addEventListener("visibilitychange", () => {
      if (document.hidden) {
        window.clearTimeout(minuteTimer);
        minuteTimer = null;
      } else {
        tick();
        scheduleMinuteTick();
      }
    });
  }

  function trapSheetFocus(event) {
    const focusables = $$(
      'button:not([disabled]), a[href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
      bottomSheet
    ).filter((element) => element.offsetParent !== null);
    if (!focusables.length) return;
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  function setPalette(palette, announce) {
    if (!PALETTES[palette]) return;
    if (state.palette === palette) {
      if (announce) showSnackbar(`已经是${PALETTES[palette]}色`);
      return;
    }
    state.palette = palette;
    applyAppearance();
    savePrefs();
    if (announce) showSnackbar(`主题色已换成${PALETTES[palette]}`);
  }

  function changeMonth(delta) {
    const current = new Date(state.viewYear, state.viewMonth, 1);
    const target = new Date(current.getFullYear(), current.getMonth() + delta, 1);
    const maxDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate();
    state.viewYear = target.getFullYear();
    state.viewMonth = target.getMonth();
    state.selectedDate = new Date(
      target.getFullYear(),
      target.getMonth(),
      Math.min(state.selectedDate.getDate(), maxDay)
    );
    renderToday({ resetScroll: false });
    renderCalendar();
  }

  /* ---------- 分钟级时钟 ---------- */

  function updateClockLabels(now) {
    const text = J.formatClock(now);
    $$("[data-clock]").forEach((element) => {
      element.textContent = text;
    });
  }

  function tick() {
    const now = new Date();
    updateClockLabels(now);

    const todayKey = J.dateKey(now);
    if (todayKey !== state.lastTodayKey) {
      const selectedWasToday = J.sameDate(
        state.selectedDate,
        J.parseDateKey(state.lastTodayKey)
      );
      state.lastTodayKey = todayKey;
      if (selectedWasToday) {
        state.selectedDate = J.atMidnight(now);
        state.viewYear = now.getFullYear();
        state.viewMonth = now.getMonth();
        renderToday({ resetScroll: false });
        renderCalendar();
      }
      renderSettingsDynamic();
    }


    if (J.sameDate(state.selectedDate, now)) {
      renderToday({ resetScroll: false });
      renderCalendar();
    }
  }

  function scheduleMinuteTick() {
    window.clearTimeout(minuteTimer);
    const now = new Date();
    const delay = 60000 - now.getSeconds() * 1000 - now.getMilliseconds() + 40;
    minuteTimer = window.setTimeout(() => {
      tick();
      scheduleMinuteTick();
    }, delay);
  }

  /* ---------- 初始化 ---------- */

  function init() {
    J.subscribe(function () {
      refreshAll();
    });
    buildPaletteSwatches();
    applyAppearance();
    renderSettingsDynamic();
    updateStageScale();
    renderToday({ resetScroll: true });
    renderCalendar();
    updateClockLabels(new Date());
    bindEvents();
    scheduleMinuteTick();
  }

  init();
})();
