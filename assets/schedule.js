/* 节律 · 共享课表数据与轻量时间工具 */
(function () {
  "use strict";

  const DAY_KEYS = ["sun", "mon", "tue", "wed", "thu", "fri", "sat"];
  const WEEKDAYS = ["日", "一", "二", "三", "四", "五", "六"];
  const STORAGE_KEY = "jielu.data.v2";
  const DEFAULT_TERM_START = "2026-08-31"; // 2026 秋季学期第一周周一
  const DEFAULT_TERM_WEEKS = 18;

  const DEFAULT_PERIODS = [
    { id: "p1", start: "08:00", end: "08:45" },
    { id: "p2", start: "08:55", end: "09:40" },
    { id: "p3", start: "10:00", end: "10:45" },
    { id: "p4", start: "10:55", end: "11:40" },
    { id: "p5", start: "13:30", end: "14:15" },
    { id: "p6", start: "14:25", end: "15:10" },
    { id: "p7", start: "15:30", end: "16:15" },
    { id: "p8", start: "16:25", end: "17:10" },
    { id: "p9", start: "18:30", end: "19:15" },
    { id: "p10", start: "19:25", end: "20:10" }
  ];

  /* 示例课表：首次启动时写入本机，之后完全由用户维护 */
  const DEFAULT_COURSES = [
    {
      id: "mon-math", day: 1, start: "08:00", end: "09:40",
      name: "高等数学 A", teacher: "王建国", room: "求真楼 201",
      kind: "公共基础", tone: "iris", credits: 4,
      weekStart: 1, weekEnd: 16, parity: "all", periodStart: 1, periodEnd: 2,
      note: "本周：定积分的换元法与分部积分。课前完成习题 8.3，课上会随机抽讲。"
    },
    {
      id: "mon-sketch", day: 1, start: "10:00", end: "11:40",
      name: "设计素描", teacher: "刘珊", room: "艺术楼 108",
      kind: "学科基础", tone: "clay", credits: 2,
      weekStart: 1, weekEnd: 12, parity: "all", periodStart: 3, periodEnd: 4,
      note: "本周：用三张速写记录校园里的秩序感。携带炭笔与 A3 画纸。"
    },
    {
      id: "mon-interaction", day: 1, start: "14:00", end: "15:40",
      name: "交互设计基础", teacher: "周野", room: "创新工坊 301",
      kind: "专业必修", tone: "sage", credits: 3,
      weekStart: 1, weekEnd: 16, parity: "all", periodStart: null, periodEnd: null,
      note: "本周主题：Material You 的动效语言。分组走查上周的原型，带上访谈记录。"
    },
    {
      id: "mon-pe", day: 1, start: "16:00", end: "16:45",
      name: "体育 · 羽毛球", teacher: "赵峰", room: "东区体育馆",
      kind: "公共基础", tone: "sky", credits: 1,
      weekStart: 1, weekEnd: 16, parity: "all", periodStart: null, periodEnd: null,
      note: "本周：正手高远球与双打轮转。穿运动鞋，提前 10 分钟热身。"
    },
    {
      id: "tue-research", day: 2, start: "09:00", end: "10:40",
      name: "用户体验研究", teacher: "林清", room: "明德楼 A105",
      kind: "专业必修", tone: "amber", credits: 3,
      weekStart: 1, weekEnd: 16, parity: "all", periodStart: null, periodEnd: null,
      note: "本周：访谈提纲评审。每组带 3 个待验证的假设和一份访谈记录。"
    },
    {
      id: "tue-vis", day: 2, start: "13:30", end: "15:10",
      name: "信息可视化", teacher: "陈默", room: "计算中心 402",
      kind: "专业选修", tone: "sky", credits: 2,
      weekStart: 1, weekEnd: 14, parity: "all", periodStart: 5, periodEnd: 6,
      note: "Lab 05：用一张图讲清一周的课程节奏。携带电脑和上周的数据集。"
    },
    {
      id: "tue-studio", day: 2, start: "18:30", end: "20:05",
      name: "设计工作坊", teacher: "周野", room: "创新工坊 301",
      kind: "实践课程", tone: "rose", credits: 2,
      weekStart: 3, weekEnd: 16, parity: "all", periodStart: null, periodEnd: null,
      note: "开放工作坊：自选主题推进，结束前 20 分钟做一次小组互评。"
    },
    {
      id: "wed-history", day: 3, start: "08:00", end: "09:40",
      name: "界面设计史", teacher: "李雯", room: "明德楼 B203",
      kind: "专业必修", tone: "iris", credits: 2,
      weekStart: 1, weekEnd: 16, parity: "all", periodStart: 1, periodEnd: 2,
      note: "本周：从瑞士平面设计到动态界面。阅读《设计中的设计》第三章。"
    },
    {
      id: "wed-service", day: 3, start: "10:00", end: "11:40",
      name: "服务设计", teacher: "赵一", room: "创新工坊 204",
      kind: "专业选修", tone: "sage", credits: 2,
      weekStart: 2, weekEnd: 15, parity: "all", periodStart: 3, periodEnd: 4,
      note: "本周：服务蓝图与触点梳理。带上你观察到的校园服务案例。"
    },
    {
      id: "wed-practice", day: 3, start: "14:00", end: "16:30",
      name: "综合设计实践", teacher: "周野", room: "创新工坊 302",
      kind: "专业必修", tone: "clay", credits: 4,
      weekStart: 1, weekEnd: 18, parity: "all", periodStart: null, periodEnd: null,
      note: "阶段评审：展示问题定义、研究证据与第一版方案。每组 12 分钟。"
    },
    {
      id: "thu-photo", day: 4, start: "09:00", end: "10:40",
      name: "摄影与观察", teacher: "刘珊", room: "艺术楼 206",
      kind: "专业选修", tone: "amber", credits: 2,
      weekStart: 1, weekEnd: 12, parity: "all", periodStart: null, periodEnd: null,
      note: "本周：街头观察与视觉叙事。带相机或手机即可，课后交 6 张照片。"
    },
    {
      id: "thu-data", day: 4, start: "10:50", end: "12:20",
      name: "数据分析", teacher: "陈默", room: "计算中心 402",
      kind: "专业选修", tone: "sky", credits: 2,
      weekStart: 1, weekEnd: 14, parity: "all", periodStart: null, periodEnd: null,
      note: "本周：用描述性统计读懂问卷。携带已完成清洗的数据集。"
    },
    {
      id: "thu-psych", day: 4, start: "15:30", end: "17:10",
      name: "设计心理学", teacher: "李雯", room: "明德楼 B203",
      kind: "专业必修", tone: "iris", credits: 3,
      weekStart: 1, weekEnd: 16, parity: "all", periodStart: 7, periodEnd: 8,
      note: "本周：注意、记忆与界面线索。课堂有一个 15 分钟的快速实验。"
    },
    {
      id: "fri-ethics", day: 5, start: "08:00", end: "09:40",
      name: "设计伦理", teacher: "李雯", room: "明德楼 B203",
      kind: "专业必修", tone: "iris", credits: 2,
      weekStart: 1, weekEnd: 16, parity: "all", periodStart: 1, periodEnd: 2,
      note: "本周：算法推荐与注意力的边界。阅读材料已上传课程平台。"
    },
    {
      id: "fri-interaction", day: 5, start: "10:00", end: "11:40",
      name: "交互设计基础", teacher: "周野", room: "创新工坊 301",
      kind: "专业必修", tone: "sage", credits: 3,
      weekStart: 1, weekEnd: 16, parity: "all", periodStart: 3, periodEnd: 4,
      note: "本周主题：Material You 的动效语言。带上课前访谈记录，分组走查原型。"
    },
    {
      id: "fri-vis", day: 5, start: "14:00", end: "15:40",
      name: "信息可视化", teacher: "陈默", room: "计算中心 402",
      kind: "专业选修", tone: "sky", credits: 2,
      weekStart: 1, weekEnd: 14, parity: "all", periodStart: null, periodEnd: null,
      note: "Lab 06：为课程表设计一张可读性更高的周视图。携带电脑。"
    },
    {
      id: "fri-research", day: 5, start: "16:00", end: "17:40",
      name: "用户研究", teacher: "林清", room: "明德楼 A105",
      kind: "专业必修", tone: "amber", credits: 3,
      weekStart: 1, weekEnd: 16, parity: "all", periodStart: null, periodEnd: null,
      note: "小组汇报：访谈洞察。每组 8 分钟，预留 5 分钟问答。"
    }
  ];

  /* 主题配色（Material You 种子色） */
  const TONES = [
    { key: "clay", name: "陶土" },
    { key: "moss", name: "苔藓" },
    { key: "iris", name: "鸢尾" },
    { key: "tide", name: "海盐" }
  ];

  /* 课程标记色，与 CSS 的 .c-* 类对应 */
  const COURSE_TONES = [
    { key: "clay", name: "陶土" },
    { key: "sage", name: "鼠尾草" },
    { key: "sky", name: "晴空" },
    { key: "iris", name: "鸢尾" },
    { key: "amber", name: "琥珀" },
    { key: "rose", name: "绯樱" }
  ];

  const PARITIES = [
    { key: "all", name: "每周" },
    { key: "odd", name: "单周" },
    { key: "even", name: "双周" }
  ];

  const PALETTE_SEEDS = {
    clay: "#ad5841",
    moss: "#4f715b",
    iris: "#6f5b93",
    tide: "#456b81"
  };

  let data = null;
  const listeners = [];

  /* ---------- 基础工具 ---------- */

  function pad(value) {
    return String(value).padStart(2, "0");
  }

  function toMinutes(time) {
    const [h, m] = String(time || "00:00").split(":").map(Number);
    return (h || 0) * 60 + (m || 0);
  }

  function fromMinutes(total) {
    const clamped = Math.max(0, Math.min(23 * 60 + 59, Math.round(total)));
    return `${pad(Math.floor(clamped / 60))}:${pad(clamped % 60)}`;
  }

  function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
  }

  function atMidnight(date) {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate());
  }

  function addDays(date, amount) {
    const next = new Date(date.getFullYear(), date.getMonth(), date.getDate());
    next.setDate(next.getDate() + amount);
    return next;
  }

  function startOfWeek(date) {
    const base = atMidnight(date);
    const offset = (base.getDay() + 6) % 7; // 周一为一周的开始
    return addDays(base, -offset);
  }

  function sameDate(a, b) {
    return (
      a.getFullYear() === b.getFullYear() &&
      a.getMonth() === b.getMonth() &&
      a.getDate() === b.getDate()
    );
  }

  function dateKey(date) {
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
      date.getDate()
    )}`;
  }

  function parseDateKey(key) {
    const [y, m, d] = String(key).split("-").map(Number);
    return new Date(y, m - 1, d);
  }

  function isValidTime(value) {
    return /^([01]\d|2[0-3]):[0-5]\d$/.test(String(value || ""));
  }

  function getDayKey(date) {
    return DAY_KEYS[date.getDay()];
  }

  function uid(prefix) {
    return `${prefix}-${Date.now().toString(36)}-${Math.random()
      .toString(36)
      .slice(2, 7)}`;
  }

  function clone(value) {
    return JSON.parse(JSON.stringify(value));
  }

  /* ---------- 数据装载 ---------- */

  function normalizeCourse(raw, termWeeks) {
    const weeks = clamp(Number(termWeeks) || DEFAULT_TERM_WEEKS, 1, 40);
    const course = {
      id: raw.id || uid("c"),
      day: clamp(Number(raw.day) || 1, 1, 7),
      name: String(raw.name || "未命名课程").trim().slice(0, 40) || "未命名课程",
      teacher: String(raw.teacher || "").trim().slice(0, 30),
      room: String(raw.room || "").trim().slice(0, 40),
      kind: String(raw.kind || "专业必修").trim().slice(0, 20),
      tone: COURSE_TONES.some((tone) => tone.key === raw.tone) ? raw.tone : "clay",
      credits: clamp(Number(raw.credits) || 0, 0, 20),
      note: String(raw.note || "").trim().slice(0, 400),
      start: isValidTime(raw.start) ? raw.start : "08:00",
      end: isValidTime(raw.end) ? raw.end : "09:40",
      weekStart: clamp(Number(raw.weekStart) || 1, 1, weeks),
      weekEnd: clamp(Number(raw.weekEnd) || weeks, 1, weeks),
      parity: ["all", "odd", "even"].includes(raw.parity) ? raw.parity : "all",
      periodStart: null,
      periodEnd: null
    };
    if (course.weekEnd < course.weekStart) course.weekEnd = course.weekStart;
    if (toMinutes(course.end) <= toMinutes(course.start)) {
      course.end = fromMinutes(toMinutes(course.start) + 45);
    }
    const ps = Number(raw.periodStart);
    const pe = Number(raw.periodEnd);
    if (ps >= 1 && pe >= ps) {
      course.periodStart = Math.round(ps);
      course.periodEnd = Math.round(pe);
    }
    return course;
  }

  function normalizePeriods(raw) {
    if (!Array.isArray(raw) || !raw.length) return clone(DEFAULT_PERIODS);
    return raw
      .filter((item) => item && isValidTime(item.start) && isValidTime(item.end))
      .map((item) => ({
        id: item.id || uid("p"),
        start: item.start,
        end: item.end
      }))
      .slice(0, 20);
  }

  function normalizeAdjustment(raw) {
    if (!raw || typeof raw !== "object") return null;
    const isDate = (value) => /^\d{4}-\d{2}-\d{2}$/.test(String(value || ""));
    const from = isDate(raw.from) ? String(raw.from) : "";
    const to = isDate(raw.to) ? String(raw.to) : "";
    const kind = raw.kind === "off" ? "off" : "swap";
    if (kind === "swap") {
      if (!from || !to || from === to) return null;
      return {
        id: String(raw.id || uid("adj")).slice(0, 40),
        kind: "swap",
        from,
        to,
        note: String(raw.note || "").trim().slice(0, 60)
      };
    }
    if (!from) return null;
    return {
      id: String(raw.id || uid("adj")).slice(0, 40),
      kind: "off",
      from,
      to: "",
      note: String(raw.note || "").trim().slice(0, 60)
    };
  }

  function normalizeData(raw) {
    const term = {
      start:
        raw && raw.term && /^\d{4}-\d{2}-\d{2}$/.test(raw.term.start || "")
          ? raw.term.start
          : DEFAULT_TERM_START,
      weeks: clamp(
        Number(raw && raw.term && raw.term.weeks) || DEFAULT_TERM_WEEKS,
        1,
        40
      )
    };
    const periods = normalizePeriods(raw && raw.periods);
    const courses = (raw && Array.isArray(raw.courses) ? raw.courses : []).map(
      (course) => normalizeCourse(course, term.weeks)
    );
    const adjustments = (raw && Array.isArray(raw.adjustments) ? raw.adjustments : [])
      .map(normalizeAdjustment)
      .filter(Boolean)
      .slice(0, 120);
    return { version: 2, term, periods, courses, adjustments };
  }

  function defaultData() {
    return normalizeData({
      term: { start: DEFAULT_TERM_START, weeks: DEFAULT_TERM_WEEKS },
      periods: DEFAULT_PERIODS,
      courses: DEFAULT_COURSES
    });
  }

  function loadData() {
    let stored = null;
    try {
      stored = localStorage.getItem(STORAGE_KEY);
    } catch (error) {}
    let parsed = null;
    if (stored) {
      try {
        parsed = JSON.parse(stored);
      } catch (error) {}
    }
    if (parsed && Array.isArray(parsed.courses)) {
      data = normalizeData(parsed);
    } else {
      data = defaultData();
      if (!stored) persist(false);
    }
    return data;
  }

  function persist(notify) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    } catch (error) {}
    if (notify !== false) {
      listeners.forEach((listener) => {
        try {
          listener(data);
        } catch (error) {}
      });
    }
  }

  function subscribe(listener) {
    if (typeof listener !== "function") return function () {};
    listeners.push(listener);
    return function () {
      const index = listeners.indexOf(listener);
      if (index >= 0) listeners.splice(index, 1);
    };
  }

  /* ---------- 学期与周次 ---------- */

  function getTerm() {
    return { start: data.term.start, weeks: data.term.weeks };
  }

  function saveTerm(term) {
    const start =
      term && /^\d{4}-\d{2}-\d{2}$/.test(term.start || "")
        ? term.start
        : data.term.start;
    const weeks = clamp(Number(term && term.weeks) || data.term.weeks, 1, 40);
    data.term = { start, weeks };
    data.courses.forEach((course) => {
      course.weekStart = clamp(course.weekStart, 1, weeks);
      course.weekEnd = clamp(course.weekEnd, course.weekStart, weeks);
    });
    persist();
  }

  function getWeekNumber(date) {
    const start = atMidnight(parseDateKey(data.term.start));
    const diff = Math.floor((atMidnight(date) - start) / 86400000);
    return Math.floor(diff / 7) + 1;
  }

  function isWithinTerm(date) {
    const week = getWeekNumber(date);
    return week >= 1 && week <= data.term.weeks;
  }

  function getWeekLabel(date) {
    const week = getWeekNumber(date);
    if (week < 1) return "学期开始前";
    if (week > data.term.weeks) return "学期已结束";
    return `第 ${week} 教学周`;
  }

  function getWeekParity(date) {
    const week = getWeekNumber(date);
    if (week < 1 || week > data.term.weeks) return "";
    return week % 2 === 1 ? "单周" : "双周";
  }

  function weekdayCN(date, short) {
    const value = WEEKDAYS[date.getDay()];
    return short ? value : `星期${value}`;
  }

  function formatDate(date, options) {
    const opts = options || {};
    const y = date.getFullYear();
    const m = date.getMonth() + 1;
    const d = date.getDate();
    if (opts.full) return `${y} 年 ${m} 月 ${d} 日`;
    if (opts.year) return `${y} 年 ${m} 月`;
    return `${m} 月 ${d} 日`;
  }

  function formatClock(date) {
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  function formatDuration(totalMinutes) {
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    if (!hours) return `${minutes} 分钟`;
    if (!minutes) return `${hours} 小时`;
    return `${hours} 小时 ${minutes} 分`;
  }

  /* ---------- 课程读取 ---------- */

  function isCourseActiveInWeek(course, week) {
    if (week < course.weekStart || week > course.weekEnd) return false;
    if (course.parity === "odd") return week % 2 === 1;
    if (course.parity === "even") return week % 2 === 0;
    return true;
  }

  function getAdjustmentForDate(date) {
    const key = dateKey(date);
    const swapIn = data.adjustments.find(
      (item) => item.kind === "swap" && item.to === key
    );
    if (swapIn) return { type: "swap-in", adjustment: swapIn };
    const removed = data.adjustments.find((item) => item.from === key);
    if (removed) {
      return {
        type: removed.kind === "off" ? "off" : "swap-out",
        adjustment: removed
      };
    }
    return null;
  }

  function getCoursesForDate(date) {
    const week = getWeekNumber(date);
    if (week < 1 || week > data.term.weeks) return [];
    const adjustment = getAdjustmentForDate(date);
    if (
      adjustment &&
      (adjustment.type === "off" || adjustment.type === "swap-out")
    ) {
      return [];
    }
    let day = date.getDay();
    let activeWeek = week;
    if (adjustment && adjustment.type === "swap-in") {
      const source = parseDateKey(adjustment.adjustment.from);
      day = source.getDay();
      activeWeek = getWeekNumber(source);
    }
    return data.courses
      .filter(
        (course) => course.day === day && isCourseActiveInWeek(course, activeWeek)
      )
      .sort(
        (a, b) =>
          toMinutes(a.start) - toMinutes(b.start) ||
          toMinutes(a.end) - toMinutes(b.end)
      );
  }

  function getAllCourses() {
    return data.courses
      .slice()
      .sort(
        (a, b) =>
          a.day - b.day ||
          toMinutes(a.start) - toMinutes(b.start) ||
          a.name.localeCompare(b.name, "zh")
      );
  }

  function getCourseById(id) {
    return data.courses.find((course) => course.id === id) || null;
  }

  function getDayStats(courses) {
    if (!courses.length) {
      return { count: 0, minutes: 0, start: "", end: "", range: "" };
    }
    const start = courses[0].start;
    const end = courses[courses.length - 1].end;
    const minutes = courses.reduce(
      (sum, course) => sum + toMinutes(course.end) - toMinutes(course.start),
      0
    );
    return {
      count: courses.length,
      minutes,
      start,
      end,
      range: `${start}–${end}`
    };
  }

  function getCurrentAndNext(courses, nowMinutes) {
    let current = null;
    let next = null;
    courses.forEach((course) => {
      const start = toMinutes(course.start);
      const end = toMinutes(course.end);
      if (start <= nowMinutes && nowMinutes < end) current = course;
      if (start > nowMinutes && !next) next = course;
    });
    return { current, next };
  }

  function getNextTeachingDay(date, maxLookahead) {
    const limit = maxLookahead || 21;
    for (let offset = 1; offset <= limit; offset += 1) {
      const nextDate = addDays(date, offset);
      const courses = getCoursesForDate(nextDate);
      if (courses.length) return { date: nextDate, courses };
    }
    return null;
  }

  function getNextCourseFrom(date, includeRestOfDay) {
    const courses = getCoursesForDate(date);
    const now = new Date();
    const nowMinutes = now.getHours() * 60 + now.getMinutes();
    if (includeRestOfDay && sameDate(date, now)) {
      const rest = courses.filter((course) => toMinutes(course.end) > nowMinutes);
      if (rest.length) {
        return { date, course: rest[0] };
      }
    }
    const nextDay = getNextTeachingDay(date, 21);
    if (nextDay) return { date: nextDay.date, course: nextDay.courses[0] };
    return null;
  }

  function getWeekDates(date) {
    const monday = startOfWeek(date);
    return Array.from({ length: 7 }, (_, index) => addDays(monday, index));
  }

  function monthStats(year, month) {
    let count = 0;
    let activeDays = 0;
    const total = new Date(year, month + 1, 0).getDate();
    for (let day = 1; day <= total; day += 1) {
      const date = new Date(year, month, day);
      const courses = getCoursesForDate(date);
      if (courses.length) {
        count += courses.length;
        activeDays += 1;
      }
    }
    return { count, activeDays };
  }

  /* ---------- 课程写入 ---------- */

  function addCourse(input) {
    const course = normalizeCourse(
      Object.assign({ id: uid("c") }, input),
      data.term.weeks
    );
    data.courses.push(course);
    persist();
    return course;
  }

  function updateCourse(id, input) {
    const index = data.courses.findIndex((course) => course.id === id);
    if (index < 0) return null;
    const merged = Object.assign({}, data.courses[index], input, { id });
    const course = normalizeCourse(merged, data.term.weeks);
    data.courses[index] = course;
    persist();
    return course;
  }

  function deleteCourse(id) {
    const index = data.courses.findIndex((course) => course.id === id);
    if (index < 0) return null;
    const [removed] = data.courses.splice(index, 1);
    persist();
    return removed;
  }

  function deleteCourses(ids) {
    const doomed = new Set(Array.isArray(ids) ? ids : []);
    if (!doomed.size) return 0;
    const before = data.courses.length;
    data.courses = data.courses.filter((course) => !doomed.has(course.id));
    const removed = before - data.courses.length;
    if (removed) persist();
    return removed;
  }

  function restoreCourse(course) {
    if (!course || !course.id) return;
    if (getCourseById(course.id)) return;
    data.courses.push(course);
    persist();
  }

  function clearCourses() {
    data.courses = [];
    persist();
  }

  /* ---------- 调休与临时调课 ---------- */

  function getAdjustments() {
    return data.adjustments
      .slice()
      .sort((a, b) => a.from.localeCompare(b.from));
  }

  function addAdjustment(input) {
    const clean = normalizeAdjustment(input);
    if (!clean) throw new Error("请选择有效的日期");
    const clash = data.adjustments.find(
      (item) =>
        item.from === clean.from ||
        item.from === clean.to ||
        (item.to && (item.to === clean.from || item.to === clean.to))
    );
    if (clash) throw new Error("这一天已经有调休或调课记录了");
    data.adjustments.push(clean);
    persist();
    return clean;
  }

  function removeAdjustment(id) {
    const index = data.adjustments.findIndex((item) => item.id === id);
    if (index < 0) return false;
    data.adjustments.splice(index, 1);
    persist();
    return true;
  }

  function clearAdjustments() {
    if (!data.adjustments.length) return;
    data.adjustments = [];
    persist();
  }

  function resetData() {
    data = defaultData();
    persist();
  }

  /* ---------- 节次 ---------- */

  function getPeriods() {
    return data.periods.map((period) => Object.assign({}, period));
  }

  function savePeriods(periods) {
    const clean = normalizePeriods(periods);
    if (!clean.length) return false;
    data.periods = clean;
    data.courses.forEach((course) => {
      if (!course.periodStart || !course.periodEnd) return;
      const startPeriod = clean[course.periodStart - 1];
      const endPeriod = clean[course.periodEnd - 1];
      if (startPeriod && endPeriod) {
        course.start = startPeriod.start;
        course.end = endPeriod.end;
      } else {
        course.periodStart = null;
        course.periodEnd = null;
      }
    });
    persist();
    return true;
  }

  function getPeriodLabel(course) {
    if (!course || !course.periodStart) return "";
    return course.periodStart === course.periodEnd
      ? `第 ${course.periodStart} 节`
      : `第 ${course.periodStart}–${course.periodEnd} 节`;
  }

  /* ---------- 周次展示 ---------- */

  function formatWeeks(course) {
    if (!course) return "";
    const range =
      course.weekStart === course.weekEnd
        ? `第 ${course.weekStart} 周`
        : `${course.weekStart}–${course.weekEnd} 周`;
    if (course.parity === "odd") return `${range} · 单周`;
    if (course.parity === "even") return `${range} · 双周`;
    return range;
  }

  /* ---------- 冲突检测 ---------- */

  function parityOverlap(a, b) {
    if (a === "all" || b === "all") return true;
    return a === b;
  }

  function findConflicts(candidate) {
    if (!candidate) return [];
    return data.courses.filter((course) => {
      if (course.id === candidate.id) return false;
      if (course.day !== candidate.day) return false;
      if (
        Math.max(course.weekStart, candidate.weekStart) >
        Math.min(course.weekEnd, candidate.weekEnd)
      ) {
        return false;
      }
      if (!parityOverlap(course.parity, candidate.parity)) return false;
      return (
        toMinutes(course.start) < toMinutes(candidate.end) &&
        toMinutes(candidate.start) < toMinutes(course.end)
      );
    });
  }

  /* ---------- 备份 ---------- */

  function exportData() {
    return JSON.stringify(data, null, 2);
  }

  function parseImportText(text) {
    let raw = String(text == null ? "" : text)
      .replace(/^\uFEFF/, "")
      .replace(/[\u200B-\u200D\uFEFF]/g, "")
      .trim();
    raw = raw.replace(/[“”]/g, '"').replace(/[‘’]/g, "'");
    const first = raw.indexOf("{");
    const last = raw.lastIndexOf("}");
    if (first >= 0 && last > first) {
      raw = raw.slice(first, last + 1);
    }
    let parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) parsed = { courses: parsed };
    return parsed;
  }

  function importData(text) {
    let parsed;
    try {
      parsed = parseImportText(text);
    } catch (error) {
      throw new Error("无法解析 JSON，请确认复制了完整内容");
    }
    if (!parsed || typeof parsed !== "object") {
      throw new Error("不是有效的课表数据");
    }
    if (!Array.isArray(parsed.courses) && !Array.isArray(parsed.adjustments)) {
      throw new Error("缺少 courses 列表");
    }
    const next = normalizeData(parsed);
    if (!next.courses.length && !next.periods.length) {
      throw new Error("没有可导入的课程");
    }
    data = next;
    persist();
    return {
      courses: data.courses.length,
      periods: data.periods.length,
      adjustments: data.adjustments.length
    };
  }

  function reloadData() {
    loadData();
  }

  const api = {
    TONES,
    COURSE_TONES,
    PARITIES,
    PALETTE_SEEDS,
    DAY_KEYS,
    WEEKDAYS,
    toMinutes,
    fromMinutes,
    clamp,
    atMidnight,
    addDays,
    startOfWeek,
    sameDate,
    dateKey,
    parseDateKey,
    isValidTime,
    getDayKey,
    getCoursesForDate,
    getAllCourses,
    getCourseById,
    getWeekNumber,
    getWeekLabel,
    getWeekParity,
    isWithinTerm,
    weekdayCN,
    formatDate,
    formatClock,
    formatDuration,
    getDayStats,
    getCurrentAndNext,
    getNextTeachingDay,
    getNextCourseFrom,
    getWeekDates,
    monthStats,
    addCourse,
    updateCourse,
    deleteCourse,
    deleteCourses,
    restoreCourse,
    clearCourses,
    resetData,
    getAdjustments,
    getAdjustmentForDate,
    addAdjustment,
    removeAdjustment,
    clearAdjustments,
    getPeriods,
    savePeriods,
    getPeriodLabel,
    formatWeeks,
    getTerm,
    saveTerm,
    findConflicts,
    exportData,
    importData,
    reloadData,
    subscribe
  };

  Object.defineProperty(api, "TERM_START", {
    get: function () {
      return parseDateKey(data.term.start);
    }
  });
  Object.defineProperty(api, "TERM_WEEKS", {
    get: function () {
      return data.term.weeks;
    }
  });

  loadData();
  window.JIELU = api;
})();
