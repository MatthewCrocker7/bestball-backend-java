DROP TABLE IF EXISTS USER_CREDENTIALS;

CREATE TABLE USER_CREDENTIALS (
    USER_ID                 UUID PRIMARY KEY NOT NULL,
    USER_NAME               VARCHAR(255) NOT NULL,
    EMAIL                   VARCHAR(255) NOT NULL,
    PASSWORD                VARCHAR(255) NOT NULL,
    FIRST_NAME              VARCHAR(255) NOT NULL,
    LAST_NAME               VARCHAR(255) NOT NULL,
    ENABLED                 boolean
);

-- INSERT INTO USERS (first_name, last_name, email, user_name) VALUES
-- ('Matthew', 'Crocker', 'matthewcroc@gmail.com', 'matthewcrocker7'),
-- ('Nicole', 'Tranchita', 'tranchita.nicole@gmail.com', 'nicciT');
