ALTER TABLE audit_log DROP CONSTRAINT audit_log_actor_id_fkey;
ALTER TABLE audit_log
    ADD CONSTRAINT audit_log_actor_id_fkey
    FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE SET NULL;
