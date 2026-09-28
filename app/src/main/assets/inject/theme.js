(function () {
    if (window.__RPHUB_THEME__) return;
    window.__RPHUB_THEME__ = true;
    try {
        var KEY = 'rphub-appearance';
        var mq = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

        function sysTheme() {
            return (mq && mq.matches) ? 'dark' : 'light';
        }

        // No saved preference -> follow the system, so the page's own theme and our
        // dark fallback layer both line up with the device setting.
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

        // Paint at document-start so the first frame already matches the system.
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
