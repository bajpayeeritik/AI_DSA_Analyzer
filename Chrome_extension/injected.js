// Injected script to access Monaco editor content
(function() {
  'use strict';

  // Function to get Monaco editor content
  function getMonacoContent() {
    try {
      // Try multiple methods to access Monaco editor
      
      // Method 1: Look for Monaco editor instances
      if (window.monaco && window.monaco.editor) {
        const models = window.monaco.editor.getModels();
        if (models.length > 0) {
          // Get the main editor model (usually the first one)
          return models[0].getValue();
        }
      }

      // Method 2: Look for editor instances in window
      if (window.require) {
        const monaco = window.require('monaco-editor');
        if (monaco && monaco.editor) {
          const editors = monaco.editor.getEditors();
          if (editors.length > 0) {
            return editors[0].getModel().getValue();
          }
        }
      }

      // Method 3: Try to find editor by DOM traversal
      const editorElements = document.querySelectorAll('.monaco-editor');
      for (const editorEl of editorElements) {
        if (editorEl.monaco_editor_instance) {
          return editorEl.monaco_editor_instance.getValue();
        }
      }

      // Method 4: Look in global scope for editor references
      const possibleEditorVars = ['editor', 'codeEditor', 'monacoEditor'];
      for (const varName of possibleEditorVars) {
        if (window[varName] && typeof window[varName].getValue === 'function') {
          return window[varName].getValue();
        }
      }

      // Method 5: Try to access through React fiber (LeetCode uses React)
      const reactFiber = document.querySelector('#editor')?.['__reactInternalInstance$'] || 
                        document.querySelector('.monaco-editor')?._reactInternalFiber;
      
      if (reactFiber) {
        // Traverse React fiber to find Monaco instance
        let current = reactFiber;
        while (current) {
          if (current.memoizedProps && current.memoizedProps.editor) {
            return current.memoizedProps.editor.getValue();
          }
          current = current.child || current.sibling || current.return;
        }
      }

      return '';
    } catch (error) {
      console.error('Error accessing Monaco content:', error);
      return '';
    }
  }

  // Listen for content requests
  window.addEventListener('message', (event) => {
    if (event.source !== window || !event.data.type) return;
    
    if (event.data.type === 'GET_MONACO_CONTENT') {
      const content = getMonacoContent();
      window.postMessage({
        type: 'MONACO_CONTENT',
        content: content
      }, '*');
    }
  });

  console.log('Monaco helper script injected');
})();
