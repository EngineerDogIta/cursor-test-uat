// Funzioni di utilità
const formatDate = (date) => {
    return new Date(date).toLocaleString('it-IT', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
};

// Gestione dei form
document.addEventListener('DOMContentLoaded', function() {
    // Conferma prima dell'invio del form
    const forms = document.querySelectorAll('form');
    forms.forEach(form => {
        form.addEventListener('submit', function(e) {
            if (!confirm('Sei sicuro di voler procedere?')) {
                e.preventDefault();
            }
        });
    });

    // Validazione del form del ticket
    const ticketForm = document.querySelector('form[action*="/ticket/submit"]');
    if (ticketForm) {
        const jiraTicketInput = ticketForm.querySelector('#jiraTicket');
        if (jiraTicketInput) {
            jiraTicketInput.addEventListener('input', function() {
                const value = this.value.trim();
                if (value && !/^[A-Z]+-\d+$/.test(value)) {
                    this.setCustomValidity('Il formato del ticket Jira deve essere PROJ-123');
                } else {
                    this.setCustomValidity('');
                }
            });
        }
    }

    // Aggiornamento automatico dello stato dei job
    const jobStatus = document.querySelector('.job-status');
    if (jobStatus && jobStatus.dataset.status === 'IN_PROGRESS') {
        const updateInterval = 30000; // 30 secondi
        setInterval(() => {
            fetch(`/api/jobs/${jobStatus.dataset.jobId}/status`)
                .then(response => response.json())
                .then(data => {
                    if (data.status !== 'IN_PROGRESS') {
                        window.location.reload();
                    }
                })
                .catch(error => console.error('Errore nell\'aggiornamento dello stato:', error));
        }, updateInterval);
    }

    // Gestione delle notifiche
    const notifications = document.querySelectorAll('.alert');
    notifications.forEach(notification => {
        if (notification.classList.contains('alert-success') || 
            notification.classList.contains('alert-danger')) {
            setTimeout(() => {
                notification.style.opacity = '0';
                setTimeout(() => notification.remove(), 300);
            }, 5000);
        }
    });

    // Gestione delle tabelle responsive
    const tables = document.querySelectorAll('.table-responsive');
    tables.forEach(table => {
        const wrapper = document.createElement('div');
        wrapper.className = 'table-wrapper';
        table.parentNode.insertBefore(wrapper, table);
        wrapper.appendChild(table);
    });
});

// Gestione degli errori
window.addEventListener('error', function(e) {
    console.error('Errore JavaScript:', e.message);
    // Qui puoi aggiungere la logica per mostrare un messaggio di errore all'utente
});

// Gestione delle richieste AJAX
const handleAjaxError = (error) => {
    console.error('Errore nella richiesta AJAX:', error);
    // Qui puoi aggiungere la logica per mostrare un messaggio di errore all'utente
};

// Funzione per aggiornare lo stato di un job
const updateJobStatus = (jobId) => {
    fetch(`/api/jobs/${jobId}/status`)
        .then(response => response.json())
        .then(data => {
            const statusElement = document.querySelector(`[data-job-id="${jobId}"]`);
            if (statusElement) {
                statusElement.textContent = data.status;
                statusElement.className = `badge bg-${getStatusColor(data.status)}`;
            }
        })
        .catch(handleAjaxError);
};

// Funzione per ottenere il colore dello stato
const getStatusColor = (status) => {
    switch (status) {
        case 'COMPLETED':
            return 'success';
        case 'IN_PROGRESS':
            return 'warning';
        case 'FAILED':
            return 'danger';
        default:
            return 'secondary';
    }
}; 