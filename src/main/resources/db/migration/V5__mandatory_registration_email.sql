-- Preserve existing accounts without email; require it for new accounts.
CREATE FUNCTION require_new_account_email() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.email IS NULL THEN
            RAISE EXCEPTION 'Email is required for new accounts' USING ERRCODE = '23514';
        END IF;
    ELSIF OLD.email IS NOT NULL AND NEW.email IS NULL THEN
        RAISE EXCEPTION 'Email cannot be removed' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER users_require_email BEFORE INSERT OR UPDATE OF email ON users
FOR EACH ROW EXECUTE FUNCTION require_new_account_email();
