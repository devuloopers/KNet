var console = {
    log: function(msg) { __bridge.log(String(msg)); },
    warn: function(msg) { __bridge.log("[WARN] " + String(msg)); },
    error: function(msg) { __bridge.log("[ERROR] " + String(msg)); },
    info: function(msg) { __bridge.log("[INFO] " + String(msg)); }
};
