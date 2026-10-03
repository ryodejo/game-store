-- V3 has already been applied. Keep its checksum; tighten only the new display-name field.
-- Existing login identifiers, password hashes and ownership relations remain unchanged.
UPDATE users SET display_name = left(display_name,30)
WHERE char_length(display_name) > 30 AND display_name = login;
ALTER TABLE users DROP CONSTRAINT users_display_name_check;
ALTER TABLE users ADD CONSTRAINT users_display_name_check CHECK
    (char_length(display_name) BETWEEN 3 AND 30 AND display_name = btrim(display_name) AND display_name !~ '[[:cntrl:]]');
