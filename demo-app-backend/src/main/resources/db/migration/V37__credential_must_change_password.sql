-- IA-5(1)(f): admin-provisioned accounts must change password on first login
ALTER TABLE credentials ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
