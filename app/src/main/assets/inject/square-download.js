(function () {
    // Only inside the embedded 万相广场 frame (rphforum.zeabur.app).
    if (location.hostname !== 'rphforum.zeabur.app') return;
    if (window.__RPHUB_SQ_DL__) return;
    window.__RPHUB_SQ_DL__ = true;

    // Deliberately low-touch: we do NOT patch URL.createObjectURL or
    // HTMLAnchorElement.prototype.click (doing that is what broke this frame
    // before). Instead we listen for clicks in the capture phase and take over
    // only for real <a download> elements.
    try {
        var SIZE = 256 * 1024;

        function chunks(str) {
            var out = [];
            for (var i = 0; i < str.length; i += SIZE) out.push(str.substr(i, SIZE));
            return out;
        }

        function saveBlob(blob, name) {
            if (!blob) return;
            var reader = new FileReader();
            reader.onload = function () {
                try {
                    var res = String(reader.result || '');
                    var idx = res.indexOf(',');
                    var b64 = idx >= 0 ? res.slice(idx + 1) : res;
                    var mime = blob.type || 'application/octet-stream';
                    var bridge = window.RPHubBridge;
                    if (!bridge || !b64) return;
                    bridge.startSave(name || 'download', mime);
                    var parts = chunks(b64);
                    for (var i = 0; i < parts.length; i++) bridge.appendChunk(parts[i]);
                    bridge.finishSave();
                } catch (e) { /* ignore */ }
            };
            reader.onerror = function () { /* ignore */ };
            reader.readAsDataURL(blob);
        }

        function findAnchor(node) {
            while (node && node !== document) {
                if (node.tagName === 'A') return node;
                node = node.parentNode;
            }
            return null;
        }

        function nameFor(anchor, blob) {
            var name = anchor.getAttribute && anchor.getAttribute('download');
            if (!name) name = 'download';
            if (name.indexOf('.') === -1 && blob && blob.type) {
                if (blob.type.indexOf('image/png') === 0) name += '.png';
                else if (blob.type.indexOf('image/jpeg') === 0) name += '.jpg';
                else if (blob.type.indexOf('json') !== -1) name += '.json';
            }
            return name;
        }

        function fallbackToNative(anchor) {
            // Re-dispatch a click that our own listener ignores, so the platform
            // download manager / DownloadListener still gets a chance.
            try {
                window.__rphubBypass = true;
                var a = document.createElement('a');
                a.href = anchor.href;
                if (anchor.hasAttribute && anchor.hasAttribute('download')) {
                    a.download = anchor.getAttribute('download');
                }
                a.rel = 'noopener';
                a.style.display = 'none';
                document.body.appendChild(a);
                a.click();
                a.remove();
            } catch (e) { /* ignore */ } finally {
                window.__rphubBypass = false;
            }
        }

        document.addEventListener('click', function (event) {
            try {
                if (window.__rphubBypass) return;
                var anchor = findAnchor(event.target);
                if (!anchor) return;
                if (!(anchor.hasAttribute && anchor.hasAttribute('download'))) return;

                var href = anchor.href || '';

                if (href.indexOf('blob:') === 0) {
                    // Same-document blob: readable via fetch(). Hand it to the native
                    // side so the user picks where to save.
                    event.preventDefault();
                    event.stopPropagation();
                    fetch(href).then(function (r) { return r.blob(); }).then(function (b) {
                        saveBlob(b, nameFor(anchor, b));
                    }).catch(function () { /* ignore */ });
                    return;
                }

                if (href.indexOf('http') === 0 && window.RPHubBridge) {
                    // A direct link (rare). Try to pull the bytes ourselves so the save
                    // dialog appears; if that fails (CORS), fall back to the platform.
                    event.preventDefault();
                    event.stopPropagation();
                    fetch(href, { credentials: 'include' }).then(function (r) {
                        if (!r.ok) throw new Error('http ' + r.status);
                        return r.blob();
                    }).then(function (b) {
                        saveBlob(b, nameFor(anchor, b));
                    }).catch(function () {
                        fallbackToNative(anchor);
                    });
                }
            } catch (e) { /* never break the page */ }
        }, true);
    } catch (e) { /* never break the page */ }
})();
