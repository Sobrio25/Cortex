import { initializeApp } from "https://www.gstatic.com/firebasejs/11.10.0/firebase-app.js";
import {
  GoogleAuthProvider,
  browserLocalPersistence,
  getAuth,
  getIdToken,
  onAuthStateChanged,
  setPersistence,
  signInWithPopup,
  signOut,
} from "https://www.gstatic.com/firebasejs/11.10.0/firebase-auth.js";

const $ = (selector) => document.querySelector(selector);
const authView = $("#auth-view");
const dashboardView = $("#dashboard-view");
const authMessage = $("#auth-message");
const signInButton = $("#sign-in");
const refreshButton = $("#refresh");
const loadingLine = $("#loading");
const compact = new Intl.NumberFormat("es-MX", { notation: "compact", maximumFractionDigits: 1 });
const integer = new Intl.NumberFormat("es-MX", { maximumFractionDigits: 0 });
const usd = new Intl.NumberFormat("es-MX", { style: "currency", currency: "USD", minimumFractionDigits: 2, maximumFractionDigits: 4 });
const dateTime = new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeStyle: "short" });
const providerColors = ["#5b5bd6", "#3976e8", "#16875d", "#b86b18", "#b84f82", "#2f9cad"];

let auth;
let currentUser;
let usageData;
let dashboardMode = "subscriptions";
let selectedDays = 30;
let selectedMetric = "totalTokens";
let modelFilter = "";

