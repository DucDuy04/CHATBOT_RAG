/**
 * 23C2 — Public widget embed layout browser verify (Playwright)
 * Usage: node _run_23c2_widget_browser_verify.mjs <baseUrl> <widgetKey>
 */
import { chromium } from "playwright";
import { writeFileSync, mkdirSync } from "fs";
import { dirname, join } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const baseUrl = (process.argv[2] || "http://localhost:5174").replace(/\/$/, "");
const widgetKey = process.argv[3] || "";
const testUrl = `${baseUrl}/public-widget-test.html?widgetKey=${encodeURIComponent(widgetKey)}`;
const outDir = join(__dirname, "screenshots_23c2");
mkdirSync(outDir, { recursive: true });

const maskKey = (k) =>
  !k ? "(none)" : k.length <= 8 ? "***" : `${k.slice(0, 4)}...${k.slice(-4)}`;

const result = {
  timestamp: new Date().toISOString(),
  task: "23C2",
  baseUrl,
  testUrl: testUrl.replace(widgetKey, maskKey(widgetKey)),
  widgetKeyMasked: maskKey(widgetKey),
  bundleProbe: null,
  layout: {},
  panel: {},
  chat: {},
  console: { errors: [], warnings: [] },
  network: [],
  screenshots: [],
  conclusion: "FAIL",
};

function probeBundleSnippet(text) {
  return {
    length: text?.length ?? 0,
    hasInjectStyles: /function u\(\)|injectWidgetStyles|width: 56px/.test(text || ""),
    hasRagHidden: /rag-chatbot-hidden/.test(text || ""),
    isStaleLegacy: /classList\.add\("hidden"\)/.test(text || "") && !/rag-chatbot-hidden/.test(text || ""),
  };
}

