/* Keyboard bridge.
   The site positions its composer with `bottom: var(--keyboard-inset)` and expects
   the visual viewport to change when the keyboard opens. In a full-screen (edge to
   edge) shell the WebView never resizes, so we feed the keyboard height straight
   into that CSS variable instead - the page lifts its own input box, and the
   WebView itself is never resized.

   The value is written with priority "important" so the page's own
   syncMobileVisualViewport() (which writes the same property without priority)
   cannot overwrite it. */
(function () {
    if (window.__rphubSetKeyboard) return;

    function isEditable(el) {
        if (!el) return false;
        var tag = el.tagName;
        return tag === 'INPUT' || tag === 'TEXTAREA' || el.isContentEditable;
    }

    window.__rphubSetKeyboard = function (height) {
        try {
            var h = Math.max(0, Math.round(Number(height) || 0));
            var root = document.documentElement;
            root.style.setProperty('--keyboard-inset', h + 'px', 'important');

            if (h > 0) {
                var el = document.activeElement;
                if (isEditable(el)) {
                    // Let the page apply the inset first, then reveal the caret.
                    setTimeout(function () {
                        try { el.scrollIntoView({ block: 'center', behavior: 'smooth' }); } catch (e) { /* ignore */ }
                    }, 60);
                }
            }
        } catch (e) { /* never break the page */ }
    };
})();