function number(value) {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function formatTokens(value) {
  const amount = number(value);
  return amount >= 10_000 ? compact.format(amount) : integer.format(amount);
}

function formatCost(micros) {
  return usd.format(number(micros) / 1_000_000);
}

function formatDuration(totalMs, requests = 1) {
  const milliseconds = requests ? number(totalMs) / number(requests) : 0;
  if (milliseconds < 1_000) return `${Math.round(milliseconds)} ms`;
  return `${(milliseconds / 1_000).toFixed(milliseconds >= 10_000 ? 1 : 2)} s`;
}

function initials(value) {
  return String(value || "AI").split(/\s+/).map((part) => part[0]).join("").slice(0, 2).toUpperCase();
}

function setLoading(active) {
  loadingLine.classList.toggle("active", active);
  refreshButton.classList.toggle("spinning", active);
  refreshButton.disabled = active;
}

function showAuth(message = "") {
  authView.hidden = false;
  dashboardView.hidden = true;
  authMessage.textContent = message;
}

function showDashboard() {
  authView.hidden = true;
  dashboardView.hidden = false;
  $("#user-avatar").textContent = initials(currentUser?.displayName || currentUser?.email);
  $("#user-avatar").title = currentUser?.email || "Administrador";
}

async function loadUsage() {
  if (!currentUser) return;
  setLoading(true);
  try {
    const token = await getIdToken(currentUser);
    const endpoint = dashboardMode === "global" ? "/v1/admin/app-usage" : "/v1/admin/usage";
    const response = await fetch(`${endpoint}?days=${selectedDays}`, {
      headers: { Authorization: `Bearer ${token}`, Accept: "application/json" },
      cache: "no-store",
    });
    const payload = await response.json().catch(() => ({}));
    if (response.status === 401) {
      await signOut(auth);
      throw new Error("Tu sesión expiró. Vuelve a iniciar sesión.");
    }
    if (response.status === 403) {
      showAuth("Esta cuenta no está autorizada para ver el panel.");
      return;
    }
    if (!response.ok) throw new Error(payload.error || "No se pudieron cargar los datos de uso.");
    usageData = payload;
    render();
    $("#last-updated").textContent = `Actualizado ${dateTime.format(new Date(payload.generatedAt))}`;
  } catch (error) {
    const message = error instanceof Error ? error.message : "No se pudieron cargar los datos.";
    if (!dashboardView.hidden) $("#last-updated").textContent = message;
    else showAuth(message);
  } finally {
    setLoading(false);
  }
}

function renderMetrics() {
  const totals = usageData?.totals || {};
  const requests = number(totals.requests);
  const successes = number(totals.successfulRequests);
  const successRate = requests ? successes / requests * 100 : 0;
  $("#metric-requests").textContent = integer.format(requests);
  if (dashboardMode === "global") {
    $("#metric-requests-note").textContent = `${integer.format(number(totals.subscriptionRequests))} suscripción · ${integer.format(number(totals.byokRequests))} BYOK · ${integer.format(number(totals.localRequests))} local`;
  } else {
    $("#metric-requests-note").textContent = `${integer.format(number(totals.freeRequests))} gratis · ${integer.format(number(totals.fallbackRequests))} fallback`;
  }
  $("#metric-tokens").textContent = formatTokens(totals.totalTokens);
  $("#metric-tokens-note").textContent = `${formatTokens(totals.promptTokens)} entrada · ${formatTokens(totals.completionTokens)} salida`;
  if (dashboardMode === "global") {
    const outsideSubscription = number(totals.byokRequests) + number(totals.localRequests);
    $("#metric-third-label").textContent = "Uso BYOK / local";
    $("#metric-third-icon").textContent = "◎";
    $("#metric-third-value").textContent = integer.format(outsideSubscription);
    $("#metric-third-note").textContent = `${integer.format(number(totals.byokRequests))} BYOK · ${integer.format(number(totals.localRequests))} en dispositivo`;
  } else {
    $("#metric-third-label").textContent = "Coste estimado";
    $("#metric-third-icon").textContent = "$";
    $("#metric-third-value").textContent = formatCost(totals.costMicros);
    $("#metric-third-note").textContent = requests ? `${formatCost(number(totals.costMicros) / requests)} por solicitud` : "Sin consumo facturable";
  }
  $("#metric-success").textContent = `${successRate.toFixed(requests ? 1 : 0)}%`;
  $("#metric-success-note").textContent = `${integer.format(number(totals.errorRequests))} errores · ${formatDuration(totals.durationMs, requests)} promedio`;
}

function svgElement(name, attributes = {}, text = "") {
  const element = document.createElementNS("http://www.w3.org/2000/svg", name);
  for (const [key, value] of Object.entries(attributes)) element.setAttribute(key, String(value));
  if (text) element.textContent = text;
  return element;
}

function renderChart() {
  const svg = $("#usage-chart");
  svg.replaceChildren();
  const daily = usageData?.daily || [];
  const values = daily.map((row) => number(row[selectedMetric]));
  const max = Math.max(...values, 1);
  const width = 760;
  const height = 250;
  const pad = { top: 12, right: 12, bottom: 28, left: 48 };
  const plotWidth = width - pad.left - pad.right;
  const plotHeight = height - pad.top - pad.bottom;

  const defs = svgElement("defs");
  const gradient = svgElement("linearGradient", { id: "bar-gradient", x1: "0", y1: "0", x2: "0", y2: "1" });
  gradient.append(svgElement("stop", { offset: "0%", "stop-color": "#6868dc" }));
  gradient.append(svgElement("stop", { offset: "100%", "stop-color": "#b9b9ed", "stop-opacity": ".45" }));
  defs.append(gradient);
  svg.append(defs);

  for (let index = 0; index <= 4; index += 1) {
    const ratio = index / 4;
    const y = pad.top + plotHeight * ratio;
    svg.append(svgElement("line", { x1: pad.left, y1: y, x2: width - pad.right, y2: y, class: "chart-grid" }));
    const labelValue = max * (1 - ratio);
    svg.append(svgElement("text", { x: pad.left - 8, y: y + 3, "text-anchor": "end", class: "chart-label" }, formatTokens(labelValue)));
  }

  const slot = plotWidth / Math.max(daily.length, 1);
  const barWidth = Math.max(2, Math.min(18, slot * .56));
  const points = [];
  daily.forEach((row, index) => {
    const value = values[index];
    const barHeight = value / max * plotHeight;
    const x = pad.left + slot * index + (slot - barWidth) / 2;
    const y = pad.top + plotHeight - barHeight;
    const bar = svgElement("rect", { x, y, width: barWidth, height: Math.max(barHeight, .5), rx: Math.min(4, barWidth / 3), class: "chart-bar" });
    bar.append(svgElement("title", {}, `${row.day}: ${selectedMetric === "requests" ? integer.format(value) : formatTokens(value)}`));
    svg.append(bar);
    points.push(`${x + barWidth / 2},${y}`);
    const labelEvery = Math.max(1, Math.ceil(daily.length / 7));
    if (index % labelEvery === 0 || index === daily.length - 1) {
      const date = new Date(`${row.day}T00:00:00Z`);
      const label = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "short", timeZone: "UTC" }).format(date);
      svg.append(svgElement("text", { x: x + barWidth / 2, y: height - 7, "text-anchor": "middle", class: "chart-label" }, label));
    }
  });
  if (points.length > 1) svg.append(svgElement("polyline", { points: points.join(" "), class: "chart-line" }));

  const total = values.reduce((sum, value) => sum + value, 0);
  $("#chart-legend-label").textContent = selectedMetric === "requests" ? "Solicitudes" : "Tokens totales";
  $("#chart-total").textContent = selectedMetric === "requests" ? integer.format(total) : formatTokens(total);
}

