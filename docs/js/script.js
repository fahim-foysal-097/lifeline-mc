// Copy to Clipboard Utility
function copyCommand(text, customMessage) {
  navigator.clipboard.writeText(text);
  const toast = document.getElementById("toast");
  const toastMsg = document.getElementById("toast-msg");
  toastMsg.innerText = customMessage || `Copied "${text}" to clipboard!`;
  toast.classList.add("show");
  setTimeout(() => {
    toast.classList.remove("show");
  }, 2500);
}

// Active YAML Tab Switcher
let activeTab = "config";

function switchTab(tab) {
  activeTab = tab;
  const configBtn = document.getElementById("tab-config-btn");
  const messagesBtn = document.getElementById("tab-messages-btn");
  const configCode = document.getElementById("code-config");
  const messagesCode = document.getElementById("code-messages");

  if (tab === "config") {
    configBtn.className =
      "px-4 py-2 rounded-xl text-xs font-bold bg-indigo-500/20 text-indigo-300 border border-indigo-500/40 transition";
    messagesBtn.className =
      "px-4 py-2 rounded-xl text-xs font-bold text-slate-400 hover:text-white transition";
    configCode.classList.remove("hidden");
    messagesCode.classList.add("hidden");
  } else {
    messagesBtn.className =
      "px-4 py-2 rounded-xl text-xs font-bold bg-indigo-500/20 text-indigo-300 border border-indigo-500/40 transition";
    configBtn.className =
      "px-4 py-2 rounded-xl text-xs font-bold text-slate-400 hover:text-white transition";
    messagesCode.classList.remove("hidden");
    configCode.classList.add("hidden");
  }
}

function copyActiveCode() {
  const targetId = activeTab === "config" ? "code-config" : "code-messages";
  const codeText = document.getElementById(targetId).innerText;
  copyCommand(codeText, `${activeTab}.yml content copied!`);
}

// Filter for Commands Table
const cmdSearch = document.getElementById("cmd-search");
const tableRows = document.querySelectorAll("#cmd-table-body tr");

cmdSearch.addEventListener("input", (e) => {
  const query = e.target.value.toLowerCase();
  tableRows.forEach((row) => {
    const text = row.innerText.toLowerCase();
    if (text.includes(query)) {
      row.style.display = "";
    } else {
      row.style.display = "none";
    }
  });
});

// Mouse Position Tracking for Card Radial Glow Effects
document.querySelectorAll(".mc-glass-card").forEach((card) => {
  card.addEventListener("mousemove", (e) => {
    const rect = card.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    card.style.setProperty("--mouse-x", `${x}px`);
    card.style.setProperty("--mouse-y", `${y}px`);
  });
});

// Initialize Swiper.js Photo Gallery Carousel
const swiper = new Swiper(".gallery-swiper", {
  loop: true,
  autoplay: {
    delay: 3500,
    disableOnInteraction: false,
    pauseOnMouseEnter: true,
  },
  spaceBetween: 20,
  effect: "slide",
  speed: 700,
  pagination: {
    el: ".swiper-pagination",
    clickable: true,
  },
  navigation: {
    nextEl: ".swiper-next-btn",
    prevEl: ".swiper-prev-btn",
  },
});

// Interactive Canvas Background Dot Grid (Smooth Velocity Acceleration Physics)
const canvas = document.getElementById("bg-canvas");
const ctx = canvas.getContext("2d");
const spotlight = document.getElementById("mouse-spotlight");

let mouse = { x: -1000, y: -1000 };
let dots = [];
const spacing = 28;

function resizeCanvas() {
  canvas.width = window.innerWidth;
  canvas.height = window.innerHeight;
  initDots();
}

function initDots() {
  dots = [];
  const cols = Math.ceil(canvas.width / spacing);
  const rows = Math.ceil(canvas.height / spacing);

  for (let i = 0; i < cols; i++) {
    for (let j = 0; j < rows; j++) {
      dots.push({
        baseX: i * spacing + spacing / 2,
        baseY: j * spacing + spacing / 2,
        x: i * spacing + spacing / 2,
        y: j * spacing + spacing / 2,
        vx: 0,
        vy: 0,
        size: 1.5,
      });
    }
  }
}

window.addEventListener("mousemove", (e) => {
  mouse.x = e.clientX;
  mouse.y = e.clientY;

  // Move ambient background spotlight
  spotlight.style.transform = `translate(${mouse.x}px, ${mouse.y}px) translate(-50%, -50%)`;
});

window.addEventListener("mouseleave", () => {
  mouse.x = -1000;
  mouse.y = -1000;
});

window.addEventListener("resize", resizeCanvas);

function animateCanvas() {
  ctx.clearRect(0, 0, canvas.width, canvas.height);

  const maxDist = 150;
  const spring = 0.03; // Smooth slow elastic return speed
  const damping = 0.88; // Inertial velocity dampening (slow deceleration)
  const pushForce = 0.8; // Soft gentle acceleration push

  for (let i = 0; i < dots.length; i++) {
    const dot = dots[i];
    const dx = mouse.x - dot.x;
    const dy = mouse.y - dot.y;
    const dist = Math.sqrt(dx * dx + dy * dy);

    let ax = 0;
    let ay = 0;

    // Repulsion acceleration when cursor is near
    if (dist < maxDist && dist > 0) {
      const angle = Math.atan2(dy, dx);
      const force = (maxDist - dist) / maxDist;
      ax = -Math.cos(angle) * force * pushForce;
      ay = -Math.sin(angle) * force * pushForce;
    }

    // Spring attraction force towards origin base position
    ax += (dot.baseX - dot.x) * spring;
    ay += (dot.baseY - dot.y) * spring;

    // Apply smooth velocity acceleration
    dot.vx = (dot.vx + ax) * damping;
    dot.vy = (dot.vy + ay) * damping;

    // Update particle positions with smooth transition
    dot.x += dot.vx;
    dot.y += dot.vy;

    // Render dot based on current distance to mouse
    const currentMouseDist = Math.sqrt(
      (mouse.x - dot.x) ** 2 + (mouse.y - dot.y) ** 2,
    );
    if (currentMouseDist < maxDist) {
      const force = (maxDist - currentMouseDist) / maxDist;
      ctx.fillStyle = `rgba(129, 140, 248, ${0.4 + force * 0.6})`;
      ctx.beginPath();
      ctx.arc(dot.x, dot.y, dot.size + force * 2.2, 0, Math.PI * 2);
      ctx.fill();
    } else {
      ctx.fillStyle = "rgba(255, 255, 255, 0.18)";
      ctx.beginPath();
      ctx.arc(dot.x, dot.y, dot.size, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  requestAnimationFrame(animateCanvas);
}

// Initialize Canvas Grid
resizeCanvas();
animateCanvas();
