ALTER TABLE tasks
DROP
CONSTRAINT fk_tasks_on_assignee;

CREATE TABLE task_assignees
(
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL
);

ALTER TABLE task_assignees
    ADD CONSTRAINT uk_task_assignees_task_user UNIQUE (task_id, user_id);

ALTER TABLE task_assignees
    ADD CONSTRAINT fk_tasass_on_task FOREIGN KEY (task_id) REFERENCES tasks (id);

CREATE INDEX idx_task_assignees_task_id ON task_assignees (task_id);

ALTER TABLE task_assignees
    ADD CONSTRAINT fk_tasass_on_user FOREIGN KEY (user_id) REFERENCES users (id);

CREATE INDEX idx_task_assignees_user_id ON task_assignees (user_id);

ALTER TABLE tasks
DROP
COLUMN assignee_id;
