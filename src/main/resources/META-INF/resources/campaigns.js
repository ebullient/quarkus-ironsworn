document.querySelectorAll('.delete-campaign').forEach(btn => {
    btn.addEventListener('click', async (e) => {
        e.preventDefault();
        const id = btn.dataset.id;
        const name = btn.dataset.name;
        if (!confirm(`Delete campaign "${name}"? This cannot be undone.`)) return;

        btn.disabled = true;
        btn.textContent = 'Deleting…';

        try {
            const resp = await fetch(`/api/play/campaigns/${encodeURIComponent(id)}`, {
                method: 'DELETE'
            });
            if (resp.ok || resp.status === 204) {
                btn.closest('.campaign-card').remove();
            } else {
                alert('Failed to delete campaign: ' + resp.statusText);
                btn.disabled = false;
                btn.textContent = 'Delete';
            }
        } catch (err) {
            alert('Error: ' + err.message);
            btn.disabled = false;
            btn.textContent = 'Delete';
        }
    });
});

document.getElementById('new-campaign-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const name = document.getElementById('char-name').value.trim();
    if (!name) return;

    const btn = e.target.querySelector('button');
    btn.disabled = true;
    btn.textContent = 'Creating...';

    try {
        const resp = await fetch('/api/play/campaigns', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name })
        });
        if (resp.ok) {
            const campaign = await resp.json();
            window.location.href = '/play/' + campaign.id;
        } else {
            alert('Failed to create campaign: ' + resp.statusText);
            btn.disabled = false;
            btn.textContent = 'Begin';
        }
    } catch (err) {
        alert('Error: ' + err.message);
        btn.disabled = false;
        btn.textContent = 'Begin';
    }
});
