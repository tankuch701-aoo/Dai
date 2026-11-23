export const PROVIDERS = {
  gemini: { label: "Gemini", model: "gemini-2.5-flash" },
  openai: { label: "ChatGPT", model: "gpt-4o-mini" },
  claude: { label: "Claude", model: "claude-sonnet-5-5" }
};

const JSON_H = { "Content-Type": "application/json" };

const BUILD = {
  gemini: (key, model, system, msgs) => ({
    url: `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:streamGenerateContent?alt=sse`,
    headers: { ...JSON_H, "x-goog-api-key": key },
    body: {
      ...(system ? { systemInstruction: { parts: [{ text: system }] } } : {}),
      contents: msgs.map(m => ({ role: m.role === "user" ? "user" : "model", parts: [{ text: m.content }] }))
    },
    pick: j => (j.candidates?.[0]?.content?.parts || []).map(p => p.text || "").join("")
  }),
  openai: (key, model, system, msgs) => ({
    url: "https://api.openai.com/v1/chat/completions",
    headers: { ...JSON_H, Authorization: "Bearer " + key },
    body: {
      model, stream: true,
      messages: [...(system ? [{ role: "system", content: system }] : []), ...msgs.map(m => ({ role: m.role, content: m.content }))]
    },
    pick: j => j.choices?.[0]?.delta?.content || ""
  }),
  claude: (key, model, system, msgs) => ({
    url: "https://api.anthropic.com/v1/messages",
    headers: { ...JSON_H, "x-api-key": key, "anthropic-version": "2023-06-01", "anthropic-dangerous-direct-browser-access": "true" },
    body: {
      model, max_tokens: 4096, stream: true,
      ...(system ? { system } : {}),
      messages: msgs.map(m => ({ role: m.role, content: m.content }))
    },
    pick: j => (j.type === "content_block_delta" ? j.delta?.text || "" : "")
  })
};

/** Streams a reply; calls onDelta(text) for each chunk. Throws on HTTP or stream errors. */
export async function stream(provider, { key, model, system, messages, signal }, onDelta) {
  const r = BUILD[provider](key, model, system, messages);
  const res = await fetch(r.url, { method: "POST", signal, headers: r.headers, body: JSON.stringify(r.body) });
  if (!res.ok) {
    const t = await res.text();
    let msg = t;
    try { const j = JSON.parse(t); msg = (Array.isArray(j) ? j[0] : j).error?.message || t; } catch {}
    throw new Error(res.status + ": " + msg.slice(0, 300));
  }
  const reader = res.body.getReader(), dec = new TextDecoder();
  let buf = "";
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buf += dec.decode(value, { stream: true });
    let i;
    while ((i = buf.indexOf("\n")) >= 0) {
      const line = buf.slice(0, i).trim();
      buf = buf.slice(i + 1);
      if (!line.startsWith("data:")) continue;
      const d = line.slice(5).trim();
      if (!d || d === "[DONE]") continue;
      let j;
      try { j = JSON.parse(d); } catch { continue; }
      if (j.error) throw new Error(j.error.message || "Stream error");
      const t = r.pick(j);
      if (t) onDelta(t);
    }
  }
}
