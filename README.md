# Spring AI Ollama Demo

Questo è un progetto demo che utilizza Spring AI per interagire con il modello Gemma:4b attraverso Ollama.

## Prerequisiti

- Java 17 o superiore
- Maven
- Ollama installato e in esecuzione localmente
- Modello Gemma:4b scaricato in Ollama

## Configurazione

1. Assicurati che Ollama sia in esecuzione localmente sulla porta 11434
2. Scarica il modello Gemma:4b usando il comando:
   ```bash
   ollama pull gemma:4b
   ```

## Compilazione e Esecuzione

1. Compila il progetto:
   ```bash
   mvn clean package
   ```

2. Esegui l'applicazione:
   ```bash
   java -jar target/spring-ai-ollama-demo-0.0.1-SNAPSHOT.jar
   ```

## Utilizzo

L'applicazione espone un endpoint REST `/chat` che accetta richieste POST con un messaggio di testo.

Esempio di utilizzo con curl:
```bash
curl -X POST -H "Content-Type: text/plain" -d "Ciao, come stai?" http://localhost:8080/chat
```

## Configurazione

Le impostazioni del modello possono essere modificate nel file `src/main/resources/application.yml`. 