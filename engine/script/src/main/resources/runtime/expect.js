function expect(actual) {
    return {
        to: {
            eql: function(expected) {
                if (actual != expected) {
                    throw new Error("Expected " + expected + " but got " + actual);
                }
            },
            include: function(needle) {
                if (String(actual).indexOf(needle) === -1) {
                    throw new Error("Expected '" + actual + "' to include '" + needle + "'");
                }
            }
        }
    };
}
