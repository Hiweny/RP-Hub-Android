(function () {
    if (window.__RPHUB_DL__) return;
    window.__RPHUB_DL__ = true;
    try {
        var blobMap = new Map();
        var origCreate = URL.createObjectURL;
        var origRevoke = URL.revokeObjectURL;

        URL.createObjectURL = function (obj) {
            var url = origCreate.call(URL, obj);
            try {
                if (obj && Object.prototype.toString.call(obj) === '[object Blob]') {
                    blobMap.set(url, obj);
                }
            } catch (e) { /* ignore */ }
            return url;
        };

        URL.revokeObjectURL = function (url) {
            try { blobMap.delete(url); } catch (e) { /* ignore */ }
            return origRevoke.call(URL, url);
        };

        function toChunks(str) {
            var SIZE = 256 * 1024, out = [];
            for (var i = 0; i < str.length; i += SIZE) out.push(str.substr(i, SIZE));
            return out;
        }

        function saveBlob(blob, fileName) {
            var reader = new FileReader();
            reader.onload = function () {
                try {
                    var res = String(reader.result || '');
                    var idx = res.indexOf(',');
                    var b64 = idx >= 0 ? res.slice(idx + 1) : res;
                    var mime = blob.type || 'application/octet-stream';
                    var bridge = window.RPHubBridge;
                    bridge.startSave(fileName || 'download', mime);
                    var parts = toChunks(b64);
                    for (var i = 0; i < parts.length; i++) bridge.appendChunk(parts[i]);
                    bridge.finishSave();
                } catch (e) { /* ignore */ }
            };
            reader.onerror = function () { /* ignore */ };
            reader.readAsDataURL(blob);
        }

        // Intercept programmatic anchor downloads pointing at blob URLs.
        var origClick = HTMLAnchorElement.prototype.click;
        HTMLAnchorElement.prototype.click = function () {
            try {
                var href = this.href || '';
                var hasDownload = this.hasAttribute && this.hasAttribute('download');
                if (hasDownload && href.indexOf('blob:') === 0 && blobMap.has(href) && window.RPHubBridge) {
                    saveBlob(blobMap.get(href), this.getAttribute('download'));
                    return;
                }
            } catch (e) { /* fall through to native behaviour */ }
            return origClick.apply(this, arguments);
        };
    } catch (e) { /* never break the page */ }
})();
