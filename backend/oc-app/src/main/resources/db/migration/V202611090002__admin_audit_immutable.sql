CREATE OR REPLACE FUNCTION admin_audit_log_immutable()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'admin_audit_log is append-only';
    END IF;
    IF current_setting('app.audit_purge', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'admin_audit_log is append-only';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_admin_audit_immutable ON admin_audit_log;
CREATE TRIGGER trg_admin_audit_immutable
    BEFORE UPDATE OR DELETE ON admin_audit_log
    FOR EACH ROW EXECUTE FUNCTION admin_audit_log_immutable();