// MongoDB collection setup for ogiri's Token storage
// Run these commands in MongoDB shell or equivalent

// Create collection with schema validation
db.createCollection("tokens", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            required: ["userId", "client", "token", "tokenType", "expiryAt", "createdAt", "updatedAt", "tokenUpdatedAt"],
            properties: {
                _id: {
                    bsonType: "objectId",
                    description: "Token ID"
                },
                userId: {
                    bsonType: "long",
                    description: "User identifier"
                },
                client: {
                    bsonType: "string",
                    description: "Client/application identifier"
                },
                token: {
                    bsonType: "string",
                    description: "Token hash (BCrypt or similar)"
                },
                tokenType: {
                    bsonType: "string",
                    enum: ["app", "sub"],
                    description: "Token type (app or sub)"
                },
                tokenSubtype: {
                    bsonType: ["string", "null"],
                    description: "Sub-token name/type (optional)"
                },
                expiryAt: {
                    bsonType: "date",
                    description: "Token expiration timestamp"
                },
                lastToken: {
                    bsonType: ["string", "null"],
                    description: "Previous token hash for grace period (optional)"
                },
                previousToken: {
                    bsonType: ["string", "null"],
                    description: "Token before last for extended grace period (optional)"
                },
                createdAt: {
                    bsonType: "date",
                    description: "When token was created"
                },
                updatedAt: {
                    bsonType: "date",
                    description: "Last update timestamp"
                },
                tokenUpdatedAt: {
                    bsonType: "date",
                    description: "When token/rotation last occurred"
                },
                lastUsedAt: {
                    bsonType: ["date", "null"],
                    description: "When token was last used (optional)"
                }
            }
        }
    }
});

// Create indexes for performance
db.tokens.createIndex({ userId: 1, client: 1 }, { unique: true });
db.tokens.createIndex({ userId: 1 });

// TTL index to automatically delete expired tokens after 1 hour grace period
db.tokens.createIndex(
    { expiryAt: 1 },
    { expireAfterSeconds: 3600 }
);
