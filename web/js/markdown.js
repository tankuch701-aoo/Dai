export const esc = s => s.replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

export function md(text) {
  return text.split(/(```[\s\S]*?(?:```|$))/g).map(p => {
    if (p.startsWith("```")) {
      const m = p.match(/^```([\w+#-]*)\n?([\s\S]*?)(?:```)?$/);
      return `<div class="code"><div class="bar"><span>${esc(m[1] || "code")}</span><button class="copy">Copy</button></div><pre><code>${esc(m[2].replace(/\n$/, ""))}</code></pre></div>`;
    }
    return esc(p)
      .replace(/^#{1,3} (.+)$/gm, "<b>$1</b>")
      .replace(/^[-*] /gm, "• ")
      .replace(/`([^`\n]+)`/g, "<code>$1</code>")
      .replace(/\*\*([^*\n]+)\*\*/g, "<b>$1</b>")
      .replace(/\n/g, "<br>");
  }).join("");
}