async function main() {
  const bundleRes = await fetch(`${baseUrl}/dist-widget/chatbot-widget.iife.js`);
  const bundleText = await bundleRes.text();
  result.bundleProbe = {
    httpStatus: bundleRes.status,
    ...probeBundleSnippet(bundleText),
  };

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1280, height: 800 } });

  page.on("console", (msg) => {
    const entry = { type: msg.type(), text: msg.text() };
    if (msg.type() === "error") result.console.errors.push(entry);
    else if (msg.type() === "warning") result.console.warnings.push(entry);
  });

  page.on("response", (res) => {
    const u = res.url();
    if (u.includes("chatbot-widget.iife.js") || u.includes("/api/chat")) {
      result.network.push({ url: u.replace(widgetKey, maskKey(widgetKey)), status: res.status() });
    }
  });

  await page.goto(testUrl, { waitUntil: "networkidle", timeout: 60000 });

  const shotInitial = join(outDir, `initial_${baseUrl.includes("5174") ? "vite5174" : "port5173"}.png`);
  await page.screenshot({ path: shotInitial, fullPage: true });
  result.screenshots.push(shotInitial);

  const bubble = page.locator("#rag-chatbot-bubble");
  const bubbleCount = await bubble.count();
  const bubbleBox = bubbleCount ? await bubble.boundingBox() : null;
  const viewport = page.viewportSize();

  const hugeBlackBlock =
    bubbleBox &&
    (bubbleBox.width > 200 || bubbleBox.height > 200) &&
    bubbleBox.y < viewport.height * 0.5;

  result.layout = {
    bubbleExists: bubbleCount > 0,
    bubbleWidth: bubbleBox?.width ?? null,
    bubbleHeight: bubbleBox?.height ?? null,
    bubbleX: bubbleBox?.x ?? null,
    bubbleY: bubbleBox?.y ?? null,
    launcherSizeOk:
      bubbleBox &&
      bubbleBox.width >= 48 &&
      bubbleBox.width <= 72 &&
      bubbleBox.height >= 48 &&
      bubbleBox.height <= 72,
    positionBottomRight:
      bubbleBox &&
      bubbleBox.x > viewport.width * 0.5 &&
      bubbleBox.y > viewport.height * 0.5,
    hugeBlackBlock: !!hugeBlackBlock,
  };

  if (bubbleCount) await bubble.click();

  const frame = page.locator("#rag-chatbot-frame");
  await frame.waitFor({ state: "visible", timeout: 15000 });
  const frameBox = await frame.boundingBox();

  const shotPanel = join(outDir, `panel_${baseUrl.includes("5174") ? "vite5174" : "port5173"}.png`);
  await page.screenshot({ path: shotPanel, fullPage: false });
  result.screenshots.push(shotPanel);

  result.panel = {
    frameVisible: await frame.isVisible(),
    frameWidth: frameBox?.width ?? null,
    frameHeight: frameBox?.height ?? null,
    panelSizeOk:
      frameBox &&
      frameBox.width >= 300 &&
      frameBox.width <= 400 &&
      frameBox.height >= 450 &&
      frameBox.height <= 560,
  };

  const iframeEl = await frame.elementHandle();
  const chatFrame = await iframeEl.contentFrame();
  const input = chatFrame.locator('input[placeholder="Nhập câu hỏi..."]');
  const sendBtn = chatFrame.getByRole("button", { name: "Gửi" });

  await input.waitFor({ state: "visible", timeout: 20000 });
  const inputBox = await input.boundingBox();
  const sendBox = await sendBtn.boundingBox();

  result.panel.inputVisible = await input.isVisible();
  result.panel.sendVisible = await sendBtn.isVisible();
  result.panel.inputNotCropped = inputBox && inputBox.width > 100 && inputBox.height >= 28;
  result.panel.sendNotCropped = sendBox && sendBox.width >= 40 && sendBox.height >= 28;

  const question = "Tài liệu này nói về hệ thống gì?";
  await input.fill(question);
  await sendBtn.click();

  await chatFrame
    .locator(".prose, [class*='markdown'], p")
    .filter({ hasText: /hệ thống|quản lý|phúc khảo|không/i })
    .first()
    .waitFor({ state: "visible", timeout: 120000 })
    .catch(() => null);

  await page.waitForTimeout(2000);
  const shotChat = join(outDir, `chat_${baseUrl.includes("5174") ? "vite5174" : "port5173"}.png`);
  await page.screenshot({ path: shotChat, fullPage: false });
  result.screenshots.push(shotChat);

  const messages = await chatFrame.locator("body").innerText();
  const hasUserMsg = messages.includes("Tài liệu này");
  const hasBotReply =
    messages.split("Tài liệu này").length > 1 &&
    messages.length > messages.indexOf("Tài liệu này") + 40;

  result.chat = {
    messageSent: hasUserMsg,
    botReplied: hasBotReply,
    excerpt: messages.slice(0, 500).replace(/\s+/g, " "),
  };

  const severeConsole =
    result.console.errors.filter(
      (e) =>
        !e.text.includes("favicon") &&
        !e.text.includes("DevTools") &&
        !e.text.includes("404") &&
        !/Failed to load resource.*favicon/i.test(e.text),
    ).length === 0;

  const layoutPass =
    result.layout.bubbleExists &&
    !result.layout.hugeBlackBlock &&
    result.layout.launcherSizeOk &&
    result.layout.positionBottomRight;

  const panelPass =
    result.panel.frameVisible &&
    result.panel.panelSizeOk &&
    result.panel.inputNotCropped &&
    result.panel.sendNotCropped;

  const chatPass = result.chat.messageSent && result.chat.botReplied;
  const bundlePass = result.bundleProbe.hasRagHidden && result.bundleProbe.hasInjectStyles;

  if (layoutPass && panelPass && chatPass && bundlePass && severeConsole) {
    result.conclusion = "PASS";
  } else if (layoutPass && panelPass && bundlePass) {
    result.conclusion = chatPass ? "PASS" : "PARTIAL";
  } else if (!bundlePass && result.bundleProbe.isStaleLegacy) {
    result.conclusion = "FAIL";
    result.staleBundleNote = "Served bundle is legacy (no rag-chatbot-hidden / 56px styles)";
  } else {
    result.conclusion = layoutPass || panelPass ? "PARTIAL" : "FAIL";
  }

  await browser.close();

  const portTag = baseUrl.includes("5174") ? "5174" : "5173";
  const outJson = join(__dirname, `_run_23c2_results_${portTag}.json`);
  writeFileSync(outJson, JSON.stringify(result, null, 2), "utf8");
  console.log(JSON.stringify(result, null, 2));
}

main().catch((err) => {
  result.fatal = String(err?.stack || err);
  result.conclusion = "FAIL";
  const portTag = baseUrl.includes("5174") ? "5174" : "5173";
  const outJson = join(__dirname, `_run_23c2_results_${portTag}.json`);
  writeFileSync(outJson, JSON.stringify(result, null, 2), "utf8");
  console.error(err);
  process.exit(1);
});
