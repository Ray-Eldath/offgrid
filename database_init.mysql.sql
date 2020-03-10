create schema hydra;
create schema grafana;

#
use offgrid;

create table user_applications
(
    id                       int auto_increment
        primary key,
    email                    varchar(50)          not null,
    is_email_confirmed       tinyint(1) default 0 not null,
    email_confirmation_token varchar(50)          not null,
    last_request_token_time  datetime             not null,
    hashed_password          varchar(150)         null,
    username                 varchar(20)          null,
    is_application_pending   tinyint(1) default 1 not null,
    constraint User_Applications_email_uindex
        unique (email)
);

create table users
(
    id       int auto_increment
        primary key,
    state    int default 0 not null,
    username varchar(20)   not null,
    email    varchar(50)   not null,
    constraint Users_email_unique
        unique (email)
);

create table authorizations
(
    user_id         int          not null
        primary key,
    hashed_password varchar(150) not null,
    role            int          not null,
    last_login_time datetime     null,
    register_time   datetime     null,
    constraint fk_Authorizations_user_id_id
        foreign key (user_id) references users (id)
            on delete cascade
);

create table extra_permissions
(
    authorization_id int                  not null,
    permission_id    varchar(5)           not null,
    is_shield        tinyint(1) default 1 not null,
    constraint Extra_Permissions_Authorizations_user_id_fk
        foreign key (authorization_id) references authorizations (user_id)
            on delete cascade
);

create table reset_password_applications
(
    id               int auto_increment
        primary key,
    authorization_id int         not null,
    email            varchar(50) not null,
    token            varchar(50) not null,
    request_time     datetime    not null,
    constraint Reset_Passwords_Authorizations_user_id_fk
        foreign key (authorization_id) references authorizations (user_id)
);

alter table users
    auto_increment = 1000;

alter table user_applications
    auto_increment = 1000;