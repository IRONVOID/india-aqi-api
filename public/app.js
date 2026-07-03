// =========================================================
// India AQI Dashboard — vanilla JS, no frameworks.
// Talks to the same Java HttpServer that's serving this page,
// so every fetch() below is a same-origin request (no CORS
// headers needed on the backend).
// =========================================================

const state = {
  search: "",
  pollutant: "",
  sort: "",
  page: 1,
  limit: 12,
};

// Maps the exact status strings returned by PollutionAnalysisService
// to a CSS class + a rank used to pick the "worst" pollutant for the
// headline pill on each card.
const STATUS_META = {
  "Good": { css: "good", rank: 0 },
  "Moderate": { css: "moderate", rank: 1 },
  "Unhealthy for Sensitive Groups": { css: "usg", rank: 2 },
  "Unhealthy": { css: "unhealthy", rank: 3 },
  "Very Unhealthy": { css: "very-unhealthy", rank: 4 },
};

const els = {
  searchInput: document.getElementById("searchInput"),
  pollutantSelect: document.getElementById("pollutantSelect"),
  sortSelect: document.getElementById("sortSelect"),
  stationGrid: document.getElementById("stationGrid"),
  emptyState: document.getElementById("emptyState"),
  resultsCount: document.getElementById("resultsCount"),
  prevPage: document.getElementById("prevPage"),
  nextPage: document.getElementById("nextPage"),
  pageIndicator: document.getElementById("pageIndicator"),
  statTotalStations: document.getElementById("statTotalStations"),
  statTotalPollutants: document.getElementById("statTotalPollutants"),
  chartCanvas: document.getElementById("coverageChart"),
  cardTemplate: document.getElementById("stationCardTemplate"),
};

init();

function init() {
  loadStats();
  loadStations();

  let searchTimer = null;
  els.searchInput.addEventListener("input", () => {
    clearTimeout(searchTimer);
    // Debounce so we don't fire a request on every keystroke.
    searchTimer = setTimeout(() => {
      state.search = els.searchInput.value.trim();
      state.page = 1;
      loadStations();
    }, 300);
  });

  els.pollutantSelect.addEventListener("change", () => {
    state.pollutant = els.pollutantSelect.value;
    state.page = 1;
    loadStations();
  });

  els.sortSelect.addEventListener("change", () => {
    state.sort = els.sortSelect.value;
    state.page = 1;
    loadStations();
  });

  els.prevPage.addEventListener("click", () => {
    if (state.page > 1) {
      state.page -= 1;
      loadStations();
    }
  });

  els.nextPage.addEventListener("click", () => {
    state.page += 1;
    loadStations();
  });
}

// =========================================================
// DATA LOADING
// =========================================================

function buildStationsQuery() {
  const params = new URLSearchParams();

  // The search box matches against station name. Locality data is
  // often missing from OpenAQ for Indian stations (see backend
  // README notes), and the name field already contains city info
  // for most stations, so searching by name covers the common case.
  if (state.search) params.set("name", state.search);
  if (state.pollutant) params.set("pollutant", state.pollutant);
  if (state.sort) params.set("sort", state.sort);
  params.set("page", state.page);
  params.set("limit", state.limit);

  return params.toString();
}

async function loadStations() {
  setGridLoading(true);

  try {
    const response = await fetch("/stations?" + buildStationsQuery());
    const body = await response.json();

    renderStations(body.data, body.pagination);
  } catch (err) {
    els.resultsCount.textContent = "Couldn't reach the station data. Is the server running?";
    els.stationGrid.innerHTML = "";
  } finally {
    setGridLoading(false);
  }
}

async function loadStats() {
  try {
    const response = await fetch("/stats");
    const stats = await response.json();

    els.statTotalStations.textContent = stats.totalStations ?? "—";
    els.statTotalPollutants.textContent = stats.totalPollutantTypes ?? "—";

    drawCoverageChart(stats);
  } catch (err) {
    // Stats are supplementary — if this fails, the rest of the
    // dashboard still works, so we just leave the header dashes.
  }
}

// =========================================================
// STATION GRID
// =========================================================

function setGridLoading(isLoading) {
  els.stationGrid.setAttribute("aria-busy", String(isLoading));
}

function renderStations(stations, pagination) {
  els.stationGrid.innerHTML = "";

  const hasResults = stations && stations.length > 0;
  els.emptyState.hidden = hasResults;

  if (hasResults) {
    stations.forEach((station) => {
      els.stationGrid.appendChild(buildStationCard(station));
    });
  }

  const from = pagination.totalResults === 0 ? 0 : (pagination.page - 1) * pagination.limit + 1;
  const to = Math.min(pagination.page * pagination.limit, pagination.totalResults);
  els.resultsCount.textContent = pagination.totalResults === 0
    ? ""
    : `Showing ${from}–${to} of ${pagination.totalResults}`;

  els.pageIndicator.textContent = `Page ${pagination.page} of ${pagination.totalPages}`;
  els.prevPage.disabled = pagination.page <= 1;
  els.nextPage.disabled = pagination.page >= pagination.totalPages;
}

