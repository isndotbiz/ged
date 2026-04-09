// GedFix Chrome Extension — Background Service Worker
// Bridges to desktop app via localhost:19876 when available, otherwise web-only mode

const BRIDGE_URL = 'http://127.0.0.1:19876';
let desktopAvailable = false;

// Check if desktop app is running
async function probeDesktopBridge() {
  try {
    const resp = await fetch(`${BRIDGE_URL}/ping`, { signal: AbortSignal.timeout(1000) });
    const data = await resp.json();
    desktopAvailable = data?.app === 'gedfix';
  } catch {
    desktopAvailable = false;
  }
}

async function updateBadge() {
  const { gedfix_pending_records } = await chrome.storage.local.get('gedfix_pending_records');
  const pending = Array.isArray(gedfix_pending_records) ? gedfix_pending_records : [];
  if (pending.length > 0) {
    chrome.action.setBadgeText({ text: String(pending.length) });
    chrome.action.setBadgeBackgroundColor({ color: '#E57C00' });
  } else {
    chrome.action.setBadgeText({ text: '' });
  }
}

async function flushPendingQueue() {
  const { gedfix_pending_records } = await chrome.storage.local.get('gedfix_pending_records');
  const pending = Array.isArray(gedfix_pending_records) ? gedfix_pending_records : [];
  if (pending.length === 0) return;
  const remaining = [];
  for (const record of pending) {
    try {
      const resp = await fetch(`${BRIDGE_URL}/import`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(record),
        signal: AbortSignal.timeout(3000),
      });
      if (!resp.ok) remaining.push(record);
    } catch {
      remaining.push(record);
      break; // If bridge is gone, stop trying
    }
  }
  await chrome.storage.local.set({ gedfix_pending_records: remaining });
  await updateBadge();
}

async function checkDesktopBridge() {
  const wasBefore = desktopAvailable;
  await probeDesktopBridge();
  // If we just connected and had a queue, flush it
  if (!wasBefore && desktopAvailable) {
    await flushPendingQueue();
  }
  await updateBadge();
  // Re-check every 30 seconds
  setTimeout(checkDesktopBridge, 30000);
}

// Send record to desktop app or queue locally
async function sendToApp(record) {
  if (desktopAvailable) {
    try {
      const resp = await fetch(`${BRIDGE_URL}/import`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(record),
        signal: AbortSignal.timeout(3000),
      });
      const data = await resp.json();
      return { sent: true, target: 'desktop', ...data };
    } catch {
      desktopAvailable = false;
    }
  }
  // Fallback: queue locally for retry when desktop reconnects
  const { gedfix_pending_records } = await chrome.storage.local.get('gedfix_pending_records');
  const pending = Array.isArray(gedfix_pending_records) ? gedfix_pending_records : [];
  pending.push(record);
  await chrome.storage.local.set({ gedfix_pending_records: pending });
  await updateBadge();
  return { sent: false, target: 'local', queued: true };
}

// Context menus
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: 'gedfix-lookup',
    title: 'Look up "%s" in GedFix',
    contexts: ['selection']
  });
  chrome.contextMenus.create({
    id: 'gedfix-add-person',
    title: 'Add to GedFix tree',
    contexts: ['selection']
  });
  checkDesktopBridge();
});

chrome.contextMenus.onClicked.addListener(async (info, tab) => {
  if (!tab?.id) return;

  if (info.menuItemId === 'gedfix-lookup') {
    await chrome.sidePanel.open({ tabId: tab.id });
    setTimeout(() => {
      chrome.runtime.sendMessage({
        type: 'gedfix:search',
        query: info.selectionText
      });
    }, 500);
  }

  if (info.menuItemId === 'gedfix-add-person') {
    await chrome.sidePanel.open({ tabId: tab.id });
    setTimeout(() => {
      chrome.runtime.sendMessage({
        type: 'gedfix:add-person',
        name: info.selectionText
      });
    }, 500);
  }
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message?.type === 'gedfix:open-sidepanel') {
    chrome.sidePanel.open({ tabId: sender.tab?.id }).then(() => {
      if (message.query) {
        setTimeout(() => {
          chrome.runtime.sendMessage({ type: 'gedfix:search', query: message.query });
        }, 500);
      }
    });
    sendResponse({ ok: true });
  }

  // Forward extracted records to desktop app
  if (message?.type === 'gedfix:extracted-record' && message.record) {
    sendToApp(message.record).then((result) => {
      // Also forward to side panel regardless
      chrome.runtime.sendMessage({
        type: 'gedfix:extracted-record',
        record: message.record,
        bridgeResult: result
      });
    });
  }

  // Status check from side panel
  if (message?.type === 'gedfix:check-bridge') {
    probeDesktopBridge().then(() => {
      sendResponse({ desktopAvailable });
    });
    return true; // async response
  }

  if (message?.type === 'gedfix:get-bridge-stats') {
    (async () => {
      await probeDesktopBridge();
      if (!desktopAvailable) {
        sendResponse({ ok: false, desktopAvailable: false });
        return;
      }
      try {
        const resp = await fetch(`${BRIDGE_URL}/stats`, { signal: AbortSignal.timeout(1500) });
        if (!resp.ok) {
          sendResponse({ ok: false, desktopAvailable: true });
          return;
        }
        const data = await resp.json();
        sendResponse({ ok: true, desktopAvailable: true, stats: data });
      } catch {
        sendResponse({ ok: false, desktopAvailable: true });
      }
    })();
    return true; // async response
  }

  return false;
});

// Initial bridge check on startup
checkDesktopBridge();
