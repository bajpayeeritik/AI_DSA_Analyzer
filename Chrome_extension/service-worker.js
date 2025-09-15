// Background service worker for LeetCode tracker
console.log('Service worker starting...');

const DEFAULT_SETTINGS = {
  userId: "user123",
  leetcodeUsername: "bajpayeeritik_",
  backendUrl: "http://localhost:8082/api/v1/problems",
  apiKey: "",
  idleThresholdMs: 30000,
  heartbeatIntervalMs: 30000,
  alfaApiBase: "http://localhost:3000",
  trackingEnabled: true
};

chrome.runtime.onInstalled.addListener((details) => {
  console.log('LeetCode Tracker extension installed');
  chrome.storage.sync.set(DEFAULT_SETTINGS, () => {
    console.log('✅ Custom settings initialized:', DEFAULT_SETTINGS);
  });

  if (chrome.alarms) {
    try {
      chrome.alarms.create('cleanup', { periodInMinutes: 60 });
      console.log('Cleanup alarm created successfully');
    } catch (error) {
      console.error('Failed to create alarms:', error);
    }
  }
});

// Fixed message handler
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  console.log('🔔 Background received message:', request.action);
  console.log('📦 Raw request data:', request);
  
  switch (request.action) {
    case 'getSettings':
      chrome.storage.sync.get(Object.keys(DEFAULT_SETTINGS), (result) => {
        console.log('⚙️ Retrieved settings:', result);
        sendResponse(result);
      });
      return true;
      
    case 'saveActivity':
      console.log('💾 Processing activity for Spring Boot backend...');
      console.log('📊 Raw activity data received:', request.data);
      handleActivityData(request.data, sendResponse);
      return true;
      
    default:
      sendResponse({ error: 'Unknown action' });
  }
});

// Fixed handleActivityData function
async function handleActivityData(rawData, sendResponse) {
  try {
    const settings = await getStoredSettings();
    console.log('📊 Using settings:', settings);
    
    // ⚠️ CRITICAL: Check if rawData has the expected structure
    console.log('🔍 Raw data analysis:', {
      hasUserId: !!rawData.userId,
      hasProblemData: !!rawData.problemData,
      hasTimestamp: !!rawData.timestamp,
      hasProblemTitle: !!rawData.problemTitle,
      dataKeys: Object.keys(rawData)
    });

    // Format data correctly for Spring Boot
    let springBootPayload;
    
    // Check if data is already in the expected format (nested structure)
    if (rawData.problemData) {
      // Data is already formatted correctly
      springBootPayload = rawData;
    } else {
      // Data is in flat structure from content script - transform it
      console.log('🔄 Transforming flat data structure to nested format');
      springBootPayload = {
        userId: settings.userId,
        leetcodeUsername: settings.leetcodeUsername,
        problemData: {
          title: rawData.problemTitle,
          url: rawData.problemUrl,
          action: rawData.action,
          timestamp: rawData.timestamp,
          language: rawData.language,
          code: rawData.code,
          sessionId: rawData.sessionId
        },
        metadata: {
          userAgent: rawData.userAgent,
          extensionVersion: chrome.runtime.getManifest().version,
          source: 'chrome_extension'
        }
      };
    }

    console.log('📤 Final payload structure:', {
      userId: springBootPayload.userId,
      leetcodeUsername: springBootPayload.leetcodeUsername,
      problemTitle: springBootPayload.problemData?.title,
      action: springBootPayload.problemData?.action,
      hasCode: !!springBootPayload.problemData?.code,
      codeLength: springBootPayload.problemData?.code?.length || 0
    });

    // Send to Spring Boot
    const success = await sendToSpringBoot(springBootPayload, settings);
    
    // Store locally as backup
    await storeActivityLocally(springBootPayload);
    
    sendResponse({ 
      success, 
      backendUrl: settings.backendUrl,
      userId: springBootPayload.userId,
      problemTitle: springBootPayload.problemData?.title,
      timestamp: new Date().toISOString() 
    });
    
  } catch (error) {
    console.error('❌ Error handling activity:', error);
    sendResponse({ success: false, error: error.message });
  }
}

// Send data to Spring Boot backend
async function sendToSpringBoot(data, settings) {
  try {
    const headers = {
      'Content-Type': 'application/json',
    };
    
    if (settings.apiKey) {
      headers['Authorization'] = `Bearer ${settings.apiKey}`;
    }

    console.log('🌐 Sending to Spring Boot:', settings.backendUrl);
    console.log('📦 Payload preview:', {
      ...data,
      problemData: {
        ...data.problemData,
        code: data.problemData?.code?.substring(0, 100) + '...'
      }
    });
    
    const response = await fetch(settings.backendUrl, {
      method: 'POST',
      headers: headers,
      body: JSON.stringify(data)
    });

    if (response.ok) {
      const responseData = await response.json();
      console.log('✅ Spring Boot success response:', responseData);
      return true;
    } else {
      const errorText = await response.text();
      console.error('❌ Spring Boot error response:', response.status, errorText);
      return false;
    }
  } catch (error) {
    console.error('❌ Network error sending to Spring Boot:', error);
    return false;
  }
}

async function storeActivityLocally(data) {
  return new Promise((resolve) => {
    chrome.storage.local.get(['activityLog'], (result) => {
      const activityLog = result.activityLog || [];
      activityLog.push({
        ...data,
        storedAt: new Date().toISOString()
      });
      
      if (activityLog.length > 100) {
        activityLog.splice(0, activityLog.length - 100);
      }
      
      chrome.storage.local.set({ activityLog }, () => {
        console.log('📚 Stored locally, total entries:', activityLog.length);
        resolve();
      });
    });
  });
}

function getStoredSettings() {
  return new Promise((resolve) => {
    chrome.storage.sync.get(Object.keys(DEFAULT_SETTINGS), (result) => {
      resolve({ ...DEFAULT_SETTINGS, ...result });
    });
  });
}

console.log('Service worker loaded with Spring Boot integration');
