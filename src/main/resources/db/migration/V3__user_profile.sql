ALTER TABLE users ADD COLUMN display_name VARCHAR(32);
-- Legacy logins can have 31–32 characters: preserve them until the owner chooses a new nickname.
UPDATE users SET display_name = login;
ALTER TABLE users ALTER COLUMN display_name SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_display_name_check CHECK
    ((char_length(display_name) BETWEEN 3 AND 30 OR display_name = login) AND display_name = btrim(display_name) AND display_name !~ '[[:cntrl:]]');
ALTER TABLE users ADD COLUMN email VARCHAR(254);
ALTER TABLE users ADD CONSTRAINT users_email_check CHECK
    (email IS NULL OR (email = lower(btrim(email)) AND email ~ '^[a-z0-9.!#$%&''*+/=?^_`{|}~-]+@[a-z0-9-]+(\.[a-z0-9-]+)+$'));
CREATE UNIQUE INDEX users_email_unique ON users(email) WHERE email IS NOT NULL;
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE CHECK (email_verified = FALSE);
ALTER TABLE users ADD COLUMN avatar_png BYTEA CHECK (avatar_png IS NULL OR octet_length(avatar_png) <= 300000);
ALTER TABLE users ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0 CHECK (session_version >= 0);
