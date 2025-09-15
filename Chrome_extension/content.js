// Content script for LeetCode activity tracking
console.log('LeetCode Tracker content script loaded');

class LeetCodeTracker {
  constructor() {
    this.isTracking = false;
    this.observers = [];
    console.log('LeetCodeTracker constructor called');
    this.init();
  }

  async init() {
    console.log('LeetCode Tracker initializing...');
    console.log('Current URL:', window.location.href);
    console.log('Document ready state:', document.readyState);
    
    // Check if we're on a problem page
    if (!window.location.pathname.includes('/problems/')) {
      console.log('Not on a problems page, exiting');
      return;
    }
    
    // Wait for page to fully load
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', () => this.setupTracking());
    } else {
      this.setupTracking();
    }
  }

  async setupTracking() {
    console.log('Setting up tracking...');
    
    // Check if tracking is enabled
    const settings = await this.getSettings();
    console.log('Settings:', settings);
    
    if (!settings.trackingEnabled) {
      console.log('Tracking disabled, exiting');
      return;
    }

    this.isTracking = true;
    console.log('Tracking enabled, starting observers');
    
    // Start observing immediately and with delay
    this.startObserving();
    
    // Try multiple times with delays
    setTimeout(() => this.attachButtonListeners(), 1000);
    setTimeout(() => this.attachButtonListeners(), 3000);
    setTimeout(() => this.attachButtonListeners(), 5000);
    
    this.injectMonacoHelper();
  }

  async getSettings() {
    return new Promise((resolve) => {
      if (typeof chrome !== 'undefined' && chrome.storage) {
        chrome.storage.sync.get(['trackingEnabled', 'backendUrl'], (result) => {
          resolve({
            trackingEnabled: result.trackingEnabled ?? true,
            backendUrl: result.backendUrl ?? 'http://localhost:3000/api/activity'
          });
        });
      } else {
        console.warn('Chrome storage not available');
        resolve({
          trackingEnabled: true,
          backendUrl: 'http://localhost:3000/api/activity'
        });
      }
    });
  }

  startObserving() {
    // Use MutationObserver to watch for dynamically loaded content
    const observer = new MutationObserver((mutations) => {
      let shouldCheckButtons = false;
      
      mutations.forEach((mutation) => {
        if (mutation.type === 'childList' && mutation.addedNodes.length > 0) {
          shouldCheckButtons = true;
        }
      });
      
      if (shouldCheckButtons) {
        setTimeout(() => this.attachButtonListeners(), 500);
      }
    });

    observer.observe(document.body, {
      childList: true,
      subtree: true
    });

    this.observers.push(observer);
    console.log('MutationObserver started');
  }

  attachButtonListeners() {
    console.log('Attempting to attach button listeners...');
    
    // Modern LeetCode selectors (updated for 2024/2025)
    const runSelectors = [
      'button[data-e2e-locator="console-run-button"]',
      'button[data-testid="console-run-button"]',
      'button:contains("Run")',
      'button[class*="run"]',
      'button[title*="Run"]',
      '[data-cy="run-code-btn"]'
    ];

    const submitSelectors = [
      'button[data-e2e-locator="console-submit-button"]',
      'button[data-testid="console-submit-button"]', 
      'button:contains("Submit")',
      'button[class*="submit"]',
      'button[title*="Submit"]',
      '[data-cy="submit-code-btn"]'
    ];

    // Try to find Run button
    let runButton = null;
    for (const selector of runSelectors) {
      try {
        if (selector.includes(':contains')) {
          // Handle :contains manually
          const buttons = document.querySelectorAll('button');
          runButton = Array.from(buttons).find(btn => 
            btn.textContent.trim().toLowerCase().includes('run')
          );
        } else {
          runButton = document.querySelector(selector);
        }
        
        if (runButton) {
          console.log('Found Run button with selector:', selector);
          break;
        }
      } catch (e) {
        console.log('Selector failed:', selector, e);
      }
    }

    // Try to find Submit button
    let submitButton = null;
    for (const selector of submitSelectors) {
      try {
        if (selector.includes(':contains')) {
          const buttons = document.querySelectorAll('button');
          submitButton = Array.from(buttons).find(btn => 
            btn.textContent.trim().toLowerCase().includes('submit')
          );
        } else {
          submitButton = document.querySelector(selector);
        }
        
        if (submitButton) {
          console.log('Found Submit button with selector:', selector);
          break;
        }
      } catch (e) {
        console.log('Selector failed:', selector, e);
      }
    }

    // Attach Run button listener
    if (runButton && !runButton.hasAttribute('data-tracker-attached')) {
      runButton.setAttribute('data-tracker-attached', 'true');
      runButton.addEventListener('click', (e) => {
        console.log('RUN BUTTON CLICKED!');
        this.handleRunClick(e);
      });
      console.log('✅ Run button listener attached successfully');
    } else if (!runButton) {
      console.log('❌ Run button not found. Available buttons:');
      const allButtons = document.querySelectorAll('button');
      allButtons.forEach((btn, i) => {
        console.log(`Button ${i}:`, btn.textContent.trim(), btn.className, btn);
      });
    }

    // Attach Submit button listener  
    if (submitButton && !submitButton.hasAttribute('data-tracker-attached')) {
      submitButton.setAttribute('data-tracker-attached', 'true');
      submitButton.addEventListener('click', (e) => {
        console.log('SUBMIT BUTTON CLICKED!');
        this.handleSubmitClick(e);
      });
      console.log('✅ Submit button listener attached successfully');
    } else if (!submitButton) {
      console.log('❌ Submit button not found');
    }
  }

  injectMonacoHelper() {
    console.log('Injecting Monaco helper...');
    const script = document.createElement('script');
    script.src = chrome.runtime.getURL('injected.js');
    script.onload = function() {
      console.log('Monaco helper script loaded');
      this.remove();
    };
    script.onerror = function() {
      console.error('Failed to load Monaco helper script');
      this.remove();
    };
    (document.head || document.documentElement).appendChild(script);
  }

  async handleRunClick(event) {
    console.log('🚀 Processing Run button click...');
    await this.captureAndSendData('run');
  }

  async handleSubmitClick(event) {
    console.log('📤 Processing Submit button click...');
    await this.captureAndSendData('submit');
  }

  async captureAndSendData(actionType) {
    try {
      console.log(`Capturing data for action: ${actionType}`);
      const data = await this.extractData(actionType);
      console.log('Extracted data:', data);
      
      await this.sendToServiceWorker(data);
      console.log('✅ Data sent successfully');
    } catch (error) {
      console.error('❌ Error capturing/sending data:', error);
    }
  }

  async extractData(actionType) {
    console.log('Extracting problem data...');
    
    const problemTitle = this.getProblemTitle();
    const problemUrl = window.location.href;
    const code = await this.getEditorContent();
    const language = this.getSelectedLanguage();

    const data = {
      timestamp: new Date().toISOString(),
      action: actionType,
      problemTitle,
      problemUrl,
      code,
      language,
      userAgent: navigator.userAgent,
      sessionId: await this.getSessionId()
    };

    console.log('Extracted data preview:', {
      ...data,
      code: code.substring(0, 100) + '...' // Truncate code for logging
    });

    return data;
  }

  getProblemTitle() {
    const selectors = [
      '[data-cy="question-title"]',
      'h1[class*="text-title"]',
      '.css-v3d350',
      'div[data-cy="question-title"]',
      'h1[data-testid="question-title"]',
      '.question-title h4',
      'h1'
    ];

    for (const selector of selectors) {
      try {
        const element = document.querySelector(selector);
        if (element && element.textContent.trim()) {
          console.log('Found problem title:', element.textContent.trim());
          return element.textContent.trim();
        }
      } catch (e) {
        console.log('Title selector failed:', selector);
      }
    }

    // Fallback: extract from URL
    const pathMatch = window.location.pathname.match(/problems\/([^\/]+)/);
    const fallbackTitle = pathMatch ? pathMatch[1].replace(/-/g, ' ') : 'Unknown Problem';
    console.log('Using fallback title:', fallbackTitle);
    return fallbackTitle;
  }

  async getEditorContent() {
    console.log('Getting editor content...');
    return new Promise((resolve) => {
      const messageHandler = (event) => {
        if (event.source !== window || !event.data.type) return;
        
        if (event.data.type === 'MONACO_CONTENT') {
          window.removeEventListener('message', messageHandler);
          console.log('Received Monaco content:', event.data.content?.substring(0, 100) + '...');
          resolve(event.data.content || '// No code found');
        }
      };

      window.addEventListener('message', messageHandler);
      window.postMessage({ type: 'GET_MONACO_CONTENT' }, '*');

      // Timeout after 3 seconds
      setTimeout(() => {
        window.removeEventListener('message', messageHandler);
        console.log('Monaco content timeout, using fallback');
        resolve('// Timeout - could not retrieve code');
      }, 3000);
    });
  }

  getSelectedLanguage() {
    const langSelectors = [
      '[data-cy="lang-select"]',
      '.ant-select-selection-item',
      'button[id*="headlessui-listbox-button"]',
      '[data-testid="lang-select"]'
    ];

    for (const selector of langSelectors) {
      try {
        const element = document.querySelector(selector);
        if (element && element.textContent.trim()) {
          const lang = element.textContent.trim().toLowerCase();
          console.log('Found language:', lang);
          return lang;
        }
      } catch (e) {
        console.log('Language selector failed:', selector);
      }
    }

    console.log('Language not found, using default');
    return 'unknown';
  }

  async getSessionId() {
    return new Promise((resolve) => {
      if (typeof chrome !== 'undefined' && chrome.storage) {
        chrome.storage.local.get(['sessionId'], (result) => {
          if (result.sessionId) {
            resolve(result.sessionId);
          } else {
            const newSessionId = 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
            chrome.storage.local.set({ sessionId: newSessionId });
            resolve(newSessionId);
          }
        });
      } else {
        resolve('session_fallback_' + Date.now());
      }
    });
  }

  async sendToServiceWorker(data) {
    console.log('Sending message to service worker...');
    
    if (typeof chrome !== 'undefined' && chrome.runtime) {
      try {
        const response = await chrome.runtime.sendMessage({
          action: 'saveActivity',
          data: data
        });
        console.log('Service worker response:', response);
      } catch (error) {
        console.error('Failed to send to service worker:', error);
      }
    } else {
      console.error('Chrome runtime not available');
    }

    // Also try to send to backend directly
    await this.sendToBackend(data);
  }

  async sendToBackend(data) {
    const settings = await this.getSettings();
    
    try {
      console.log('Sending to backend:', settings.backendUrl);
      const response = await fetch(settings.backendUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(data)
      });

      if (response.ok) {
        console.log('✅ Successfully sent to backend');
      } else {
        console.error('❌ Backend response error:', response.status);
      }
    } catch (error) {
      console.error('❌ Failed to send to backend:', error);
    }
  }
}

// Initialize tracker
console.log('Initializing LeetCode Tracker...');
const tracker = new LeetCodeTracker();

// Also add a global reference for debugging
window.leetcodeTracker = tracker;
console.log('LeetCode Tracker available as window.leetcodeTracker');
