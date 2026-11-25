import { PROVIDERS, stream } from "./providers.js";
import { md, esc } from "./markdown.js";

const $ = s => document.querySelector(s);
const uid = () => Math.random().toString(36).slice(2, 9);

const PRESETS = {
  chat: "You are Dai, a helpful and concise AI assistant.",
  code: "You are Dai, an expert software engineer. Reply with complete, working code in fenced code blocks with a language tag, then a short explanation and any edge cases."
};
const DEFAULT_WORKFLOWS = [
  { id: "review", name: "Review, then fix code", steps: ["Review this code. List bugs and risks:\n\n{{input}}", "Rewrite the code and fix these issues. Return the final code.\n\nIssues:\n{{prev}}\n\nCode:\n{{input}}"] },
  { id: "plan", name: "Plan, then build", steps: ["Break this task into a short numbered plan:\n\n{{input}}", "Implement the plan step by step.\n\nPlan:\n{{prev}}"] },
  { id: "tests", name: "Write tests", steps: ["Write thorough unit tests for this code:\n\n{{input}}"] },
  { id: "explain", name: "Explain simply", steps: ["Explain this in plain language with a small example:\n\n{{input}}"] }
];

/* ---------- state ---------- */
const load = (k, d) => { try { const v = localStorage.getItem("dai2:" + k); return v === null ? d : JSON.parse(v); } catch { return d; } };
const S = {
  provider: load("provider", "gemini"), keys: load("keys", {}), models: load("models", {}),
  system: load("system", PRESETS.chat), folders: load("folders", []), chats: load("chats", []),
  current: load("current", null), workflows: load("workflows", DEFAULT_WORKFLOWS)
};
if (!PROVIDERS[S.provider]) S.provider = "gemini";
const save = () => { try { for (const k in S) localStorage.setItem("dai2:" + k, JSON.stringify(S[k])); } catch {} };
const chat = () => S.chats.find(c => c.id === S.current);
let ctl = null, raf = 0, tt = 0, editFolder = null;

/* ---------- rendering ---------- */
function renderSide() {
  const row = c => `<div class="item ${c.id === S.current ? "on" : ""}" data-chat="${c.id}"><span>${esc(c.title)}</span><button data-del="${c.id}" aria-label="Delete chat">×</button></div>`;
  const loose = S.chats.filter(c => !S.folders.some(f => f.id === c.folder));
  $("#list").innerHTML = S.folders.map(f =>
    `<div class="folder"><div class="fhead"><b>${esc(f.name)}</b><span><button data-fnew="${f.id}" aria-label="New chat in folder">+</button><button data-fedit="${f.id}" aria-label="Edit folder">✎</button></span></div>${S.chats.filter(c => c.folder === f.id).map(row).join("")}</div>`
  ).join("") + loose.map(row).join("");
}
function renderTools() {
  const c = chat();
  $("#title").textContent = c.title;
  $("#move").innerHTML = '<option value="">No folder</option>' + S.folders.map(f => `<option value="${f.id}">${esc(f.name)}</option>`).join("");
  $("#move").value = c.folder || "";
}
function render() {
  const el = $("#chat"), c = chat();
  if (!c.messages.length) {
    el.innerHTML = '<div class="empty"><h1>Dai</h1><p>Chat or code with Gemini. Add a key in Settings to start.</p></div>';
    return;
  }
  const near = el.scrollHeight - el.scrollTop - el.clientHeight < 90;
  el.innerHTML = c.messages.map(m =>
    `<div class="msg ${m.role}"><div class="bubble">${m.role === "user" ? esc(m.content).replace(/\n/g, "<br>") : md(m.content) || "…"}${m.err ? `<div class="err">${esc(m.err)}</div>` : ""}</div></div>`
  ).join("");
  if (near) el.scrollTop = el.scrollHeight;
}
const renderAll = () => { renderSide(); renderTools(); render(); };
const schedule = () => { if (!raf) raf = requestAnimationFrame(() => { raf = 0; render(); }); };
function toast(t) {
  const el = $("#toast"); el.textContent = t; el.classList.add("on");
  clearTimeout(tt); tt = setTimeout(() => el.classList.remove("on"), 2800);
}
const setBusy = b => { $("#send").textContent = b ? "■" : "↑"; $("#send").setAttribute("aria-label", b ? "Stop" : "Send"); };
const autosize = () => { const i = $("#input"); i.style.height = "auto"; i.style.height = Math.min(i.scrollHeight, 160) + "px"; };
const closeSide = () => $("#side").classList.remove("open");

