-- AC-9: preserve the previous login timestamp so it can be shown to the user upon next login
ALTER TABLE credentials ADD COLUMN previous_login_at TIMESTAMPTZ;
