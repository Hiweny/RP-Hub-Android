(function () {
    if (window.__RPHUB_THEME__) return;
    window.__RPHUB_THEME__ = true;
    try {
        var KEY = 'rphub-appearance';
        var mq = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

        function sysTheme() {
            return (mq && mq.matches) ? 'dark' : 'light';
        }

        // Treat "no saved preference" as "follow the system", so the page's own
        // theme.js picks the system theme on first paint and never flashes white.
        try {
            var proto = Object.getPrototypeOf(window.localStorage);
            var origGet = proto.getItem;
            proto.getItem = function (key) {
                var value = origGet.call(this, key);
                if (key === KEY && (value === null || value === undefined) && this === window.localStorage) {
                    return sysTheme();
                }
                return value;
            };
        } catch (e) { /* storage may be blocked */ }

        var root = document.documentElement;
        function paint(theme) {
            try {
                root.dataset.appTheme = theme;
                root.style.colorScheme = theme;
            } catch (e) { /* ignore */ }
        }

        // Paint immediately at document-start to avoid a white first frame.
        paint(sysTheme());

        function onChange() {
            var t = sysTheme();
            if (window.RPHubTheme && typeof window.RPHubTheme.set === 'function') {
                window.RPHubTheme.set(t);
            } else {
                paint(t);
            }
        }

        if (mq) {
            if (mq.addEventListener) mq.addEventListener('change', onChange);
            else if (mq.addListener) mq.addListener(onChange);
        }
    } catch (e) { /* never break the page */ }
})();