function renderProviders() {
  const list = $("#provider-list");
  const empty = $("#providers-empty");
  list.replaceChildren();
  const providers = (usageData?.providers || []).slice(0, 6);
  empty.hidden = providers.length > 0;
  const max = Math.max(...providers.map((provider) => number(provider.totalTokens)), 1);
  providers.forEach((provider, index) => {
    const row = document.createElement("div");
    row.className = "provider-row";
    row.style.setProperty("--provider-color", providerColors[index % providerColors.length]);
    const meta = document.createElement("div");
    meta.className = "provider-meta";
    const name = document.createElement("div");
    name.className = "provider-name";
    const dot = document.createElement("span");
    dot.className = "provider-dot";
    const nameText = document.createElement("span");
    nameText.textContent = provider.provider;
    name.append(dot, nameText);
    const value = document.createElement("span");
    value.className = "provider-value";
    value.textContent = dashboardMode === "global"
      ? `${formatTokens(provider.totalTokens)} · ${integer.format(number(provider.requests))} solicitudes`
      : `${formatTokens(provider.totalTokens)} · ${formatCost(provider.costMicros)}`;
    meta.append(name, value);
    const track = document.createElement("div");
    track.className = "provider-track";
    const fill = document.createElement("div");
    fill.className = "provider-fill";
    fill.style.width = `${Math.max(2, number(provider.totalTokens) / max * 100)}%`;
    track.append(fill);
    row.append(meta, track);
    list.append(row);
  });
}

function appendCell(row, child) {
  const cell = document.createElement("td");
  if (child instanceof Node) cell.append(child);
  else cell.textContent = String(child);
  row.append(cell);
}

function tag(text, className) {
  const element = document.createElement("span");
  element.className = className;
  element.textContent = text;
  return element;
}

function sourceLabel(source) {
  if (source === "subscription") return "Suscripción";
  if (source === "local") return "Local";
  return "BYOK";
}

function renderModels() {
  const body = $("#models-body");
  const empty = $("#models-empty");
  body.replaceChildren();
  const query = modelFilter.trim().toLowerCase();
  const models = (usageData?.models || []).filter((row) => !query || `${row.model} ${row.provider} ${row.gateway || ""} ${row.source || ""}`.toLowerCase().includes(query));
  empty.hidden = models.length > 0;
  models.forEach((model) => {
    const row = document.createElement("tr");
    const modelCell = document.createElement("div");
    modelCell.className = "model-cell";
    const glyph = document.createElement("span");
    glyph.className = "model-glyph";
    glyph.textContent = initials(model.provider);
    const modelName = document.createElement("span");
    modelName.textContent = model.model;
    modelCell.append(glyph, modelName);
    appendCell(row, modelCell);
    appendCell(row, tag(model.provider, "provider-tag"));
    appendCell(row, tag(
      dashboardMode === "global" ? sourceLabel(model.source) : model.gateway,
      "gateway-tag"
    ));
    appendCell(row, integer.format(number(model.requests)));
    appendCell(row, formatTokens(model.totalTokens));
    if (dashboardMode === "global") {
      const estimatedRate = number(model.requests)
        ? number(model.estimatedRequests) / number(model.requests) * 100
        : 0;
      appendCell(row, estimatedRate ? `${estimatedRate.toFixed(0)}% estim.` : "Exacta");
    } else {
      appendCell(row, formatCost(model.costMicros));
    }
    appendCell(row, formatDuration(model.durationMs, model.requests));
    body.append(row);
  });
}

