function expect(actual) {
    var checkEql = function(expected) {
        if (actual != expected) {
            throw new Error("Expected '" + expected + "' but got '" + actual + "'");
        }
    };
    var checkInclude = function(needle) {
        if (String(actual).indexOf(needle) === -1) {
            throw new Error("Expected '" + actual + "' to include '" + needle + "'");
        }
    };
    var checkType = function(expectedType) {
        var actualType = typeof actual;
        if (actualType !== expectedType) {
            throw new Error("Expected type '" + expectedType + "' but got '" + actualType + "'");
        }
    };

    var chain = {
        eql: checkEql,
        equal: checkEql,
        equals: checkEql,
        include: checkInclude,
        includes: checkInclude,
        contain: checkInclude,
        contains: checkInclude,
        a: checkType,
        an: checkType
    };

    try {
        Object.defineProperty(chain, 'true', {
            get: function() {
                if (actual !== true) throw new Error("Expected true but got " + actual);
            }
        });
        Object.defineProperty(chain, 'false', {
            get: function() {
                if (actual !== false) throw new Error("Expected false but got " + actual);
            }
        });
        Object.defineProperty(chain, 'null', {
            get: function() {
                if (actual !== null) throw new Error("Expected null but got " + actual);
            }
        });
        Object.defineProperty(chain, 'undefined', {
            get: function() {
                if (actual !== undefined) throw new Error("Expected undefined but got " + actual);
            }
        });
    } catch (_) {}

    var target = Object.assign ? Object.assign({}, chain) : chain;
    target.be = chain;

    return {
        to: target
    };
}
