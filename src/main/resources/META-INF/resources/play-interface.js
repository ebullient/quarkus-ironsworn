/**
 * Ironsworn Play Interface — WebSocket client, dice widget, momentum burn, character creation.
 *
 * During creation, widgets (inspiration, stats form, backstory guide messages, vow editor)
 * are injected directly into the #chat-messages stream. The main textarea is reused for
 * backstory chat input. Once creation finalizes, the UI transitions to gameplay in-place.
 */
class PlayInterface {
    constructor(config) {
        this.campaignId = config.campaignId;
        this.wsUrl = config.wsUrl;
        this.character = null;
        this.pendingMove = null;
        this.selectedStat = null;
        this.creationMode = false;

        // DOM elements — gameplay
        this.chatContainer = document.getElementById('chat-messages');
        this.messageInput = document.getElementById('message-input');
        this.sendBtn = document.getElementById('send-btn');
        this.inputContainer = document.getElementById('input-container');
        this.moveBadge = document.getElementById('move-badge');
        this.rollMoveNameEl = document.getElementById('roll-move-name');
        this.rollControls = document.getElementById('roll-controls');
        this.rollBtn = document.getElementById('roll-btn');
        this.manualDiceToggle = document.getElementById('manual-dice-toggle');
        this.manualDicePanel = document.getElementById('manual-dice');
        this.momentumBurnPanel = document.getElementById('momentum-burn');
        this.drawerToggle = document.getElementById('drawer-toggle');
        this.sidebar = document.getElementById('sidebar');

        this.inspireBtn = document.getElementById('inspire-btn');

        this.initWebSocket();
        this.initInput();
        this.initMoveButtons();
        this.initOracleButtons();
        this.initRollPanel();
        this.initDrawer();
        this.initMeters();
        this.initInspire();
    }

    // --- WebSocket ---

    initWebSocket() {
        this.ws = new WebSocket(this.wsUrl);
        this.ws.onmessage = (event) => {
            const msg = JSON.parse(event.data);
            this.handleMessage(msg);
        };
        this.ws.onclose = () => {
            setTimeout(() => this.initWebSocket(), 3000);
        };
        this.ws.onerror = (err) => {
            console.error('WebSocket error:', err);
        };
    }

    handleMessage(msg) {
        switch (msg.type) {
            // Creation flow
            case 'creation_phase':
                this.handleCreationPhase(msg);
                break;
            case 'inspire':
                this.handleInspire(msg);
                break;
            case 'creation_response':
                this.handleCreationResponse(msg);
                break;
            case 'creation_resume':
                this.handleCreationResume(msg);
                break;
            case 'creation_ready':
                this.injectStatsWidget(msg.character);
                // Server is re-engaging the guide after resume — show loading
                this.addLoadingIndicator();
                break;
            case 'play_resume':
                this.handlePlayResume(msg);
                break;
            // Gameplay flow
            case 'narrative':
                this.handleNarrative(msg);
                break;
            case 'move_outcome':
                this.handleMoveOutcome(msg);
                break;
            case 'oracle_result':
                this.handleOracleResult(msg);
                break;
            case 'character_update':
                this.handleCharacterUpdate(msg);
                break;
            case 'loading':
                this.addLoadingIndicator();
                break;
            case 'ready':
                this.enableInput();
                break;
            case 'error':
                this.removeLoadingIndicator();
                this.addSystemMessage('Error: ' + msg.message);
                this.enableInput();
                break;
        }
    }

