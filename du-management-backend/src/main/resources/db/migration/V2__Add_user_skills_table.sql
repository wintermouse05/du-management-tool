CREATE TABLE user_skills
(
    user_id     BIGINT      NOT NULL,
    skill       VARCHAR(60) NOT NULL,
    skill_level INTEGER     NOT NULL
);

ALTER TABLE user_skills
    ADD CONSTRAINT uk_user_skills_user_skill UNIQUE (user_id, skill);

CREATE UNIQUE INDEX IX_pk_seminar_votes ON seminar_votes (user_id, seminar_id);

CREATE UNIQUE INDEX IX_pk_user_surveys ON user_surveys (user_id, survey_id);

ALTER TABLE user_skills
    ADD CONSTRAINT fk_user_skills_on_user FOREIGN KEY (user_id) REFERENCES users (id);
