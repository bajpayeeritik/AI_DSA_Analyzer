// Popup script for LeetCode tracker
document.addEventListener('DOMContentLoaded', async () => {
  await loadSettings();
  await loadStats();
  
  // Set up event listeners
  document.getElementById('trackingToggle').addEventListener('change', saveSettings);
  document.getElementById('backendUrl').addEventListener('change', saveSettings);
  document.getElementById('exportData').addEventListener('click', exportData);
});

async function loadSettings() {
  return new Promise((resolve) => {
    chrome.storage.sync.get(['trackingEnabled', 'backendUrl'], (result) => {
      document.getElementById('trackingToggle').checked = result.trackingEnabled ?? true;
      document.getElementById('backendUrl').value = result.backendUrl ?? 'http://localhost:3000/api/activity';
      resolve();
    });
  });
}

async function saveSettings() {
  const trackingEnabled = document.getElementById('trackingToggle').checked;
  const backendUrl = document.getElementById('backendUrl').value;
  
  chrome.storage.sync.set({
    trackingEnabled,
    backendUrl
  });
  
  console.log('Settings saved');
}

async function loadStats() {
  return new Promise((resolve) => {
    chrome.storage.local.get(['activityLog'], (result) => {
      const activityLog = result.activityLog || [];
      
      // Filter today's activities
      const today = new Date().toDateString();
      const todayActivities = activityLog.filter(activity => 
        new Date(activity.timestamp).toDateString() === today
      );
      
      // Calculate stats
      const runCount = todayActivities.filter(a => a.action === 'run').length;
      const submitCount = todayActivities.filter(a => a.action === 'submit').length;
      const uniqueProblems = new Set(todayActivities.map(a => a.problemUrl)).size;
      
      // Update UI
      document.getElementById('runCount').textContent = runCount;
      document.getElementById('submitCount').textContent = submitCount;
      document.getElementById('problemCount').textContent = uniqueProblems;
      
      resolve();
    });
  });
}

function exportData() {
  chrome.storage.local.get(['activityLog'], (result) => {
    const activityLog = result.activityLog || [];
    
    if (activityLog.length === 0) {
      alert('No data to export');
      return;
    }
    
    const dataStr = JSON.stringify(activityLog, null, 2);
    const dataBlob = new Blob([dataStr], {type: 'application/json'});
    
    const url = URL.createObjectURL(dataBlob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `leetcode-activity-${new Date().toISOString().split('T')[0]}.json`;
    link.click();
    
    URL.revokeObjectURL(url);
  });
}
