(function () {
    // Main app only - CDN warm-up is pointless (and risky) inside embedded frames.
    if (location.hostname !== 'sta1n156.github.io') return;
    if (window.__RPHUB_NET__) return;
    window.__RPHUB_NET__ = true;
    try {
        var hosts = [
            'https://cdn.tailwindcss.com',
            'https://unpkg.com',
            'https://cdn.jsdelivr.net'
        ];
        var root = document.head || document.documentElement;
        if (!root) return;
        for (var i = 0; i < hosts.length; i++) {
            try {
                if (document.querySelector('link[rel="preconnect"][href="' + hosts[i] + '"]')) continue;
                var link = document.createElement('link');
                link.rel = 'preconnect';
                link.href = hosts[i];
                link.crossOrigin = 'anonymous';
                root.appendChild(link);
            } catch (e) { /* ignore */ }
        }
    } catch (e) { /* never break the page */ }
})();
