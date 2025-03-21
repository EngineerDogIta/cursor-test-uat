document.addEventListener('DOMContentLoaded', function() {
    const testButton = document.getElementById('testConnection');
    const errorContainer = document.createElement('div');
    errorContainer.className = 'alert alert-danger mt-3 d-none';
    
    if (testButton) {
        // Aggiungi il container degli errori dopo il form
        const form = testButton.closest('form');
        form.appendChild(errorContainer);
        
        testButton.addEventListener('click', function() {
            const serverUrl = document.getElementById('serverUrl').value;
            const username = document.getElementById('username').value;
            const apiToken = document.getElementById('apiToken').value;
            
            // Nascondi eventuali errori precedenti
            errorContainer.classList.add('d-none');
            
            if (!serverUrl || !username || !apiToken) {
                showError('Completa tutti i campi prima di testare la connessione');
                return;
            }
            
            testButton.disabled = true;
            testButton.textContent = 'Verifica in corso...';
            
            fetch('/jira/api/test-connection', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    serverUrl: serverUrl,
                    username: username,
                    apiToken: apiToken
                })
            })
            .then(response => {
                if (!response.ok) {
                    return response.json().then(data => {
                        throw new Error(data.message || 'Errore durante la connessione a Jira');
                    });
                }
                return response.json();
            })
            .then(data => {
                if (data.success) {
                    // Crea un alert di successo
                    const successAlert = document.createElement('div');
                    successAlert.className = 'alert alert-success mt-3';
                    successAlert.textContent = 'Connessione riuscita!';
                    errorContainer.parentNode.insertBefore(successAlert, errorContainer);
                    
                    // Rimuovi l'alert dopo 3 secondi
                    setTimeout(() => successAlert.remove(), 3000);
                } else {
                    showError(data.message || 'Errore durante la connessione a Jira');
                }
            })
            .catch(error => {
                showError(error.message || 'Errore durante il test della connessione');
            })
            .finally(() => {
                testButton.disabled = false;
                testButton.textContent = 'Testa connessione';
            });
        });
    }
    
    function showError(message) {
        errorContainer.textContent = message;
        errorContainer.classList.remove('d-none');
    }
    
    // Gestione esempi JQL
    const jqlExamples = document.querySelectorAll('.card-body code');
    const jqlInput = document.getElementById('jql');
    
    if (jqlExamples.length > 0 && jqlInput) {
        jqlExamples.forEach(example => {
            example.style.cursor = 'pointer';
            example.title = 'Clicca per usare questo esempio';
            
            example.addEventListener('click', function() {
                jqlInput.value = this.textContent.trim();
                jqlInput.focus();
            });
        });
    }
}); 