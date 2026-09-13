import http from "node:http";
import { spawn } from "node:child_process";
import { Client } from "discord.js-selfbot-v13";
import {
  Streamer,
  prepareStream,
  playStream,
  Encoders,
  Utils,
} from "@dank074/discord-video-stream";

const PORT = Number(process.env.LIVESTREAM_SIDECAR_PORT || 8790);
const SECRET = process.env.LIVESTREAM_SIDECAR_SECRET || "";
const TOKEN = process.env.LIVESTREAM_USER_TOKEN || "";
const FFMPEG = process.env.LIVESTREAM_FFMPEG_PATH || "ffmpeg";
const HOST = "127.0.0.1";

const streamer = new Streamer(new Client());
const encoder = Encoders.software({
  x264: { preset: "superfast" },
  x265: { preset: "superfast" },
});

let ready = false;
let loginError = null;
let state = { status: "idle" };

class SidecarError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
  }
}

function json(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  res.end(payload);
}

function authorized(req) {
  return SECRET.length > 0 && req.headers.authorization === `Bearer ${SECRET}`;
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = "";
    req.on("data", (chunk) => {
      data += chunk;
      if (data.length > 1_000_000) reject(new Error("corpo grande demais"));
    });
    req.on("end", () => {
      try {
        resolve(data ? JSON.parse(data) : {});
      } catch (error) {
        reject(new Error("JSON inválido"));
      }
    });
    req.on("error", reject);
  });
}

function gdigrabInput(window) {
  return window.toLowerCase() === "desktop" ? "desktop" : `title=${window}`;
}

function leaveVoiceQuietly() {
  try {
    streamer.leaveVoice();
  } catch {
    // já desconectado
  }
}

function probeWindow(input) {
  if (input === "desktop") {
    return Promise.resolve();
  }
  return new Promise((resolve, reject) => {
    const probe = spawn(
      FFMPEG,
      [
        "-hide_banner", "-loglevel", "error",
        "-f", "gdigrab", "-framerate", "1",
        "-i", input, "-frames:v", "1", "-f", "null", "-",
      ],
      { stdio: ["ignore", "ignore", "pipe"] },
    );
    let stderr = "";
    probe.stderr.on("data", (chunk) => {
      stderr += chunk.toString();
    });
    probe.on("error", (error) => reject(new SidecarError("CAPTURE_FAILED", error.message)));
    probe.on("close", (code) => {
      if (code === 0) {
        resolve();
        return;
      }
      const message = stderr.trim() || `ffmpeg saiu com código ${code}`;
      const missing = /find window|window.*not found|cannot find/i.test(message);
      reject(new SidecarError(missing ? "WINDOW_NOT_FOUND" : "CAPTURE_FAILED", message));
    });
  });
}

async function startStream({ guildId, channelId, window }) {
  const target = String(window ?? "").trim();
  if (!target) {
    throw new SidecarError("CAPTURE_FAILED", "janela vazia");
  }
  await stopStream();
  const input = gdigrabInput(target);
  await probeWindow(input);

  const session = { status: "starting", guildId, channelId, window: target };
  state = session;
  try {
    await streamer.joinVoice(guildId, channelId);
  } catch (error) {
    leaveVoiceQuietly();
    state = { status: "idle" };
    throw new SidecarError("TRANSMITTER_UNAVAILABLE", error?.message ?? String(error));
  }

  try {
    const controller = new AbortController();
    const { output } = prepareStream(
      input,
      {
        encoder,
        customInputOptions: ["-f", "gdigrab", "-framerate", "30"],
        includeAudio: false,
        width: -2,
        height: 720,
        frameRate: 30,
        bitrateVideo: 3000,
        bitrateVideoMax: 4500,
        videoCodec: Utils.normalizeVideoCodec("H264"),
        logLevel: "warning",
      },
      controller.signal,
    );
    const streaming = { status: "streaming", guildId, channelId, window: target, controller };
    state = streaming;
    playStream(output, streamer, { type: "go-live" }, controller.signal)
      .catch(() => {})
      .finally(() => {
        if (state === streaming) {
          leaveVoiceQuietly();
          state = { status: "idle" };
        }
      });
    return { status: "streaming" };
  } catch (error) {
    leaveVoiceQuietly();
    state = { status: "idle" };
    throw new SidecarError("CAPTURE_FAILED", error?.message ?? String(error));
  }
}

async function stopStream() {
  const current = state;
  if (current.status !== "streaming" && current.status !== "starting") {
    return { status: "idle", wasActive: false };
  }
  const wasActive = current.status === "streaming";
  state = { status: "stopping" };
  if (current.controller) {
    try {
      current.controller.abort();
    } catch {
      // ignora
    }
  }
  leaveVoiceQuietly();
  state = { status: "idle" };
  return { status: "idle", wasActive };
}

async function handle(req, res) {
  const url = new URL(req.url, `http://${HOST}:${PORT}`);
  if (req.method === "GET" && url.pathname === "/health") {
    if (ready) {
      json(res, 200, { status: "ready" });
    } else {
      json(res, 503, { status: "error", detail: loginError ?? "iniciando" });
    }
    return;
  }
  if (!authorized(req)) {
    json(res, 401, { reason: "não autorizado" });
    return;
  }
  if (req.method === "POST" && url.pathname === "/start") {
    const body = await readBody(req);
    if (!ready) {
      json(res, loginError ? 400 : 503, {
        code: loginError ? "TRANSMITTER_UNAVAILABLE" : "CAPTURE_FAILED",
        reason: loginError ?? "sidecar iniciando",
      });
      return;
    }
    if (!body.guildId || !body.channelId || !body.window) {
      json(res, 400, { code: "CAPTURE_FAILED", reason: "guildId, channelId e window são obrigatórios" });
      return;
    }
    try {
      json(res, 200, await startStream(body));
    } catch (error) {
      const code = error instanceof SidecarError ? error.code : "CAPTURE_FAILED";
      json(res, 400, { code, reason: error?.message ?? String(error) });
    }
    return;
  }
  if (req.method === "POST" && url.pathname === "/stop") {
    await readBody(req);
    json(res, 200, await stopStream());
    return;
  }
  json(res, 404, { reason: "rota desconhecida" });
}

const server = http.createServer((req, res) => {
  handle(req, res).catch((error) => {
    if (!res.headersSent) {
      json(res, 500, { reason: error?.message ?? "erro interno" });
    } else {
      res.end();
    }
  });
});

async function boot() {
  if (!TOKEN) {
    loginError = "LIVESTREAM_USER_TOKEN ausente";
    console.error(`[sidecar] ${loginError}`);
    return;
  }
  try {
    await streamer.client.login(TOKEN);
    ready = true;
    console.log("[sidecar] conta de transmissão conectada.");
  } catch (error) {
    loginError = error?.message ?? String(error);
    console.error("[sidecar] falha ao conectar a conta de transmissão:", loginError);
  }
}

async function shutdown() {
  try {
    await stopStream();
  } catch {
    // ignora
  }
  try {
    streamer.client.destroy();
  } catch {
    // ignora
  }
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 2000).unref();
}

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);

server.listen(PORT, HOST, () => {
  console.log(`[sidecar] ouvindo em ${HOST}:${PORT}`);
  boot();
});
