(function () {
    // Main app only - never touch cross-origin embedded frames.
    if (location.hostname !== 'sta1n156.github.io') return;
    if (window.__RPHUB_THEME__) return;
    window.__RPHUB_THEME__ = true;
    try {
        var KEY = 'rphub-appearance';
        // RP Hub ships its own dark theme; the shell always uses it.
        try { window.localStorage.setItem(KEY, 'dark'); } catch (e) { /* storage may be blocked */ }
        var root = document.documentElement;
        root.dataset.appTheme = 'dark';
        root.style.colorScheme = 'dark';
    } catch (e) { /* never break the page */ }
})();
