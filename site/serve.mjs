// Serveur local du site Orbit MC : node serve.mjs  →  http://localhost:5180
//   /                     → index.html (le site, routes en #hash)
//   /Charte-Orbit-MC.pdf  → la charte en PDF
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";

const fichiers = {
  "/": ["./index.html", "text/html; charset=utf-8"],
  "/index.html": ["./index.html", "text/html; charset=utf-8"],
  "/Charte-Orbit-MC.pdf": ["./Charte-Orbit-MC.pdf", "application/pdf"],
};

createServer(async (req, res) => {
  const { pathname } = new URL(req.url, "http://x");
  const f = fichiers[pathname];
  if (!f) { res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" }); return res.end("Introuvable"); }
  try {
    const buf = await readFile(new URL(f[0], import.meta.url));
    res.writeHead(200, { "Content-Type": f[1], "Cache-Control": "no-store" });
    res.end(buf);
  } catch {
    res.writeHead(500); res.end();
  }
}).listen(5180, () => console.log("Orbit MC : http://localhost:5180"));