/* ---------- chats ---------- */
function newChat(folder = null) {
  if (ctl) ctl.abort();
  const c = { id: uid(), title: "New chat", folder, messages: [] };
  S.chats.unshift(c); S.current = c.id; save(); renderAll(); closeSide();
}
function selectChat(id) { if (ctl) ctl.abort(); S.current = id; save(); renderAll(); closeSide(); }

/** Sends one prompt in the current chat. Returns the reply, "" if stopped or failed, null if nothing was kept. */
async function ask(text) {
  const p = S.provider, key = (S.keys[p] || "").trim();
  if (!key) { openSettings(); toast("Add your " + PROVIDERS[p].label + " API key"); return null; }
  const c = chat(), folder = S.folders.find(f => f.id === c.folder);
  c.messages.push({ role: "user", content: text }, { role: "assistant", content: "" });
  const reply = c.messages[c.messages.length - 1];
  if (c.title === "New chat") c.title = text.replace(/\s+/g, " ").slice(0, 40);
  renderAll(); setBusy(true);
  const mine = (ctl = new AbortController());
  let failed = "";
  try {
    await stream(p, {
      key, model: S.models[p] || PROVIDERS[p].model, system: folder?.system || S.system,
      messages: c.messages.slice(0, -1), signal: mine.signal
    }, t => { reply.content += t; schedule(); });
  } catch (e) {
    if (e.name !== "AbortError") failed = e.name === "TypeError" ? "Network error. Check your connection and API key." : e.message;
  }
  const stopped = mine.signal.aborted;
  if (ctl === mine) { ctl = null; setBusy(false); }
  let result = stopped || failed ? "" : reply.content;
  if (!reply.content) {
    c.messages.splice(-2, 2); result = null;
    if (failed) toast(failed);
  } else if (failed) reply.err = failed;
  save(); renderAll();
  return result;
}

async function send() {
  if (ctl) { ctl.abort(); return; }
  const i = $("#input"), text = i.value.trim();
  if (!text) return;
  i.value = ""; autosize();
  if ((await ask(text)) === null && !i.value) { i.value = text; autosize(); }
}

async function runWorkflow(wf) {
  if (ctl) return;
  const i = $("#input"), input = i.value.trim();
  if (!input && wf.steps.some(s => s.includes("{{input}}"))) { toast("Type or paste your input first"); return; }
  $("#wdlg").close(); i.value = ""; autosize();
  let prev = "";
  for (const s of wf.steps) {
    const out = await ask(s.replaceAll("{{input}}", () => input).replaceAll("{{prev}}", () => prev));
    if (out === null && !i.value) { i.value = input; autosize(); }
    if (!out) break;
    prev = out;
  }
}

/* ---------- dialogs ---------- */
function openSettings() {
  for (const p in PROVIDERS) { $("#k-" + p).value = S.keys[p] || ""; $("#m-" + p).value = S.models[p] || PROVIDERS[p].model; }
  $("#sys").value = S.system;
  $("#dlg").showModal();
}
function openFolder(id) {
  editFolder = id;
  const f = S.folders.find(f => f.id === id);
  $("#fname").value = f?.name || ""; $("#fsys").value = f?.system || ""; $("#fdel").hidden = !f;
  $("#fdlg").showModal();
}
function renderWorkflows() {
  $("#wflist").innerHTML = S.workflows.map(w =>
    `<div class="item"><span>${esc(w.name)} <small>${w.steps.length} step${w.steps.length > 1 ? "s" : ""}</small></span><span><button class="ghost" data-run="${w.id}">Run</button><button data-wdel="${w.id}" aria-label="Delete workflow">×</button></span></div>`
  ).join("");
}

$("#fields").innerHTML = Object.entries(PROVIDERS).map(([p, v]) =>
  `<label>${v.label} API key<input type="password" id="k-${p}" autocomplete="off" spellcheck="false"></label><label>${v.label} model<input id="m-${p}" spellcheck="false"></label>`
).join("");
$("#provider").innerHTML = Object.entries(PROVIDERS).map(([p, v]) => `<option value="${p}">${v.label}</option>`).join("");
$("#provider").value = S.provider;