function buildStationCard(station) {
  const node = els.cardTemplate.content.cloneNode(true);
  const card = node.querySelector(".card");

  card.querySelector(".card__name").textContent = station.name;

  const localityEl = card.querySelector(".card__locality");
  localityEl.textContent = station.locality ? station.locality : "Locality not reported";

  const pollutantsList = card.querySelector(".card__pollutants");
  (station.pollutants || []).forEach((p) => {
    const li = document.createElement("li");
    li.textContent = p;
    pollutantsList.appendChild(li);
  });

  const checkBtn = card.querySelector(".btn--check");
  const analysisPanel = card.querySelector(".card__analysis");
  const loadingEl = card.querySelector(".card__analysis-loading");
  const bodyEl = card.querySelector(".card__analysis-body");

  let loaded = false;

  checkBtn.addEventListener("click", async () => {
    const isHidden = analysisPanel.hidden;

    if (!isHidden) {
      analysisPanel.hidden = true;
      checkBtn.textContent = "Check air quality";
      return;
    }

    analysisPanel.hidden = false;
    checkBtn.textContent = "Hide readout";

    if (loaded) return; // Already fetched once, no need to refetch.

    checkBtn.disabled = true;
    loadingEl.hidden = false;

    try {
      const response = await fetch(`/analysis?id=${station.id}`);
      const data = await response.json();
      renderAnalysis(bodyEl, data);
      loaded = true;
    } catch (err) {
      bodyEl.innerHTML = `<p class="analysis-empty">Couldn't load sensor readings right now.</p>`;
    } finally {
      loadingEl.hidden = true;
      checkBtn.disabled = false;
    }
  });

  return node;
}

function renderAnalysis(container, data) {
  const readings = data.analysis || [];

  if (readings.length === 0) {
    container.innerHTML = `<p class="analysis-empty">No comparable pollutant readings available for this station right now.</p>`;
    return;
  }

  // Headline = whichever pollutant is furthest into unhealthy territory.
  const worst = readings.reduce((a, b) => {
    const rankA = STATUS_META[a.status]?.rank ?? 0;
    const rankB = STATUS_META[b.status]?.rank ?? 0;
    return rankB > rankA ? b : a;
  });

  const worstMeta = STATUS_META[worst.status] || { css: "good" };

  const pillHtml = `<span class="status-pill status-pill--${worstMeta.css}">${worst.status}</span>`;

  const rowsHtml = readings
    .map((r) => {
      return `
        <div class="reading">
          <span class="reading__label">${r.pollutant}</span>
          <span class="reading__value">${r.currentValue} <span style="color:var(--muted); font-weight:400;">/ ${r.safeLimit}</span></span>
        </div>
      `;
    })
    .join("");

  container.innerHTML = `
    <div style="margin-bottom:10px;">${pillHtml}</div>
    ${rowsHtml}
    <p class="recommendation">${worst.recommendation}</p>
  `;
}

// =========================================================
// COVERAGE CHART (plain canvas, no chart library)
// =========================================================

function drawCoverageChart(stats) {
  const entries = Object.entries(stats)
    .filter(([key]) => key.endsWith("Stations") && key !== "totalStations")
    .map(([key, value]) => ({
      pollutant: key.replace(/Stations$/, ""),
      count: value,
    }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 10);

  const canvas = els.chartCanvas;
  const ctx = canvas.getContext("2d");

  // Support high-DPI screens without blurring the chart.
  const dpr = window.devicePixelRatio || 1;
  const cssWidth = canvas.clientWidth || 900;
  const cssHeight = 220;
  canvas.width = cssWidth * dpr;
  canvas.height = cssHeight * dpr;
  ctx.scale(dpr, dpr);

  ctx.clearRect(0, 0, cssWidth, cssHeight);

  if (entries.length === 0) return;

  const maxCount = Math.max(...entries.map((e) => e.count));
  const barGap = 14;
  const barWidth = (cssWidth - barGap * (entries.length - 1)) / entries.length;
  const chartFloor = cssHeight - 32;
  const maxBarHeight = chartFloor - 24;

  ctx.font = "600 11px 'IBM Plex Mono', monospace";
  ctx.textAlign = "center";

  entries.forEach((entry, i) => {
    const x = i * (barWidth + barGap);
    const barHeight = maxCount === 0 ? 0 : (entry.count / maxCount) * maxBarHeight;
    const y = chartFloor - barHeight;

    const gradient = ctx.createLinearGradient(0, y, 0, chartFloor);
    gradient.addColorStop(0, "#c8102e");
    gradient.addColorStop(1, "#5c1120");

    ctx.fillStyle = gradient;
    ctx.fillRect(x, y, barWidth, barHeight);

    // Count label above the bar.
    ctx.fillStyle = "#f2ece9";
    ctx.fillText(String(entry.count), x + barWidth / 2, y - 8);

    // Pollutant name below the baseline.
    ctx.fillStyle = "#9c8e89";
    ctx.fillText(entry.pollutant, x + barWidth / 2, chartFloor + 18);
  });
}

window.addEventListener("resize", () => {
  // Redraw on resize so the chart stays crisp at the new width.
  // Cheap to just refetch stats rather than caching them separately.
  loadStats();
});