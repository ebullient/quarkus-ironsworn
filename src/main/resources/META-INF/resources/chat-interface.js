/**
 * Generic chat interface module for D&D Campaign Assistant.
 * Handles message display, user input, and API communication.
 */
class ChatInterface {
    constructor(config) {
        this.apiEndpoint = config.apiEndpoint;
        this.loadingMessage = config.loadingMessage || 'Thinking';
        this.errorMessage = config.errorMessage || 'Error: Could not get response.';
        this.method = config.method || 'POST';

        this.chatContainer = document.getElementById('chat-messages');
        this.messageInput = document.getElementById('message-input');
        this.sendBtn = document.getElementById('send-btn');

        this.init();
    }

    init() {
        this.sendBtn.addEventListener('click', () => this.sendQuestion());

        // Auto-resize textarea
        this.messageInput.addEventListener('input', () => this.autoResize());

        // Handle Enter key (Shift+Enter for new line)
        this.messageInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.sendQuestion();
            }
        });

        // Focus input on load
        this.messageInput.focus();
    }

    autoResize() {
        this.messageInput.style.height = 'auto';
        this.messageInput.style.height = this.messageInput.scrollHeight + 'px';
    }

    addMessage(text, isUser) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message ' + (isUser ? 'user' : 'assistant');

        if (isUser) {
            messageDiv.textContent = text;
        } else {
            messageDiv.innerHTML = text;
        }

        this.chatContainer.appendChild(messageDiv);
        this.chatContainer.scrollTop = this.chatContainer.scrollHeight;
    }

    addLoadingIndicator() {
        const loadingDiv = document.createElement('div');
        loadingDiv.className = 'loading';
        loadingDiv.id = 'loading';
        loadingDiv.textContent = this.loadingMessage;
        this.chatContainer.appendChild(loadingDiv);
        this.chatContainer.scrollTop = this.chatContainer.scrollHeight;
    }

    removeLoadingIndicator() {
        const loading = document.getElementById('loading');
        if (loading) {
            loading.remove();
        }
    }

    async sendQuestion() {
        const question = this.messageInput.value.trim();
        if (!question) return;

        this.addMessage(question, true);
        this.messageInput.value = '';
        this.autoResize(); // Reset height after clearing
        this.sendBtn.disabled = true;
        this.addLoadingIndicator();

        try {
            let response;
            if (this.method === 'GET') {
                response = await fetch(this.apiEndpoint + '?question=' + encodeURIComponent(question));
            } else {
                response = await fetch(this.apiEndpoint, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'text/plain'
                    },
                    body: question
                });
            }

            this.removeLoadingIndicator();

            if (response.ok) {
                const answer = await response.text();
                this.addMessage(answer, false);
            } else {
                this.addMessage(this.errorMessage, false);
            }
        } catch (error) {
            this.removeLoadingIndicator();
            this.addMessage('Error: ' + error.message, false);
        } finally {
            this.sendBtn.disabled = false;
            this.messageInput.focus();
        }
    }
}
