// Options page script
document.addEventListener('DOMContentLoaded', () => {
  loadSettings();
  loadStatistics();
  
  document.getElementById('backendUrl').addEventListener('change', saveSettings);
  document.getElementById('testConnection').addEventListener('click', testConnection);
  document.getElementById('exportAllData').addEventListener('click', exportAllData);
  document.getElementById('clearData').addEventListener('click', clearData);
  document.getElementById('viewData').addEventListener('click', viewData);
});

function loadSettings() {
  chrome.storage.sync.get(['backendUrl'], (result) => {
    document.getElementById('backendUrl').value = result.backendUrl || 'http://localhost:3000/api/activity';
  });
}

function saveSettings() {
  const backendUrl = document.getElementById('backendUrl').value;
  chrome.storage.sync.set({ backendUrl });
  showStatus('Settings saved successfully', 'success');
}

async function testConnection() {
  const backendUrl = document.getElementById('backendUrl').value;
  
  try {
    const response = await fetch(backendUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ test: true })
    });
    
    if (response.ok) {
      showStatus('Connection successful', 'success', 'connectionStatus');
    } else {
      showStatus(`Connection failed: ${response.status}`, 'error', 'connectionStatus');
    }
  } catch (error) {
    showStatus(`Connection failed: ${error.message}`, 'error', 'connectionStatus');
  }
}

function exportAllData() {
  chrome.storage.local.get(['activityLog'], (result) => {
    const activityLog = result.activityLog || [];
    
    if (activityLog.length === 0) {
      showStatus('No data to export', 'error');
      return;
    }
    
    const dataStr = JSON.stringify(activityLog, null, 2);
    const dataBlob = new Blob([dataStr], {type: 'application/json'});
    
    const url = URL.createObjectURL(dataBlob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `leetcode-activity-export-${new Date().toISOString().split('T')[0]}.json`;
    link.click();
    
    URL.revokeObjectURL(url);
    showStatus('Data exported successfully', 'success');
  });
}

function clearData() {
  if (confirm('Are you sure you want to clear all data? This cannot be undone.')) {
    chrome.storage.local.set({ activityLog: [] });
    showStatus('All data cleared', 'success');
    loadStatistics();
  }
}

function viewData() {
  chrome.storage.local.get(['activityLog'], (result) => {
    const activityLog = result.activityLog || [];
    
    const newWindow = window.open('', '_blank');
    newWindow.document.write(`
      <html>
        <head><title>LeetCode Activity Data</title></head>
        <body style="font-family: Arial, sans-serif; padding: 20px;">
          <h1>LeetCode Activity Data</h1>
          <pre>${JSON.stringify(activityLog, null, 2)}</pre>
        </body>
      </html>
    `);
  });
}

function loadStatistics() {
  chrome.storage.local.get(['activityLog'], (result) => {
    const activityLog = result.activityLog || [];
    
    const totalEntries = activityLog.length;
    const runCount = activityLog.filter(a => a.action === 'run').length;
    const submitCount = activityLog.filter(a => a.action === 'submit').length;
    const uniqueProblems = new Set(activityLog.map(a => a.problemUrl)).size;
    const languages = [...new Set(activityLog.map(a => a.language))];
    
    document.getElementById('statistics').innerHTML = `
      <p><strong>Total Activities:</strong> ${totalEntries}</p>
      <p><strong>Runs:</strong> ${runCount}</p>
      <p><strong>Submissions:</strong> ${submitCount}</p>
      <p><strong>Unique Problems:</strong> ${uniqueProblems}</p>
      <p><strong>Languages Used:</strong> ${languages.join(', ')}</p>
    `;
  });
}

function showStatus(message, type, elementId = null) {
  const statusDiv = document.createElement('div');
  statusDiv.textContent = message;
  statusDiv.className = `status ${type}`;
  
  const targetElement = elementId ? document.getElementById(elementId) : document.body;
  targetElement.appendChild(statusDiv);
  
  setTimeout(() => {
    statusDiv.remove();
  }, 3000);
}
