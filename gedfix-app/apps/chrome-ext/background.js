// GedFix Chrome Extension — Background Service Worker

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
  return false;
});