    send(obj) {
        if (this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(obj));
        }
    }

    // --- Creation flow (inline in chat) ---

    enterCreationMode() {
        this.creationMode = true;
        // Hide gameplay chrome during creation
        document.getElementById('character-bar').classList.add('hidden');
        document.getElementById('scene-bar').classList.add('hidden');
        if (this.drawerToggle) {
            this.drawerToggle.classList.add('hidden');
        }
        if (this.inspireBtn) {
            this.inspireBtn.classList.add('hidden');
        }
        // Repurpose the input for backstory chat
        this.messageInput.placeholder = 'Tell the guide about your character...';
        this.sendBtn.textContent = 'Send';
    }

    exitCreationMode() {
        this.creationMode = false;
        document.getElementById('character-bar').classList.remove('hidden');
        document.getElementById('scene-bar').classList.remove('hidden');
        if (this.drawerToggle) {
            this.drawerToggle.classList.remove('hidden');
        }
        if (this.inspireBtn) {
            this.inspireBtn.classList.remove('hidden');
        }
        this.messageInput.placeholder = 'What do you do?';
        this.sendBtn.textContent = 'Send';
        // Remove interactive creation widgets (stats, vow) but keep conversation messages
        this.chatContainer.querySelectorAll('.stats-widget, .vow-widget').forEach(el => el.remove());
        // Reclassify creation messages as regular messages
        this.chatContainer.querySelectorAll('.creation-widget').forEach(el => el.classList.remove('creation-widget'));
        this.enableInput();
    }

    handleCreationPhase(msg) {
        if (msg.phase === 'creation') {
            this.enterCreationMode();
        } else if (msg.phase === 'active') {
            this.exitCreationMode();
        }
    }

    handleInspire(msg) {
        // Inject inspiration text as a chat widget
        const widget = document.createElement('div');
        widget.className = 'message creation-widget inspiration-text';
        this.chatContainer.appendChild(widget);
        this.typewriter(widget, msg.text || '', 30, () => {
            // After typewriter finishes, inject the stats widget
            this.injectStatsWidget();
        });
        this.scrollToBottom();
    }

    injectStatsWidget(character) {
        // Use persisted stats if available, otherwise defaults
        const c = character || { edge: 1, heart: 1, iron: 2, shadow: 2, wits: 3 };
        const isDefault = (c.edge === 1 && c.heart === 1 && c.iron === 1 && c.shadow === 1 && c.wits === 1);
        const alreadyConfirmed = !isDefault && (c.edge + c.heart + c.iron + c.shadow + c.wits === 9);

        const widget = document.createElement('div');
        widget.className = 'creation-widget stats-widget';
        widget.innerHTML =
            '<div class="stats-widget-header">Shape Your Character</div>' +
            '<p class="stat-hint">Assign stats (1, 1, 2, 2, 3 — distribute among the five):</p>' +
            '<div class="stat-inputs">' +
            '  <label>Edge <input type="number" id="create-edge" min="1" max="3" value="' + c.edge + '"></label>' +
            '  <label>Heart <input type="number" id="create-heart" min="1" max="3" value="' + c.heart + '"></label>' +
            '  <label>Iron <input type="number" id="create-iron" min="1" max="3" value="' + c.iron + '"></label>' +
            '  <label>Shadow <input type="number" id="create-shadow" min="1" max="3" value="' + c.shadow + '"></label>' +
            '  <label>Wits <input type="number" id="create-wits" min="1" max="3" value="' + c.wits + '"></label>' +
            '</div>' +
            '<div class="stats-widget-footer">' +
            '  <span id="stats-validation" class="stats-validation"></span>' +
            '  <button id="confirm-stats-btn" class="btn-primary">Confirm Stats</button>' +
            '</div>';
        this.chatContainer.appendChild(widget);
        this.scrollToBottom();

        if (alreadyConfirmed) {
            // Stats were already confirmed before reconnect — lock the widget
            widget.querySelectorAll('input[type="number"]').forEach(input => input.disabled = true);
            document.getElementById('confirm-stats-btn').textContent = 'Stats confirmed';
            document.getElementById('confirm-stats-btn').disabled = true;
            widget.classList.add('confirmed');
        } else {
            // Validate on every input change
            const inputs = widget.querySelectorAll('input[type="number"]');
            inputs.forEach(input => input.addEventListener('input', () => this.validateStats()));
            this.validateStats();

            document.getElementById('confirm-stats-btn').addEventListener('click', () => {
                this.confirmStats(widget);
            });
        }

        // Enable input for backstory chat
        this.enableInput();
        this.messageInput.focus();
    }

    validateStats() {
        const edge = parseInt(document.getElementById('create-edge').value) || 0;
        const heart = parseInt(document.getElementById('create-heart').value) || 0;
        const iron = parseInt(document.getElementById('create-iron').value) || 0;
        const shadow = parseInt(document.getElementById('create-shadow').value) || 0;
        const wits = parseInt(document.getElementById('create-wits').value) || 0;
        const sum = edge + heart + iron + shadow + wits;
        const validEl = document.getElementById('stats-validation');
        const confirmBtn = document.getElementById('confirm-stats-btn');

        const allInRange = [edge, heart, iron, shadow, wits].every(v => v >= 1 && v <= 3);

        if (!allInRange) {
            validEl.textContent = 'Each stat must be 1, 2, or 3';
            validEl.className = 'stats-validation invalid';
            confirmBtn.disabled = true;
        } else if (sum !== 9) {
            validEl.textContent = 'Total: ' + sum + '/9 — must sum to 9';
            validEl.className = 'stats-validation invalid';
            confirmBtn.disabled = true;
        } else {
            validEl.textContent = 'Total: 9/9';
            validEl.className = 'stats-validation valid';
            confirmBtn.disabled = false;
        }
    }

    confirmStats(widget) {
        const edge = parseInt(document.getElementById('create-edge').value);
        const heart = parseInt(document.getElementById('create-heart').value);
        const iron = parseInt(document.getElementById('create-iron').value);
        const shadow = parseInt(document.getElementById('create-shadow').value);
        const wits = parseInt(document.getElementById('create-wits').value);

        // Send update to server
        this.send({
            type: 'character_update',
            character: { edge, heart, iron, shadow, wits, health: 5, spirit: 5, supply: 5, momentum: 2, vows: [] }
        });

        // Lock the inputs
        widget.querySelectorAll('input[type="number"]').forEach(input => {
            input.disabled = true;
        });
        const btn = document.getElementById('confirm-stats-btn');
        btn.textContent = 'Stats confirmed';
        btn.disabled = true;
        widget.classList.add('confirmed');
    }

    handleCreationResponse(msg) {
        this.removeLoadingIndicator();
        // Add guide's message to chat
        this.addCreationGuideMessage(msg.message);

        if (msg.suggestedVow) {
            this.injectVowWidget(msg.suggestedVow);
        } else {
            this.enableInput();
            this.messageInput.focus();
        }
    }

    handleCreationResume(msg) {
        this.appendBlocks((msg.blocks || []), 'creation-widget');
        // Stats widget will be injected by the subsequent creation_ready message
    }

    handlePlayResume(msg) {
        this.appendBlocks((msg.blocks || []));
    }

    appendBlocks(blocks, extraClass) {
        const grouped = [];
        for (const block of (blocks || [])) {
            const type = block.type || 'assistant';
            const html = block.html || '';
            const last = grouped.length > 0 ? grouped[grouped.length - 1] : null;
            if (type === 'assistant' && last && last.type === 'assistant') {
                last.html += '\n' + html;
            } else {
                grouped.push({ type, html });
            }
        }

        for (const block of grouped) {
            const div = document.createElement('div');
            div.className = 'message ' + (extraClass ? (extraClass + ' ') : '') + (block.type || 'assistant');
            if (block.type === 'user') {
                div.textContent = block.html;
            } else {
                div.innerHTML = block.html;
            }
            this.chatContainer.appendChild(div);
        }
        this.scrollToBottom();
    }

    addCreationGuideMessage(text) {
        const div = document.createElement('div');
        div.className = 'message assistant creation-widget';
        div.textContent = text;
        this.chatContainer.appendChild(div);
        this.scrollToBottom();
    }

    injectVowWidget(suggestedVow) {
        const widget = document.createElement('div');
        widget.className = 'creation-widget vow-widget';
        widget.innerHTML =
            '<div class="vow-widget-header">Your Vow</div>' +
            '<div class="vow-editor">' +
            '  <input type="text" id="vow-text" value="' + this.escapeAttr(suggestedVow) + '" placeholder="e.g., Find the truth behind the iron curse">' +
            '  <div class="vow-rank-row">' +
            '    <label for="vow-rank">Rank</label>' +
            '    <select id="vow-rank">' +
            '      <option value="TROUBLESOME">Troublesome</option>' +
            '      <option value="DANGEROUS" selected>Dangerous</option>' +
            '      <option value="FORMIDABLE">Formidable</option>' +
            '      <option value="EXTREME">Extreme</option>' +
            '      <option value="EPIC">Epic</option>' +
            '    </select>' +
            '  </div>' +
            '  <button id="begin-journey-btn" class="btn-primary">Begin Journey</button>' +
            '</div>';
        this.chatContainer.appendChild(widget);
        this.scrollToBottom();

        document.getElementById('begin-journey-btn').addEventListener('click', () => {
            this.finalizeCreation();
        });

        // Hide the main input — vow widget has its own button
        this.inputContainer.classList.add('hidden');
    }

    finalizeCreation() {
        const btn = document.getElementById('begin-journey-btn');
        btn.disabled = true;
        btn.textContent = 'Creating...';

        const vowText = document.getElementById('vow-text').value.trim();
        const vowRank = document.getElementById('vow-rank').value;

        const character = {
            edge: parseInt(document.getElementById('create-edge').value),
            heart: parseInt(document.getElementById('create-heart').value),
            iron: parseInt(document.getElementById('create-iron').value),
            shadow: parseInt(document.getElementById('create-shadow').value),
            wits: parseInt(document.getElementById('create-wits').value),
            vows: []
        };
        if (vowText) {
            character.vows = [{ description: vowText, rank: vowRank, progress: 0 }];
        }

        this.send({
            type: 'finalize_creation',
            character
        });
    }

    escapeAttr(str) {
        return str.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    }

    typewriter(el, text, speed, onComplete) {
        el.textContent = '';
        let i = 0;
        function tick() {
            if (i < text.length) {
                el.textContent += text.charAt(i);
                i++;
                setTimeout(tick, speed);
            } else if (onComplete) {
                onComplete();
            }
        }
        tick();
    }

    // --- Input ---

    initInput() {
        this.sendBtn.addEventListener('click', () => this.handleSend());
        this.messageInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.handleSend();
            }
        });
        this.messageInput.addEventListener('input', () => {
            this.messageInput.style.height = 'auto';
            this.messageInput.style.height = this.messageInput.scrollHeight + 'px';
        });
    }

    handleSend() {
        if (this.pendingMove) {
            // In move mode, Enter triggers the roll (if stat selected)
            if (this.selectedStat) {
                this.executeRoll();
            }
            return;
        }
        if (this.creationMode) {
            this.sendCreationChat();
        } else {
            this.sendNarrative();
        }
    }

    sendCreationChat() {
        const text = this.messageInput.value.trim();
        if (!text) return;
        this.addUserMessage(text);
        this.messageInput.value = '';
        this.messageInput.style.height = 'auto';
        this.disableInput();
        this.addLoadingIndicator();
        this.send({ type: 'creation_chat', text });
    }

    sendNarrative() {
        const text = this.messageInput.value.trim();
        if (!text) return;
        this.addUserMessage(text);
        this.messageInput.value = '';
        this.messageInput.style.height = 'auto';
        this.disableInput();
        this.addLoadingIndicator();
        this.send({ type: 'narrative', text });
    }

    disableInput() {
        this.sendBtn.disabled = true;
        this.messageInput.disabled = true;
    }

    enableInput() {
        this.sendBtn.disabled = false;
        this.messageInput.disabled = false;
        this.inputContainer.classList.remove('hidden');
    }

    // --- Messages ---

    addUserMessage(text) {
        const div = document.createElement('div');
        div.className = 'message user';
        if (this.creationMode) div.classList.add('creation-widget');
        div.textContent = text;
        this.chatContainer.appendChild(div);
        this.scrollToBottom();
    }

    addNarrativeMessage(html) {
        const div = document.createElement('div');
        div.className = 'message assistant';
        div.innerHTML = html;
        this.chatContainer.appendChild(div);
        this.scrollToBottom();
    }

    addSystemMessage(text) {
        const div = document.createElement('div');
        div.className = 'message system';
        div.textContent = text;
        this.chatContainer.appendChild(div);
        this.scrollToBottom();
    }

    addMechanicalMessage(html, outcomeClass) {
        const div = document.createElement('div');
        div.className = 'message mechanical ' + (outcomeClass || '');
        div.innerHTML = html;
        this.chatContainer.appendChild(div);
        this.scrollToBottom();
    }

    addLoadingIndicator() {
        const div = document.createElement('div');
        div.className = 'loading';
        div.id = 'loading';
        div.textContent = this.creationMode ? 'The guide considers...' : 'The oracle speaks...';
        this.chatContainer.appendChild(div);
        this.scrollToBottom();
    }

    removeLoadingIndicator() {
        const el = document.getElementById('loading');
        if (el) el.remove();
    }

    scrollToBottom() {
        this.chatContainer.scrollTop = this.chatContainer.scrollHeight;
    }

    // --- Message handlers ---

    handleNarrative(msg) {
        this.removeLoadingIndicator();
        if (msg.blocks && msg.blocks.length > 0) {
            this.appendBlocks(msg.blocks);
        } else {
            this.addNarrativeMessage(msg.narrativeHtml || msg.narrative);
        }
        if (msg.location) {
            document.getElementById('scene-location').textContent = msg.location;
        }
        if (msg.npcs && msg.npcs.length > 0) {
            document.getElementById('scene-npcs').textContent = msg.npcs.join(', ');
        }
        this.enableInput();
    }

    handleMoveOutcome(msg) {
        // Remove the loading indicator so we can insert rules before it
        this.removeLoadingIndicator();

        // Append rules to the last mechanical message (the roll result)
        const lastMech = this.chatContainer.querySelector('.message.mechanical:last-of-type');
        if (lastMech) {
            const details = document.createElement('details');
            details.className = 'move-outcome-details';
            details.innerHTML = '<summary><strong>' + msg.moveName + '</strong> — Rules</summary>' +
                '<p>' + msg.moveOutcomeText + '</p>';
            lastMech.appendChild(details);
        }

        // Re-add loading indicator (narrative is still coming)
        this.addLoadingIndicator();
    }

    handleOracleResult(msg) {
        const r = msg.result;
        this.addMechanicalMessage(
            '<strong>Oracle</strong> (' + r.tableName + '): <strong>' + r.resultText + '</strong> <span class="roll-detail">[' + r.roll + ']</span>',
            'oracle'
        );
    }

    handleCharacterUpdate(msg) {
        console.log('[character] received update:', msg.character);
        this.character = msg.character;
        this.updateCharacterDisplay();
    }

    // --- Meters ---

    initMeters() {
        this.meterDebounce = null;
        ['health', 'spirit', 'supply', 'momentum'].forEach(key => {
            const input = document.getElementById('meter-' + key);
            input.addEventListener('input', () => {
                const min = parseInt(input.min);
                const max = parseInt(input.max);
                let val = parseInt(input.value);
                if (isNaN(val)) return;
                val = Math.max(min, Math.min(max, val));
                input.value = val;
                if (this.character) {
                    this.character[key] = val;
                    clearTimeout(this.meterDebounce);
                    this.meterDebounce = setTimeout(() => {
                        console.log('[character] sending meter update:', key, '=', val);
                        this.send({ type: 'character_update', character: this.character });
                    }, 500);
                }
            });
        });
    }

    // --- Character display ---

    updateCharacterDisplay() {
        const c = this.character;
        if (!c) return;

        document.getElementById('char-name-display').textContent = c.name;
        document.getElementById('stat-edge').textContent = c.edge;
        document.getElementById('stat-heart').textContent = c.heart;
        document.getElementById('stat-iron').textContent = c.iron;
        document.getElementById('stat-shadow').textContent = c.shadow;
        document.getElementById('stat-wits').textContent = c.wits;

        document.getElementById('meter-health').value = c.health;
        document.getElementById('meter-spirit').value = c.spirit;
        document.getElementById('meter-supply').value = c.supply;
        document.getElementById('meter-momentum').value = c.momentum;

        // Vows
        const vowsList = document.getElementById('vows-list');
        vowsList.innerHTML = '';
        if (c.vows) {
            c.vows.forEach((vow, idx) => {
                const div = document.createElement('div');
                div.className = 'vow-item';
                div.innerHTML =
                    '<span class="vow-desc">' + vow.description + '</span>' +
                    '<span class="vow-rank">' + vow.rank.toLowerCase() + '</span>' +
                    '<div class="progress-bar"><div class="progress-fill" style="width:' + (vow.progress * 10) + '%"></div></div>' +
                    '<button class="progress-mark-btn" data-vow="' + idx + '">Mark</button>';
                vowsList.appendChild(div);
            });

            vowsList.querySelectorAll('.progress-mark-btn').forEach(btn => {
                btn.addEventListener('click', () => {
                    this.send({ type: 'progress_mark', vowIndex: parseInt(btn.dataset.vow) });
                });
            });
        }
    }

    // --- Moves ---

    initMoveButtons() {
        document.querySelectorAll('.move-btn').forEach(btn => {
            btn.addEventListener('click', () => this.startMove(btn));
        });

        document.querySelectorAll('.stat-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                if (this.pendingMove) {
                    this.selectStat(btn.dataset.stat);
                }
            });
        });
    }

    startMove(btn) {
        this.pendingMove = {
            category: btn.dataset.category,
            key: btn.dataset.move,
            name: btn.dataset.moveName
        };
        this.selectedStat = null;

        // Enhance the input container with move controls
        this.rollMoveNameEl.textContent = this.pendingMove.name;
        this.moveBadge.classList.remove('hidden');
        this.rollControls.classList.remove('hidden');
        this.sendBtn.classList.add('hidden');
        if (this.inspireBtn) {
            this.inspireBtn.classList.add('hidden');
        }
        this.rollBtn.disabled = true;
        this.inputContainer.classList.add('move-mode');
        document.getElementById('roll-stat-prompt').textContent = 'Select a stat';
        this.messageInput.placeholder = 'How do you ' + this.pendingMove.name + '?';

        document.querySelectorAll('.stat-btn').forEach(b => b.classList.add('selectable'));
        this.sidebar.classList.remove('open');
        this.messageInput.focus();
    }

    selectStat(stat) {
        this.selectedStat = stat;
        document.querySelectorAll('.stat-btn').forEach(b => {
            b.classList.toggle('selected', b.dataset.stat === stat);
        });
        document.getElementById('roll-stat-prompt').textContent = 'Rolling +' + stat;
        this.rollBtn.disabled = false;
    }

    // --- Roll panel ---

    initRollPanel() {
        this.rollBtn.addEventListener('click', () => this.executeRoll());

        document.getElementById('roll-cancel').addEventListener('click', () => this.cancelMove());

        this.manualDiceToggle.addEventListener('change', () => {
            this.manualDicePanel.classList.toggle('hidden', !this.manualDiceToggle.checked);
        });

        document.getElementById('burn-btn').addEventListener('click', () => this.burnMomentum());
        document.getElementById('skip-burn-btn').addEventListener('click', () => this.skipBurn());
    }

    executeRoll() {
        if (!this.pendingMove || !this.selectedStat || !this.character) return;

        const statValue = this.character[this.selectedStat];
        const adds = parseInt(document.getElementById('roll-adds').value) || 0;
        let actionDie, challenge1, challenge2;

        if (this.manualDiceToggle.checked) {
            actionDie = parseInt(document.getElementById('dice-action').value);
            challenge1 = parseInt(document.getElementById('dice-c1').value);
            challenge2 = parseInt(document.getElementById('dice-c2').value);
            if (isNaN(actionDie) || isNaN(challenge1) || isNaN(challenge2)) {
                alert('Please enter all dice values');
                return;
            }
        } else {
            actionDie = Math.floor(Math.random() * 6) + 1;
            challenge1 = Math.floor(Math.random() * 10) + 1;
            challenge2 = Math.floor(Math.random() * 10) + 1;
        }

        const actionScore = Math.min(10, actionDie + statValue + adds);
        const outcome = this.computeOutcome(actionScore, challenge1, challenge2);

        // Show player's action description before the mechanical result
        const playerAction = this.messageInput.value.trim();
        if (playerAction) {
            this.addUserMessage(playerAction);
        }

        const outcomeClass = outcome.replace('_', '-').toLowerCase();
        const addsLabel = adds > 0 ? ' +' + adds + ' adds' : '';
        this.addMechanicalMessage(
            '<strong>' + this.pendingMove.name + '</strong> (+' + this.selectedStat + ' ' + statValue + addsLabel + ')<br>' +
            'Action die: ' + actionDie + ' → Score: ' + actionScore + '<br>' +
            'Challenge: ' + challenge1 + ' / ' + challenge2 + '<br>' +
            '<strong class="outcome-' + outcomeClass + '">' + outcome.replace('_', ' ') + '</strong>',
            outcomeClass
        );

        const momentum = this.character.momentum;
        if (this.canBurnMomentum(momentum, actionScore, challenge1, challenge2, outcome)) {
            this.pendingRollData = {
                actionDie, challenge1, challenge2, actionScore, statValue,
                adds, outcome, momentum
            };
            this.showMomentumBurn(momentum, challenge1, challenge2, outcome);
            return;
        }

        this.finalizeRoll(actionDie, challenge1, challenge2, actionScore, outcome, adds);
    }

    computeOutcome(actionScore, challenge1, challenge2) {
        if (actionScore > challenge1 && actionScore > challenge2) return 'STRONG_HIT';
        if (actionScore > challenge1 || actionScore > challenge2) return 'WEAK_HIT';
        return 'MISS';
    }

    canBurnMomentum(momentum, actionScore, challenge1, challenge2, currentOutcome) {
        if (momentum <= 0) return false;
        const burnOutcome = this.computeOutcome(momentum, challenge1, challenge2);
        return this.outcomeRank(burnOutcome) > this.outcomeRank(currentOutcome);
    }

    outcomeRank(outcome) {
        switch (outcome) {
            case 'STRONG_HIT': return 2;
            case 'WEAK_HIT': return 1;
            case 'MISS': return 0;
            default: return -1;
        }
    }

    showMomentumBurn(momentum, challenge1, challenge2, currentOutcome) {
        const burnOutcome = this.computeOutcome(momentum, challenge1, challenge2);
        document.getElementById('burn-comparison').innerHTML =
            'Current: <strong>' + currentOutcome.replace('_', ' ') + '</strong> → ' +
            'Burn momentum (' + momentum + '→2): <strong class="outcome-' + burnOutcome.toLowerCase().replace('_', '-') + '">' +
            burnOutcome.replace('_', ' ') + '</strong>';
        this.momentumBurnPanel.classList.remove('hidden');
        this.rollBtn.disabled = true;
    }

    burnMomentum() {
        const data = this.pendingRollData;
        const burnOutcome = this.computeOutcome(data.momentum, data.challenge1, data.challenge2);

        this.character.momentum = 2;
        console.log('[character] sending momentum burn update');
        this.send({ type: 'character_update', character: this.character });
        this.updateCharacterDisplay();

        this.addSystemMessage('Momentum burned! (' + data.momentum + ' → 2)');

        this.momentumBurnPanel.classList.add('hidden');
        this.pendingRollData = null;
        this.finalizeRoll(data.actionDie, data.challenge1, data.challenge2, data.momentum, burnOutcome, data.adds);
    }

    skipBurn() {
        const data = this.pendingRollData;
        this.momentumBurnPanel.classList.add('hidden');
        this.pendingRollData = null;
        this.finalizeRoll(data.actionDie, data.challenge1, data.challenge2, data.actionScore, data.outcome, data.adds);
    }

    finalizeRoll(actionDie, challenge1, challenge2, actionScore, outcome, adds = 0) {
        this.disableInput();
        this.addLoadingIndicator();

        const playerAction = this.messageInput.value.trim();
        this.send({
            type: 'move_result',
            categoryKey: this.pendingMove.category,
            moveKey: this.pendingMove.key,
            stat: this.selectedStat,
            statValue: this.character[this.selectedStat],
            adds,
            actionDie,
            challenge1,
            challenge2,
            actionScore,
            outcome,
            playerAction
        });

        this.cancelMove();
    }

    cancelMove() {
        this.pendingMove = null;
        this.selectedStat = null;
        // Restore input container to normal mode
        this.moveBadge.classList.add('hidden');
        this.rollControls.classList.add('hidden');
        this.sendBtn.classList.remove('hidden');
        if (this.inspireBtn) {
            this.inspireBtn.classList.remove('hidden');
        }
        this.inputContainer.classList.remove('move-mode');
        this.momentumBurnPanel.classList.add('hidden');
        this.manualDiceToggle.checked = false;
        this.manualDicePanel.classList.add('hidden');
        document.getElementById('roll-adds').value = 0;
        this.messageInput.value = '';
        this.messageInput.style.height = 'auto';
        this.messageInput.placeholder = this.creationMode ? 'Tell the guide about your character...' : 'What do you do?';
        document.querySelectorAll('.stat-btn').forEach(b => {
            b.classList.remove('selectable', 'selected');
        });
    }

    // --- Oracles ---

    initOracleButtons() {
        document.querySelectorAll('.oracle-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                this.send({
                    type: 'oracle',
                    collectionKey: btn.dataset.collection,
                    tableKey: btn.dataset.table
                });
            });
        });
    }

    // --- Inspire ---

    initInspire() {
        if (this.inspireBtn) {
            this.inspireBtn.addEventListener('click', () => this.sendInspire());
        }
    }

    sendInspire() {
        this.disableInput();
        this.addLoadingIndicator();
        this.send({ type: 'inspire' });
    }

    // --- Bottom drawer (narrow screens) ---

    initDrawer() {
        if (this.drawerToggle) {
            this.drawerToggle.addEventListener('click', () => {
                this.sidebar.classList.toggle('open');
            });
        }
    }
}
