(function () {
    if (window.__RPHUB_THEME__) return;
    window.__RPHUB_THEME__ = true;
    try {
        var KEY = 'rphub-appearance';
        // The page ships a built-in dark theme; the APK uses it directly instead of
        // following the system setting.
        var DEFAULT = 'dark';
        var root = document.documentElement;

        function paint(theme) {
            try {
                root.dataset.appTheme = theme;
                root.style.colorScheme = theme;
            } catch (e) { /* ignore */ }
        }

        // First run -> default to the page's dark theme. A choice the user makes
        // inside the page is still honoured (it is written to localStorage).
        var theme = DEFAULT;
        try {
            var saved = window.localStorage.getItem(KEY);
            if (saved === 'dark' || saved === 'light') {
                theme = saved;
            } else {
                window.localStorage.setItem(KEY, DEFAULT);
            }
        } catch (e) { /* storage may be blocked */ }

        // Paint at document-start so the very first frame is already dark (no flash).
        paint(theme);
    } catch (e) { /* never break the page */ }
})();
