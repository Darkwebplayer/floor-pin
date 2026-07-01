/* FloorPin — shared data store + sidebar/nav injection. */
(function () {
  // ─────────────────────────────────────────────────────────────
  // Store: plans + per-plan locations, persisted to localStorage.
  // Exposed as window.FloorPin so every page reads the same data.
  // ─────────────────────────────────────────────────────────────
  const PLAN_KEY = "floorpin.plans.v1", LAST_KEY = "floorpin.lastPlan", LOC_PREFIX = "floorpin.loc.";
  const IMG = "assets/issue.jpg";
  const SEED_PLANS = [
    { id:"lakeside", name:"Lakeside Duplex — Ground Floor", sub:"Unit A & B · residential handover snagging", img:"assets/floor-plan.webp", created:"2026-06-18" },
    { id:"maple",    name:"Maple Court — Level 2",          sub:"Commercial fit-out · QA inspection",        img:"assets/floor-plan.webp", created:"2026-06-15" },
    { id:"harbour",  name:"Harbour Office — Reception Wing", sub:"Maintenance audit · Q2",                    img:"assets/floor-plan.webp", created:"2026-06-10" },
  ];
  const SEED_LOC = {
    lakeside: [
      { id:1, name:"Reception", x:18.5, y:39, defects:[{ id:11, title:"Ceiling light not working", desc:"Downlight above the desk stays off after handover.", status:"progress", date:"2026-06-21", x:21, y:36, photo:IMG }] },
      { id:2, name:"Kitchen Sink", x:26, y:30, defects:[{ id:21, title:"Sink not working", desc:"No flow from the mixer tap; suspected isolation valve closed.", status:"open", date:"2026-06-22", x:28.5, y:32, photo:IMG }] },
      { id:3, name:"Living Space — Unit A", x:36, y:25, defects:[{ id:31, title:"Wall crack near window", desc:"Hairline crack ~30cm running from the window reveal.", status:"open", date:"2026-06-20", x:33.5, y:27, photo:IMG }] },
      { id:4, name:"WC", x:46, y:53, defects:[{ id:41, title:"Slow draining basin", desc:"Water pools for ~20s before clearing.", status:"resolved", date:"2026-06-18", x:48, y:55, photo:IMG }] },
      { id:5, name:"Sauna", x:42, y:71, defects:[] },
      { id:6, name:"Bedroom — Unit A", x:17, y:62, defects:[{ id:61, title:"Wardrobe door misaligned", desc:"Door catches on the frame; hinge adjustment needed.", status:"open", date:"2026-06-21", x:19.5, y:64, photo:IMG }] },
      { id:7, name:"Living Space — Unit B", x:64, y:25, defects:[] },
      { id:8, name:"Bedroom — Unit B", x:83, y:62, defects:[{ id:81, title:"Skirting not sealed", desc:"Gap at the skirting board on the east wall.", status:"open", date:"2026-06-22", x:80.5, y:64, photo:IMG }] },
    ],
    maple: [
      { id:1, name:"Lobby", x:30, y:35, defects:[{ id:11, title:"Floor tile lifting", desc:"Two tiles near the entrance mat are loose.", status:"open", date:"2026-06-19", x:32, y:37, photo:IMG }] },
      { id:2, name:"Meeting Room", x:60, y:48, defects:[] },
    ],
    harbour: [],
  };
  const read = (k, f) => { try { const r = JSON.parse(localStorage.getItem(k)); return r == null ? f : r; } catch (e) { return f; } };
  const write = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} };

  const FloorPin = {
    plans() { let p = read(PLAN_KEY, null); if (!p) { p = SEED_PLANS.slice(); write(PLAN_KEY, p); } return p; },
    savePlans(a) { write(PLAN_KEY, a); },
    plan(id) { return FloorPin.plans().find(p => p.id === id) || null; },
    locations(id) { const k = LOC_PREFIX + id; let l = read(k, null); if (l == null) { l = JSON.parse(JSON.stringify(SEED_LOC[id] || [])); write(k, l); } return l; },
    saveLocations(id, a) { write(LOC_PREFIX + id, a); },
    lastPlan() { const id = read(LAST_KEY, null); const plans = FloorPin.plans(); return ((plans.find(p => p.id === id)) || plans[0] || {}).id; },
    setLastPlan(id) { write(LAST_KEY, id); },
    addPlan({ name, sub, img }) {
      const plans = FloorPin.plans();
      const plan = { id: "plan_" + Date.now(), name: name || "Untitled plan", sub: sub || "", img: img || "assets/floor-plan.webp", created: new Date().toISOString().slice(0, 10) };
      plans.push(plan); FloorPin.savePlans(plans); FloorPin.saveLocations(plan.id, []); return plan;
    },
    counts(id) {
      const locs = FloorPin.locations(id);
      let open = 0, total = 0;
      locs.forEach(l => l.defects.forEach(d => { total++; if (d.status === "open") open++; }));
      return { locations: locs.length, defects: total, open };
    },
  };
  window.FloorPin = FloorPin;

  // ─────────────────────────────────────────────────────────────
  // On-screen keyboard inset → --kb, so bottom sheets / modals lift
  // above the keyboard instead of being covered on mobile.
  // ─────────────────────────────────────────────────────────────
  const vv = window.visualViewport;
  if (vv) {
    const setKb = () => {
      const kb = Math.max(0, window.innerHeight - vv.height - vv.offsetTop);
      document.documentElement.style.setProperty('--kb', kb + 'px');
    };
    vv.addEventListener('resize', setKb);
    vv.addEventListener('scroll', setKb);
    setKb();
  }

  // ─────────────────────────────────────────────────────────────
  // Sidebar + mobile drawer
  // ─────────────────────────────────────────────────────────────
  const I = {
    map:'<path d="M9 4 3 6v14l6-2 6 2 6-2V4l-6 2-6-2Zm0 0v14m6-12v14" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/>',
    users:'<circle cx="9" cy="8" r="3.2" fill="none" stroke="currentColor" stroke-width="1.6"/><path d="M3.5 20a5.5 5.5 0 0 1 11 0M16 5.3a3.2 3.2 0 0 1 0 6M16.5 20a5.5 5.5 0 0 0-2.3-4.5" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>',
    doc:'<path d="M6 3h8l4 4v14H6z" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M14 3v4h4M9 12h6M9 16h6" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/>',
    logo:'<path d="M12 21s7-6.3 7-11a7 7 0 1 0-14 0c0 4.7 7 11 7 11Z" fill="none" stroke="#fff" stroke-width="1.7"/><circle cx="12" cy="10" r="2.3" fill="#fff"/>'
  };
  const nav = [
    ['plans','plans.html','Floor Plans','map'],
    ['staff','staff.html','Staff','users'],
    ['report','report.html','Reports','doc'],
  ];
  const page = document.body.dataset.page || '';
  const items = nav.map(([id,href,label,icon]) =>
    `<a class="nav-item${id===page?' active':''}" href="${href}"><svg viewBox="0 0 24 24">${I[icon]}</svg><span>${label}</span></a>`
  ).join('');
  const html =
    `<aside class="sidebar">
      <a class="brand" href="floorplan.html"><span class="logo"><svg viewBox="0 0 24 24">${I.logo}</svg></span><span class="name">Floor<b>Pin</b></span></a>
      <div class="nav-group-label">Inspection</div>
      ${items}
      <div class="spacer"></div>
      <a class="nav-item" href="index.html"><svg viewBox="0 0 24 24"><path d="M14 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2v-2M9 12h11m0 0-3-3m3 3-3 3" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg><span>Sign out</span></a>
      <div class="side-user"><span class="avatar">AM</span><span class="meta"><span class="n">Aisha Malik</span><span class="r">Admin</span></span></div>
    </aside>`;

  const mount = document.querySelector('[data-shell]');
  if (!mount) return;
  mount.insertAdjacentHTML('afterbegin', html);

  // ── mobile: off-canvas drawer + hamburger ──
  const backdrop = document.createElement('div');
  backdrop.className = 'nav-backdrop';
  mount.appendChild(backdrop);
  const setOpen = open => mount.classList.toggle('nav-open', open);
  backdrop.addEventListener('click', () => setOpen(false));

  const topbar = mount.querySelector('.topbar');
  if (topbar) {
    const burger = document.createElement('button');
    burger.className = 'hamburger';
    burger.setAttribute('aria-label', 'Menu');
    burger.innerHTML = '<svg viewBox="0 0 24 24" width="22" height="22"><path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>';
    topbar.insertBefore(burger, topbar.firstChild);
    burger.addEventListener('click', () => setOpen(!mount.classList.contains('nav-open')));
  }
  mount.querySelectorAll('.sidebar a').forEach(a => a.addEventListener('click', () => setOpen(false)));
})();
