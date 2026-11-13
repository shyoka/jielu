/* 节律 · 手表端交互 */
(function () {
  "use strict";

  const J = window.JIELU;
  const PREFS_KEY = "jielu.prefs.v1";
  const PALETTES = ["clay", "moss", "iris", "tide"];
  const RADIUS = 47.6;
  const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

  const $ = (selector, root = document) => root.querySelector(selector);

  const watchFace = $("#watchFace");
  const watchDate = $("#watchDate");
  const statusText = $("#statusText");
  const courseName = $("#courseName");
  const courseMeta = $("#courseMeta");
  const watchFocus = $("#watchFocus");
  const bigValue = $("#bigValue");
  const bigLabel = $("#bigLabel");
  const nextLabel = $("#nextLabel");
  const nextValue = $("#nextValue");
  const agendaCount = $("#agendaCount");
  const agendaRows = $("#agendaRows");
  const agendaFootCount = $("#agendaFootCount");
  const agendaView = $("#agendaView");
  const nowView = $("#nowView");
  const settingsView = $("#settingsView");
  const paletteLabel = $("#paletteLabel");
  const swatches = Array.from(document.querySelectorAll("[data-palette-pick]"));
  const watchArc = $("#watchArc");
  const dots = Array.from(document.querySelectorAll(".watch-dots i"));

  const PALETTE_NAMES = { clay: "陶土", moss: "苔绿", iris: "鸢尾", tide: "潮汐" };
  const VIEW_LABELS = ["轻触查看今日安排", "轻触查看设置", "轻触返回此刻"];

  let minuteTimer = null;
  let viewIndex = 0;

  function escapeHTML(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function readPalette() {
    try {
      const raw = localStorage.getItem(PREFS_KEY);
      const prefs = raw ? JSON.parse(raw) : {};
      return PALETTES.includes(prefs.palette) ? prefs.palette : "clay";
    } catch (error) {
      return "clay";
    }
  }

  function applyPalette(palette) {
    if (!PALETTES.includes(palette)) return;
    document.documentElement.dataset.palette = palette;
    document.documentElement.dataset.theme = "dark";
  }

  function setArc(progress, visible) {
    watchArc.style.strokeDasharray = String(CIRCUMFERENCE);
    watchArc.style.strokeDashoffset = String(
      visible ? CIRCUMFERENCE * (1 - Math.max(0.012, Math.min(progress, 1))) : CIRCUMFERENCE
    );
    watchArc.style.opacity = visible ? "0.92" : "0";
  }

  function nextTeachingFrom(date) {
    for (let offset = 1; offset <= 21; offset += 1) {
      const nextDate = J.addDays(date, offset);
      const courses = J.getCoursesForDate(nextDate);
      if (courses.length) {
        return { date: nextDate, course: courses[0] };
      }
    }
    return null;
  }

  function getFooterNext(courses, index, today) {
    if (index >= 0 && index < courses.length - 1) {
      return {
        label: "之后",
        value: `${courses[index + 1].start} · ${courses[index + 1].name}`,
        inline: true
      };
    }
    const next = nextTeachingFrom(today);
    if (!next) return { label: "接下来", value: "学期课程已结束", inline: false };
    return {
      label: "下一节",
      value: `周${J.weekdayCN(next.date, true)} ${next.course.start} · ${next.course.name}`,
      inline: false
    };
  }

  function buildAgenda(courses, nowMinutes, isToday) {
    agendaCount.textContent = `${courses.length} 节`;
    agendaFootCount.textContent = String(courses.length);
    if (!courses.length) {
      agendaRows.innerHTML = `
        <div class="watch-agenda-empty">
          <b>今天没有课</b>
          <span>把时间留给自己</span>
        </div>`;
      return;
    }

    agendaRows.innerHTML = courses
      .map((course) => {
        const start = J.toMinutes(course.start);
        const end = J.toMinutes(course.end);
        const isActive = isToday && nowMinutes >= start && nowMinutes < end;
        const isPast = isToday && nowMinutes >= end;
        return `
          <div class="watch-agenda-row${isActive ? " is-active" : ""}${isPast ? " is-past" : ""}">
            <span class="watch-agenda-time">${course.start}</span>
            <span class="watch-agenda-name">${escapeHTML(course.name)}</span>
            <span class="watch-agenda-dot"></span>
          </div>`;
      })
      .join("");
  }

  function refresh() {
    const now = new Date();
    const today = J.atMidnight(now);
    const nowMinutes = now.getHours() * 60 + now.getMinutes();
    const courses = J.getCoursesForDate(today);
    const stats = J.getDayStats(courses);
    const { current, next } = J.getCurrentAndNext(courses, nowMinutes);

    document.querySelectorAll("[data-clock]").forEach((element) => {
      element.textContent = J.formatClock(now);
    });
    watchDate.textContent = `${J.formatDate(today)} · 周${J.weekdayCN(today, true)}`;

    if (current) {
      const start = J.toMinutes(current.start);
      const end = J.toMinutes(current.end);
      const progress = (nowMinutes - start) / (end - start);
      const remaining = Math.max(1, end - nowMinutes);
      const index = courses.indexOf(current);
      const footer = getFooterNext(courses, index, today);

      statusText.textContent = "正在上课";
      courseName.textContent = current.name;
      courseMeta.textContent = `${current.room} · ${current.teacher}`;
      bigValue.textContent = String(remaining);
      bigLabel.textContent = "分钟后下课";
      watchFocus.classList.remove("is-time", "is-dash");
      nextLabel.textContent = footer.label;
      nextValue.textContent = footer.value;
      setArc(progress, true);
    } else if (next) {
      const index = courses.indexOf(next);
      const footer = getFooterNext(courses, index, today);

      statusText.textContent = "接下来";
      courseName.textContent = next.name;
      courseMeta.textContent = `${next.room} · ${next.teacher}`;
      bigValue.textContent = next.start;
      bigLabel.textContent = "开始上课";
      watchFocus.classList.add("is-time");
      watchFocus.classList.remove("is-dash");
      nextLabel.textContent = footer.label;
      nextValue.textContent = footer.value;
      setArc(0.035, true);
    } else if (courses.length) {
      const nextTeaching = nextTeachingFrom(today);
      statusText.textContent = "今日完成";
      courseName.textContent = "今天的课，结束啦";
      courseMeta.textContent = `${stats.count} 节 · ${J.formatDuration(stats.minutes)}`;
      bigValue.textContent = stats.end;
      bigLabel.textContent = "已下课";
      watchFocus.classList.add("is-time");
      watchFocus.classList.remove("is-dash");
      nextLabel.textContent = "下一节";
      nextValue.textContent = nextTeaching
        ? `周${J.weekdayCN(nextTeaching.date, true)} ${nextTeaching.course.start} · ${nextTeaching.course.name}`
        : "学期课程已结束";
      setArc(1, true);
    } else {
      const nextTeaching = nextTeachingFrom(today);
      statusText.textContent = "今天休息";
      courseName.textContent = "给自己留一点空白";
      courseMeta.textContent = "今天没有课程";
      bigValue.textContent = "—";
      bigLabel.textContent = "空白";
      watchFocus.classList.add("is-dash");
      watchFocus.classList.remove("is-time");
      nextLabel.textContent = "下一节";
      nextValue.textContent = nextTeaching
        ? `周${J.weekdayCN(nextTeaching.date, true)} ${nextTeaching.course.start} · ${nextTeaching.course.name}`
        : "学期课程已结束";
      setArc(0, false);
    }

    buildAgenda(courses, nowMinutes, true);
  }

  function setView(next) {
    viewIndex = (next + 3) % 3;
    watchFace.classList.toggle("is-agenda", viewIndex === 1);
    watchFace.classList.toggle("is-settings", viewIndex === 2);
    [nowView, agendaView, settingsView].forEach((view, index) => {
      view.setAttribute("aria-hidden", String(index !== viewIndex));
    });
    dots.forEach((dot, index) => dot.classList.toggle("is-active", index === viewIndex));
    watchFace.setAttribute("aria-label", VIEW_LABELS[viewIndex]);
    if (viewIndex === 2) {
      setArc(0, false);
    } else {
      refresh();
    }
  }

  function cycleView() {
    setView(viewIndex + 1);
  }

  function syncSwatches(palette) {
    swatches.forEach((swatch) => {
      const active = swatch.dataset.palettePick === palette;
      swatch.classList.toggle("is-active", active);
      swatch.setAttribute("aria-checked", String(active));
    });
    if (paletteLabel) paletteLabel.textContent = PALETTE_NAMES[palette] || "陶土";
  }

  function scheduleMinuteTick() {
    window.clearTimeout(minuteTimer);
    const now = new Date();
    const delay = 60000 - now.getSeconds() * 1000 - now.getMilliseconds() + 30;
    minuteTimer = window.setTimeout(() => {
      refresh();
      scheduleMinuteTick();
    }, delay);
  }

  function init() {
    const palette = readPalette();
    applyPalette(palette);
    syncSwatches(palette);
    refresh();
    scheduleMinuteTick();

    swatches.forEach((swatch) => {
      swatch.addEventListener("click", (event) => {
        event.stopPropagation();
        const palette = swatch.dataset.palettePick;
        applyPalette(palette);
        syncSwatches(palette);
      });
    });

    watchFace.addEventListener("click", cycleView);
    watchFace.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        cycleView();
      }
    });

    document.addEventListener("visibilitychange", () => {
      if (document.hidden) {
        window.clearTimeout(minuteTimer);
        minuteTimer = null;
      } else {
        refresh();
        scheduleMinuteTick();
      }
    });

    window.addEventListener("message", (event) => {
      if (!event.data) return;
      if (event.data.type === "jielu:appearance") {
        applyPalette(event.data.palette);
        syncSwatches(event.data.palette);
        refresh();
      } else if (event.data.type === "jielu:data") {
        J.reloadData();
        refresh();
      }
    });
  }

  init();
})();