function renderRecent() {
  const body = $("#recent-body");
  const empty = $("#recent-empty");
  body.replaceChildren();
  const recent = usageData?.recent || [];
  empty.hidden = recent.length > 0;
  recent.forEach((event) => {
    const row = document.createElement("tr");
    appendCell(row, event.createdAt ? dateTime.format(new Date(event.createdAt)) : "Procesando…");
    const modelCell = document.createElement("div");
    modelCell.className = "model-cell";
    const glyph = document.createElement("span");
    glyph.className = "model-glyph";
    glyph.textContent = initials(event.provider);
    const name = document.createElement("span");
    name.textContent = event.model;
    modelCell.append(glyph, name);
    appendCell(row, modelCell);
    appendCell(row, tag(event.provider, "provider-tag"));
    appendCell(row, event.user);
    const context = document.createElement("div");
    context.append(tag(
      dashboardMode === "global" ? sourceLabel(event.source) : event.plan,
      "plan-tag"
    ));
    if (dashboardMode !== "global" && event.agentName) {
      const agent = document.createElement("span");
      agent.className = "agent-name";
      agent.textContent = event.agentName;
      context.append(agent);
    }
    appendCell(row, context);
    appendCell(row, `${formatTokens(event.totalTokens)}${event.usageEstimated ? " est." : ""}`);
    appendCell(row, dashboardMode === "global" ? formatDuration(event.durationMs) : formatCost(event.costMicros));
    appendCell(row, tag(event.status === "success" ? "Completada" : "Error", `status-tag status-${event.status}`));
    body.append(row);
  });
}

function render() {
  const global = dashboardMode === "global";
  $("#dashboard-eyebrow").textContent = global ? "Toda la app" : "Suscripciones";
  $("#dashboard-title").textContent = global ? "Uso global de Cortex" : "Uso de suscripciones";
  $("#range-label").textContent = `${global ? "Suscripción, BYOK y local" : "Planes y modelos administrados"} · últimos ${selectedDays} días · ${usageData?.range?.timezone || "UTC"}`;
  $("#models-context-heading").textContent = global ? "Origen" : "Gateway";
  $("#models-detail-heading").textContent = global ? "Medición" : "Coste";
  $("#recent-context-heading").textContent = global ? "Origen" : "Plan / agente";
  $("#recent-detail-heading").textContent = global ? "Duración" : "Coste";
  $("#footer-note").textContent = global
    ? "Datos agregados en UTC · BYOK y local usan estimaciones de tokens"
    : "Datos agregados en UTC · Costes estimados en USD";
  renderMetrics();
  renderChart();
  renderProviders();
  renderModels();
  renderRecent();
}

document.querySelectorAll("[data-dashboard-mode]").forEach((button) => {
  button.addEventListener("click", () => {
    const nextMode = button.dataset.dashboardMode;
    if (nextMode === dashboardMode) return;
    dashboardMode = nextMode;
    usageData = undefined;
    modelFilter = "";
    $("#model-search").value = "";
    document.querySelectorAll("[data-dashboard-mode]").forEach((item) => {
      const active = item === button;
      item.classList.toggle("active", active);
      item.setAttribute("aria-selected", String(active));
    });
    loadUsage();
  });
});

document.querySelectorAll("[data-days]").forEach((button) => {
  button.addEventListener("click", () => {
    selectedDays = Number(button.dataset.days);
    document.querySelectorAll("[data-days]").forEach((item) => item.classList.toggle("active", item === button));
    loadUsage();
  });
});

document.querySelectorAll("[data-metric]").forEach((button) => {
  button.addEventListener("click", () => {
    selectedMetric = button.dataset.metric;
    document.querySelectorAll("[data-metric]").forEach((item) => item.classList.toggle("active", item === button));
    if (usageData) renderChart();
  });
});

$("#model-search").addEventListener("input", (event) => {
  modelFilter = event.target.value;
  if (usageData) renderModels();
});
refreshButton.addEventListener("click", loadUsage);
$("#sign-out").addEventListener("click", () => signOut(auth));

async function bootstrap() {
  try {
    const response = await fetch("/__/firebase/init.json", { cache: "no-store" });
    if (!response.ok) throw new Error("Firebase Hosting no devolvió la configuración del proyecto.");
    const firebaseConfig = await response.json();
    auth = getAuth(initializeApp(firebaseConfig));
    await setPersistence(auth, browserLocalPersistence);
    const provider = new GoogleAuthProvider();
    provider.setCustomParameters({ prompt: "select_account" });
    signInButton.addEventListener("click", async () => {
      signInButton.disabled = true;
      authMessage.textContent = "";
      try {
        await signInWithPopup(auth, provider);
      } catch (error) {
        authMessage.textContent = error instanceof Error ? error.message : "No se pudo iniciar sesión.";
      } finally {
        signInButton.disabled = false;
      }
    });
    onAuthStateChanged(auth, (user) => {
      currentUser = user;
      if (!user) {
        usageData = undefined;
        showAuth();
        return;
      }
      showDashboard();
      loadUsage();
    });
  } catch (error) {
    showAuth(error instanceof Error ? error.message : "No se pudo iniciar el panel.");
    signInButton.disabled = true;
  }
}

bootstrap();