/* ---------- events ---------- */
document.addEventListener("click", e => { if (e.target.dataset?.close !== undefined) e.target.closest("dialog").close(); });
$("#menu").onclick = () => $("#side").classList.toggle("open");
$("#newChat").onclick = () => newChat();
$("#newFolder").onclick = () => openFolder(null);
$("#setBtn").onclick = () => { closeSide(); openSettings(); };
$("#wfBtn").onclick = () => { closeSide(); renderWorkflows(); $("#wdlg").showModal(); };
$("#send").onclick = send;
$("#pChat").onclick = () => { $("#sys").value = PRESETS.chat; };
$("#pCode").onclick = () => { $("#sys").value = PRESETS.code; };
$("#saveBtn").onclick = () => {
  for (const p in PROVIDERS) { S.keys[p] = $("#k-" + p).value.trim(); S.models[p] = $("#m-" + p).value.trim() || PROVIDERS[p].model; }
  S.system = $("#sys").value.trim(); save(); $("#dlg").close(); toast("Settings saved");
};
$("#provider").onchange = e => { S.provider = e.target.value; save(); updatePlaceholder(); };
$("#move").onchange = e => { chat().folder = e.target.value || null; save(); renderAll(); };

$("#list").onclick = e => {
  const d = e.target.dataset;
  if (d.del) {
    if (!confirm("Delete this chat?")) return;
    S.chats = S.chats.filter(c => c.id !== d.del);
    if (!chat()) { if (!S.chats.length) return newChat(); S.current = S.chats[0].id; }
    save(); renderAll();
  } else if (d.fnew) newChat(d.fnew);
  else if (d.fedit) openFolder(d.fedit);
  else { const r = e.target.closest("[data-chat]"); if (r) selectChat(r.dataset.chat); }
};
$("#fsave").onclick = () => {
  const name = $("#fname").value.trim();
  if (!name) { toast("Name the folder"); return; }
  let f = S.folders.find(f => f.id === editFolder);
  if (!f) { f = { id: uid() }; S.folders.push(f); }
  f.name = name; f.system = $("#fsys").value.trim();
  save(); $("#fdlg").close(); renderAll();
};
$("#fdel").onclick = () => {
  if (!confirm("Delete this folder? Its chats stay and move out of the folder.")) return;
  S.folders = S.folders.filter(f => f.id !== editFolder);
  S.chats.forEach(c => { if (c.folder === editFolder) c.folder = null; });
  save(); $("#fdlg").close(); renderAll();
};
$("#wflist").onclick = e => {
  const d = e.target.dataset;
  if (d.run) runWorkflow(S.workflows.find(w => w.id === d.run));
  else if (d.wdel) { S.workflows = S.workflows.filter(w => w.id !== d.wdel); save(); renderWorkflows(); }
};
$("#wsave").onclick = () => {
  const name = $("#wname").value.trim(), steps = $("#wsteps").value.split(/\n-{3,}\n/).map(s => s.trim()).filter(Boolean);
  if (!name || !steps.length) { toast("Add a name and at least one step"); return; }
  S.workflows.push({ id: uid(), name, steps }); save();
  $("#wname").value = ""; $("#wsteps").value = ""; renderWorkflows();
};
$("#export").onclick = () => {
  const c = chat();
  const text = `# ${c.title}\n\n` + c.messages.map(m => `**${m.role === "user" ? "You" : "Dai"}**\n\n${m.content}`).join("\n\n---\n\n");
  const a = document.createElement("a");
  a.href = URL.createObjectURL(new Blob([text], { type: "text/markdown" }));
  a.download = (c.title.replace(/[^\w-]+/g, "_") || "chat") + ".md";
  a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 1000);
};
$("#input").addEventListener("input", autosize);
$("#input").addEventListener("keydown", e => {
  if (e.key === "Enter" && !e.shiftKey && !e.isComposing && !matchMedia("(pointer:coarse)").matches) { e.preventDefault(); send(); }
});
$("#chat").addEventListener("click", async e => {
  closeSide();
  if (!e.target.classList.contains("copy")) return;
  try { await navigator.clipboard.writeText(e.target.closest(".code").querySelector("code").textContent); e.target.textContent = "Copied"; }
  catch { e.target.textContent = "Copy failed"; }
  setTimeout(() => { e.target.textContent = "Copy"; }, 1500);
});
function updatePlaceholder() { $("#input").placeholder = "Message " + PROVIDERS[S.provider].label + "…"; }

updatePlaceholder();
if (chat()) renderAll(); else newChat();
$("#chat").scrollTop = $("#chat").scrollHeight;
